package com.aerse.mail;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class NamingThreadFactory implements ThreadFactory {

	private final String prefix;
	private final AtomicInteger threadCreated = new AtomicInteger(0);

	NamingThreadFactory(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public Thread newThread(Runnable r) {
		return new Thread(r, prefix + "-" + threadCreated.incrementAndGet());
	}

}
