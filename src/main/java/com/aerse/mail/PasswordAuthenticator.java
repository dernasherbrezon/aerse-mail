package com.aerse.mail;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;

class PasswordAuthenticator extends Authenticator {

	private final String username;
	private final String password;

	public PasswordAuthenticator(String username, String password) {
		this.username = username;
		this.password = password;
	}

	@Override
	protected PasswordAuthentication getPasswordAuthentication() {
		return new PasswordAuthentication(username, password);
	}

}
