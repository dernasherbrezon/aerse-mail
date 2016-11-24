package com.aerse.mail;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Mail sender to send messages through relay-server. Relay server should be SSL based with password-based authentication 
 *
 */
public class RelayMailSender implements IMailSender {

	private static final Logger LOG = Logger.getLogger(RelayMailSender.class);

	private long connectionTimeoutMillis;

	private String fromEmail;
	private String fromName;

	private String host;
	private int port;
	private String username;
	private String password;
	private PasswordAuthenticator auth;

	private InternetAddress from;

	public void start() throws UnsupportedEncodingException {
		from = new InternetAddress(fromEmail, fromName, "UTF-8");
		auth = new PasswordAuthenticator(username, password);
	}

	@Override
	public void send(Message message) throws MessagingException {
		Properties props = new Properties();
		props.setProperty("mail.smtps.host", host);
		props.setProperty("mail.smtps.port", String.valueOf(port));
		props.setProperty("mail.smtps.user", username);
		props.setProperty("mail.smtps.starttls.enable", "true");
		props.setProperty("mail.smtps.ssl.trust", "*");
		props.setProperty("mail.smtps.ssl.enable", "true");
		props.setProperty("mail.smtps.requiresAuthentication", "true");
		props.setProperty("mail.smtps.isSecure", "true");
		props.setProperty("mail.smtps.auth", "true");

		String timeoutMillisStr = String.valueOf(connectionTimeoutMillis);
		props.setProperty("mail.smtps.timeout", timeoutMillisStr);
		props.setProperty("mail.smtps.connectiontimeout", timeoutMillisStr);

		Session session = Session.getInstance(props, auth);
		if (LOG.isDebugEnabled()) {
			try {
				session.setDebugOut(new PrintStream(new Log4jPrintStream(LOG, Level.DEBUG), false, "UTF-8"));
				session.setDebug(true);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}

		message.setFrom(from);
		Transport.send(message);
	}

	public void setConnectionTimeoutMillis(long connectionTimeoutMillis) {
		this.connectionTimeoutMillis = connectionTimeoutMillis;
	}

	public void setFromEmail(String fromEmail) {
		this.fromEmail = fromEmail;
	}

	public void setFromName(String fromName) {
		this.fromName = fromName;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

}
