package com.aerse.mail;

import java.util.Map;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

public class FreemarkerMimeMessage extends MimeMessage {

	private final Map<Object, Object> model;
	private final String template;

	public FreemarkerMimeMessage(Session session, Map<Object, Object> model, String template) {
		super(session);
		this.model = model;
		this.template = template;
	}

	public String getTemplate() {
		return template;
	}

	public Map<Object, Object> getModel() {
		return model;
	}

}
