package com.aerse.mail;

class MXRecord {

	private final Integer priority;
	private final String value;

	MXRecord(Integer priority, String value) {
		this.priority = priority;
		this.value = value;
	}

	public Integer getPriority() {
		return priority;
	}

	public String getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "MXRecord [priority=" + priority + ", value=" + value + "]";
	}

}
