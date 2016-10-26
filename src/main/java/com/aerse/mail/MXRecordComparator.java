package com.aerse.mail;

import java.util.Comparator;

class MXRecordComparator implements Comparator<MXRecord> {
	
	final static MXRecordComparator INSTANCE = new MXRecordComparator();

	@Override
	public int compare(MXRecord o1, MXRecord o2) {
		return o1.getPriority().compareTo(o2.getPriority());
	}

}
