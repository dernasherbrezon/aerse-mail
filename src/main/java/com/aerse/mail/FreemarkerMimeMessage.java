package com.aerse.mail;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FreemarkerMimeMessage {

	private Map<Object, Object> model;
	private String template;
	private List<String> to;
	private List<String> cc;
	private List<String> bcc;
	private String subject;
	private String replyTo;
	
	public String getReplyTo() {
		return replyTo;
	}
	
	public void setReplyTo(String replyTo) {
		this.replyTo = replyTo;
	}

	public void setTo(String to) {
		this.to = Collections.singletonList(to);
	}

	public void setCc(String cc) {
		this.cc = Collections.singletonList(cc);
	}

	public void setBcc(String bcc) {
		this.bcc = Collections.singletonList(bcc);
	}

	public Map<Object, Object> getModel() {
		return model;
	}

	public void setModel(Map<Object, Object> model) {
		this.model = model;
	}

	public String getTemplate() {
		return template;
	}

	public void setTemplate(String template) {
		this.template = template;
	}

	public List<String> getTo() {
		return to;
	}

	public void setTo(List<String> to) {
		this.to = to;
	}

	public List<String> getCc() {
		return cc;
	}

	public void setCc(List<String> cc) {
		this.cc = cc;
	}

	public List<String> getBcc() {
		return bcc;
	}

	public void setBcc(List<String> bcc) {
		this.bcc = bcc;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

}
