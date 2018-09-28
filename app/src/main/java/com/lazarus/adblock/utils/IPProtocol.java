package com.lazarus.adblock.utils;

public enum IPProtocol {
	UNSUPPORTED(0),
	TCP(6),
	UDP(17);
	
	private final int protocol;
	private IPProtocol(int protocol) {
		this.protocol = protocol;
	}
	
	public int value() {
		return protocol;
	}
	
	public static IPProtocol valueOf(int protocol) {
		switch(protocol) {
			case 6: 
				return TCP;
			case 17:
				return UDP;
			default:
				return UNSUPPORTED;
		}
	}
}
