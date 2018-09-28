package com.lazarus.adblock.connections;

public enum Direction {
	UPSTREAM(0x01),			// From connection initiator to remote host
	DOWNSTREAM(0x02),		// From remote host to connection initiator
	BOTH(0x3);				// OR-ed both
	
	private int value;
	
	private Direction(int value) {
		this.value = value;
	}
	
	public int value() {
		return value;
	}
}
