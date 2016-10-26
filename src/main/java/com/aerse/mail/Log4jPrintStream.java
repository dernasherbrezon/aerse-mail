package com.aerse.mail;

import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

class Log4jPrintStream extends OutputStream {

	private final static Pattern CARET = Pattern.compile("\r");
	private final Logger log;
	private final Priority priority;
	private StringBuffer buffer = new StringBuffer();

	Log4jPrintStream(Logger log, Priority priority) {
		this.log = log;
		this.priority = priority;
	}

	@Override
	public void write(int b) throws IOException {
		byte[] bytes = new byte[1];
		bytes[0] = (byte) (b & 0xff);
		write(bytes, 0, bytes.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		String str = new String(b, off, len, "UTF-8");
		int nextLineIndex = -1;
		String remainder = CARET.matcher(str).replaceAll("");
		while ((nextLineIndex = remainder.indexOf("\n")) != -1) {
			buffer.append(remainder.substring(0, nextLineIndex));
			flush();
			remainder = remainder.substring(nextLineIndex + 1);
		}
		buffer.append(remainder);
	}

	@Override
	public void flush() throws IOException {
		String message = buffer.toString();
		if (message.length() != 0) {
			log.log(priority, message);
			buffer = new StringBuffer();
		}
	}
}
