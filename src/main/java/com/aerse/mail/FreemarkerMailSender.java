package com.aerse.mail;

import java.io.StringWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class FreemarkerMailSender {

	private static final Logger LOG = Logger.getLogger(FreemarkerMailSender.class);
	// starts with /
	private String templateClasspathPrefix;
	private IMailSender mailSender;
	private boolean enabled;
	private boolean useSeparateThread;

	private Configuration freemarkerConfig;
	private ExecutorService threadpool;

	public void start() {
		freemarkerConfig = new Configuration();
		freemarkerConfig.setDefaultEncoding("UTF-8");
		freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
		freemarkerConfig.setClassForTemplateLoading(FreemarkerMailSender.class, templateClasspathPrefix);
		freemarkerConfig.setTimeZone(TimeZone.getTimeZone("GMT"));
		if (useSeparateThread) {
			threadpool = Executors.newFixedThreadPool(1, new NamingThreadFactory("aerse-email"));
		} else {
			threadpool = null;
		}
	}

	public void stop() {
		if (threadpool != null) {
			threadpool.shutdown();
		}
	}

	public void sendQuietly(final FreemarkerMimeMessage message) {
		if (message == null) {
			return;
		}
		try {
			send(message);
		} catch (MessagingException e) {
			LOG.error("unable to send message: " + message.getTo() + " subject: " + message.getSubject(), e);
		}
	}

	public void send(final FreemarkerMimeMessage message) throws MessagingException {
		if (message == null) {
			throw new IllegalArgumentException("message cannot be null");
		}
		if (message.getTo() == null || message.getTo().isEmpty()) {
			throw new IllegalArgumentException("\"to\" should be specified");
		}
		if (threadpool != null) {
			threadpool.execute(new Runnable() {

				@Override
				public void run() {
					try {
						implSend(message);
					} catch (MessagingException e) {
						LOG.error("unable to send message: " + message.getTo() + " subject: " + message.getSubject(), e);
					}
				}
			});
		} else {
			implSend(message);
		}
	}

	private void implSend(FreemarkerMimeMessage message) throws MessagingException {
		Map<Object, Object> modelToUse;
		if (message.getModel() == null) {
			modelToUse = new HashMap<Object, Object>();
		} else {
			modelToUse = message.getModel();
		}
		Calendar calendar = Calendar.getInstance();
		// string for no-formatting
		modelToUse.put("currentYear", String.valueOf(calendar.get(Calendar.YEAR)));
		modelToUse.put("email", message.getTo().get(0));
		String text;
		try {
			Template fTemplate = freemarkerConfig.getTemplate(message.getTemplate());
			StringWriter w = new StringWriter();
			fTemplate.process(modelToUse, w);
			text = w.toString();
		} catch (Exception e1) {
			throw new MessagingException("unable to prepare message", e1);
		}
		if (!enabled) {
			LOG.info("sending message. subject: " + message.getSubject() + " body: " + text);
			return;
		}

		MimeMessage mime = new MimeMessage((Session) null);
		mime.setSubject(message.getSubject(), "UTF-8");
		if (message.getBcc() != null) {
			for (String cur : message.getBcc()) {
				mime.addRecipient(RecipientType.BCC, new InternetAddress(cur));
			}
		}
		if (message.getCc() != null) {
			for (String cur : message.getCc()) {
				mime.addRecipient(RecipientType.CC, new InternetAddress(cur));
			}
		}
		for (String cur : message.getTo()) {
			mime.addRecipient(RecipientType.TO, new InternetAddress(cur));
		}
		mime.setContent(text, "text/html; charset=UTF-8");
		if (message.getReplyTo() != null) {
			mime.setReplyTo(new InternetAddress[] { new InternetAddress(message.getReplyTo()) });
		}
		mailSender.send(mime);
	}

	public void setTemplateClasspathPrefix(String templateClasspathPrefix) {
		this.templateClasspathPrefix = templateClasspathPrefix;
	}

	public void setMailSender(IMailSender mailSender) {
		this.mailSender = mailSender;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setUseSeparateThread(boolean useSeparateThread) {
		this.useSeparateThread = useSeparateThread;
	}
}
