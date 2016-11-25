package com.aerse.mail;

import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Simple interface for sending emails
 */
public interface IMailSender {

	/**
	 * Send message.
	 * 
	 * @param message - message to send
	 * @throws MessagingException - any other exceptions. For example: user not found, connection exception &amp;etc.
	 */
	void send(Message message) throws MessagingException;
}
