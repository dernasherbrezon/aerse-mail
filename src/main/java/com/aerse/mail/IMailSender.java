package com.aerse.mail;

import javax.mail.MessagingException;
import javax.naming.NamingException;

/**
 * JavaMail wrapper, that supports the following features:
 * <ul>
 *    <li>send transactional messages. These are messages sent directly to client. Include: password reset notifications, welcome letters, order notifications &etc</li>
 *    <li>send directly to client's SMTP server. <code>IMailSender</code> analyses MX records. If SMTP server for particular MX record is not available (throws <code>java.net.ConnectException</code>), then next server is used.</li>
 *    <li>switch JavaMail logging to log4j</li>
 * </ul> 
 * 
 * Before using this MailSender, please do the following configuration:
 * <ul>
 *    <li>configure SPF record in DNS:<br><code>@ TXT v=spf1 a -all</code></li>
 *    <li>configure DMARC record in DNS:<br><code>_dmarc TXT v=DMARC1; p=none; rua=mailto:postmaster@example.com</code></li>
 *    <li>configure DKIM.
 *    	<ol>
 *    		<li>Create rsa keys: <code>openssl genrsa -out dkim.pem 1024</code></li>
 *    		<li>Output public key: <code>openssl rsa -in dkim.pem -pubout</code></li>
 *    		<li>Copy this public key to the DKIM record in DNS:<br>
 *    		<code>mail._domainkey TXT v=DKIM1; k=rsa; p=&lt;your public key&gt;</code>
 *    		</li>
 *    		<li>Convert PEM from pkcs1 to pkcs8 format:<br>
 *    		<code>openssl pkcs8 -topk8 -inform PEM -outform PEM -in dkim.pem -out dkim8.pem -nocrypt</code><br>
 *    		This result file <code>dkim8.pem</code> should be used in com.aerse.mail.MailSender.setDkimPrivateKeyLocation(String location)
 *    		</li>
 *      </ol>
 *    </li>
 *    <li>configure reverse DNS. Your hosting provider should give you tool to do so. If everything works fine, then the following command:<br>
 *    <code>dig -x &lt;your-ip-address&gt; +short</code><br>
 *    should output your domain name.</li>
 * </ul>
 * 
 * @see <a href="https://www.mail-tester.com">mail-tester.com</a>
 */
public interface IMailSender {

	/**
	 * Send message.
	 * 
	 * @param message - message to send
	 * @throws NamingException - invalid "to" address. Unable to lookup DNS record for it.
	 * @throws MessagingException - any other exceptions. For example: user not found, connection exception &etc.
	 */
	void send(MailMessage message) throws NamingException, MessagingException;
}
