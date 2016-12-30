package com.aerse.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.xml.bind.DatatypeConverter;

import net.markenwerk.utils.mail.dkim.Canonicalization;
import net.markenwerk.utils.mail.dkim.DkimMessage;
import net.markenwerk.utils.mail.dkim.DkimSigner;
import net.markenwerk.utils.mail.dkim.SigningAlgorithm;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * JavaMail wrapper, that supports the following features:
 * <ul>
 * <li>send transactional messages. These are messages sent directly to client.
 * Include: password reset notifications, welcome letters, order notifications
 * &amp;etc</li>
 * <li>send directly to client's SMTP server. <code>IMailSender</code> analyses
 * MX records. If SMTP server for particular MX record is not available (throws
 * <code>java.net.ConnectException</code>), then next server is used.</li>
 * <li>switch JavaMail logging to log4j</li>
 * </ul>
 * 
 * Before using this MailSender, please do the following configuration:
 * <ul>
 * <li>configure SPF record in DNS:<br>
 * <code>@ TXT v=spf1 a -all</code></li>
 * <li>configure DMARC record in DNS:<br>
 * <code>_dmarc TXT v=DMARC1; p=none; rua=mailto:postmaster@example.com</code></li>
 * <li>configure DKIM.
 * <ol>
 * <li>Create rsa keys: <code>openssl genrsa -out dkim.pem 1024</code></li>
 * <li>Output public key: <code>openssl rsa -in dkim.pem -pubout</code></li>
 * <li>Copy this public key to the DKIM record in DNS:<br>
 * <code>mail._domainkey TXT v=DKIM1; k=rsa; p=&lt;your public key&gt;</code></li>
 * <li>Convert PEM from pkcs1 to pkcs8 format:<br>
 * <code>openssl pkcs8 -topk8 -inform PEM -outform PEM -in dkim.pem -out dkim8.pem -nocrypt</code>
 * <br>
 * This result file <code>dkim8.pem</code> should be used in
 * com.aerse.mail.MailSender.setDkimPrivateKeyLocation(String location)</li>
 * </ol>
 * </li>
 * <li>configure reverse DNS. Your hosting provider should give you tool to do
 * so. If everything works fine, then the following command:<br>
 * <code>dig -x &lt;your-ip-address&gt; +short</code><br>
 * should output your domain name.</li>
 * </ul>
 * 
 * IMailSender implementation. Spring-friendly. Here is sample standalone usage:
 * 
 * <pre>
 * {
 * 	&#064;code
 * 	MailSender sender = new MailSender();
 * 	sender.setSigningDomain(&quot;example.com&quot;);
 * 	sender.setDkimPrivateKeyLocation(&quot;example.com.pem&quot;);
 * 	sender.setFromEmail(&quot;postmaster@example.com&quot;);
 * 	sender.setFromName(&quot;PostMaster&quot;);
 * 	sender.setConnectionTimeoutMillis(1000);
 * 	sender.setDkimSelector(&quot;mail&quot;);
 * 
 * 	sender.start();
 * 
 * 	MailMessage message = new MailMessage();
 * 	message.setTo(&quot;admin@example.com&quot;);
 * 	message.setSubject(&quot;This is test&quot;);
 * 	message.setText(&quot;This is test body&quot;);
 * 	message.setHtml(false);
 * 
 * 	sender.send(message);
 * }
 * </pre>
 * 
 * @see <a href="https://www.mail-tester.com">mail-tester.com</a>
 *
 */
public class DirectMailSender implements IMailSender {

	private final static Logger LOG = Logger.getLogger(DirectMailSender.class);
	private final static String[] MX_RECORD = new String[] { "MX" };
	private InitialDirContext iDirC;

	// parameters for dkim
	private String dkimPrivateKeyLocation;
	private String signingDomain;
	private String dkimSelector;

	private String fromEmail;
	private String fromName;

	private long connectionTimeoutMillis;

	private InternetAddress from;
	private RSAPrivateKey dkimPrivateKey;

	public void start() throws IOException, GeneralSecurityException, NamingException {
		if (fromEmail == null) {
			throw new IllegalStateException("from email should be specified");
		}
		if (dkimPrivateKeyLocation == null) {
			throw new IllegalStateException("dkim private key location should be specified");
		}
		if (signingDomain == null) {
			throw new IllegalStateException("signing domain should be specified");
		}
		if (dkimSelector == null) {
			throw new IllegalArgumentException("dkim selector should be specified");
		}
		iDirC = new InitialDirContext();
		from = new InternetAddress(fromEmail, fromName, "UTF-8");
		dkimPrivateKey = loadPrivateKey(dkimPrivateKeyLocation);
	}

	@Override
	public void send(Message mailMessage) throws MessagingException {
		Address[] to = mailMessage.getRecipients(RecipientType.TO);
		if (to == null) {
			throw new MessagingException("missing \"to\" recipients");
		}
		if (to.length > 1) {
			throw new MessagingException("only single \"to\" recipient supported");
		}
		if (!(to[0] instanceof InternetAddress)) {
			throw new MessagingException("unsupported address type: " + to[0].getClass());
		}
		InternetAddress toAddress = (InternetAddress) to[0];
		String domain = toAddress.getAddress().substring(toAddress.getAddress().indexOf('@') + 1);
		List<MXRecord> mx;
		try {
			mx = getMX(domain);
		} catch (NamingException e2) {
			throw new MessagingException("unable to resolve domain: " + domain, e2);
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("MX records detected: " + mx);
		}
		for (int i = 0; i < mx.size(); i++) {
			String cur = mx.get(i).getValue();
			Properties props = new Properties();
			props.setProperty("mail.smtp.host", cur);
			props.setProperty("mail.smtp.localhost", signingDomain);
			props.setProperty("mail.smtp.starttls.enable", "true");
			props.setProperty("mail.smtp.ssl.trust", "*");
			String timeoutMillisStr = String.valueOf(connectionTimeoutMillis);
			props.setProperty("mail.smtp.timeout", timeoutMillisStr);
			props.setProperty("mail.smtps.timeout", timeoutMillisStr);
			props.setProperty("mail.smtp.connectiontimeout", timeoutMillisStr);
			props.setProperty("mail.smtps.connectiontimeout", timeoutMillisStr);

			Session session = Session.getInstance(props);
			if (LOG.isDebugEnabled()) {
				try {
					session.setDebugOut(new PrintStream(new Log4jPrintStream(LOG, Level.DEBUG), false, "UTF-8"));
					session.setDebug(true);
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException(e);
				}
			}

			MimeMessage message = new MimeMessage(session);
			message.setFrom(from);
			message.setRecipient(RecipientType.TO, to[0]);
			message.setSubject(mailMessage.getSubject());
			message.setReplyTo(mailMessage.getReplyTo());
			try {
				if (mailMessage.getContent() instanceof Multipart) {
					message.setContent(mailMessage.getContent(), mailMessage.getContentType());
				} else {
					message.setDataHandler(mailMessage.getDataHandler());
				}
			} catch (IOException e1) {
				throw new MessagingException("unable to get content", e1);
			}
			message.setSentDate(new Date());
			MimeMessage dkimSignedMessage = dkimSignMessage(message);
			try {
				Transport.send(dkimSignedMessage);
				return;
			} catch (MessagingException e) {
				if (!hasRootCause(e, IOException.class) || i == mx.size() - 1) {
					throw new MessagingException("mx is not available: " + cur, e);
				}
				LOG.info("mx is not available: " + cur);
			}
		}
	}

	private List<MXRecord> getMX(String domainName) throws NamingException {
		// see: RFC 974 - Mail routing and the domain system
		// see: RFC 1034 - Domain names - concepts and facilities
		// see: http://java.sun.com/j2se/1.5.0/docs/guide/jndi/jndi-dns.html
		// - DNS Service Provider for the Java Naming Directory Interface (JNDI)

		// get the MX records from the default DNS directory service provider
		// NamingException thrown if no DNS record found for domainName
		Attributes attributes = iDirC.getAttributes("dns:/" + domainName, MX_RECORD);
		// attributeMX is an attribute ('list') of the Mail Exchange(MX)
		// Resource Records(RR)
		Attribute attributeMX = attributes.get("MX");

		// if there are no MX RRs then default to domainName (see: RFC 974)
		if (attributeMX == null) {
			return Collections.singletonList(new MXRecord(0, domainName));
		}

		// split MX RRs into Preference Values(pvhn[0]) and Host Names(pvhn[1])
		List<MXRecord> result = new ArrayList<MXRecord>(attributeMX.size());
		for (int i = 0; i < attributeMX.size(); i++) {
			String curValue = attributeMX.get(i).toString();
			int spaceIndex = curValue.indexOf(' ');
			if (spaceIndex == -1) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("invalid mx record: " + curValue);
				}
				continue;
			}
			String record;
			if (curValue.charAt(curValue.length() - 1) == '.') {
				record = curValue.substring(spaceIndex + 1, curValue.length() - 1);
			} else {
				record = curValue.substring(spaceIndex + 1);
			}
			try {
				InetAddress[] aRecords = InetAddress.getAllByName(record);
				Integer priority = Integer.valueOf(curValue.substring(0, spaceIndex));
				for (InetAddress cur : aRecords) {
					result.add(new MXRecord(priority, cur.getHostAddress()));
				}
			} catch (UnknownHostException e) {
				String message = "unable to resolve host: " + record + " skipping";
				if (LOG.isDebugEnabled()) {
					LOG.debug(message, e);
				} else {
					LOG.info(message);
				}
			}
		}

		if (result.size() > 1) {
			// sort the MX RRs by RR value (lower is preferred)
			Collections.sort(result, MXRecordComparator.INSTANCE);
		}
		return result;
	}

	public static void main(String[] args) throws Exception {
		InetAddress[] all = InetAddress.getAllByName("inmx.rambler.ru");
		// InetAddress[] all = InetAddress.getAllByName("81.19.78.65");
		System.out.println(all[0].getHostAddress());
	}

	private MimeMessage dkimSignMessage(MimeMessage message) throws MessagingException {
		DkimSigner dkimSigner = new DkimSigner(signingDomain, dkimSelector, dkimPrivateKey);
		dkimSigner.setIdentity(fromEmail);
		dkimSigner.setHeaderCanonicalization(Canonicalization.SIMPLE);
		dkimSigner.setBodyCanonicalization(Canonicalization.RELAXED);
		dkimSigner.setSigningAlgorithm(SigningAlgorithm.SHA256_WITH_RSA);
		dkimSigner.setLengthParam(true);
		dkimSigner.setZParam(false);
		dkimSigner.setCheckDomainKey(false);
		return new DkimMessage(message, dkimSigner);
	}

	private static RSAPrivateKey loadPrivateKey(String location) throws IOException, GeneralSecurityException {
		RSAPrivateKey key = null;
		BufferedReader br = null;
		try {
			InputStream is = DirectMailSender.class.getClassLoader().getResourceAsStream(location);
			if (is == null) {
				throw new IllegalArgumentException("unable to find key in classpath: " + location);
			}
			br = new BufferedReader(new InputStreamReader(is));
			StringBuilder builder = new StringBuilder();
			boolean inKey = false;
			for (String line = br.readLine(); line != null; line = br.readLine()) {
				if (!inKey) {
					if (line.startsWith("-----BEGIN ") && line.endsWith(" PRIVATE KEY-----")) {
						inKey = true;
					}
					continue;
				} else {
					if (line.startsWith("-----END ") && line.endsWith(" PRIVATE KEY-----")) {
						inKey = false;
						break;
					}
					builder.append(line);
				}
			}
			//
			byte[] encoded = DatatypeConverter.parseBase64Binary(builder.toString());
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			key = (RSAPrivateKey) kf.generatePrivate(keySpec);
		} finally {
			if (br != null) {
				br.close();
			}
		}
		return key;
	}

	private static boolean hasRootCause(Throwable e, Class<?> rootCause) {
		if (rootCause.isInstance(e)) {
			return true;
		}
		if (e.getCause() != null) {
			boolean result = hasRootCause(e.getCause(), rootCause);
			if (result) {
				return true;
			}
		}
		return false;
	}

	public void setSigningDomain(String signingDomain) {
		this.signingDomain = signingDomain;
	}

	public void setDkimPrivateKeyLocation(String dkimPrivateKeyLocation) {
		this.dkimPrivateKeyLocation = dkimPrivateKeyLocation;
	}

	public void setFromEmail(String fromEmail) {
		this.fromEmail = fromEmail;
	}

	public void setFromName(String fromName) {
		this.fromName = fromName;
	}

	public void setConnectionTimeoutMillis(long connectionTimeoutMillis) {
		this.connectionTimeoutMillis = connectionTimeoutMillis;
	}

	public void setDkimSelector(String dkimSelector) {
		this.dkimSelector = dkimSelector;
	}
}
