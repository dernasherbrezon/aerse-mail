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
import javax.mail.internet.InternetAddress;

import org.apache.log4j.Logger;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class FreemarkerMailSender {

	private static final Logger LOG = Logger.getLogger(FreemarkerMailSender.class);
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

	public void send(final FreemarkerMimeMessage message) throws MessagingException {
		if (threadpool != null) {
			threadpool.execute(new Runnable() {

				@Override
				public void run() {
					try {
						implSend(message);
					} catch (MessagingException e) {
						LOG.error("unable to send message", e);
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
		modelToUse.put("email", ((InternetAddress) message.getRecipients(RecipientType.TO)[0]).getAddress());
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
		message.setContent(text, "text/html;charset=UTF-8");
		mailSender.send(message);
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
