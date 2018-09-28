package com.lazarus.adblock.filters;

import org.json.JSONException;
import org.json.JSONObject;

import com.lazarus.adblock.connections.Direction;
import com.lazarus.adblock.connections.Tuple;
import com.lazarus.adblock.utils.IPProtocol;

public class Criteria {
	
	private static final String DIRECTION = "direction";
	private static final String PROTOCOL = "protocol";

	private static final int UPSTREAM = 0x0001;
	private static final int DOWNSTREAM = 0x0002;
	private static final int TCP = 0x0004;
	private static final int UDP = 0x0008;
	
	private static final int DIRMASK = UPSTREAM | DOWNSTREAM;
	private static final int PROTMASK = TCP | UDP;
	
	private static final String JUPSTREAM = "u";
	private static final String JDOWNSTREAM = "d";
	private static final String JBOTH = "both";
	private static final String JTCP = "tcp";
	private static final String JUDP = "udp";

	public enum Protocol {
		TCP,
		UDP,
		BOTH
	}

	private int mask;

	public Criteria(Direction direction, Protocol protocol) {
		mask = 0;
		if (direction == Direction.UPSTREAM)
			mask |= UPSTREAM;

		if (direction == Direction.DOWNSTREAM)
			mask |= DOWNSTREAM;

		if (direction == Direction.BOTH)
			mask |= (UPSTREAM | DOWNSTREAM);

		if (protocol == Protocol.TCP)
			mask |= TCP;

		if (protocol == Protocol.UDP)
			mask |= UDP;

		if (protocol == Protocol.BOTH)
			mask |= (TCP | UDP);
	}


	public Criteria(String jsonString) throws JSONException {

		JSONObject c = new JSONObject(jsonString);

		String direction = c.getString(DIRECTION);
		String protocol = c.getString(PROTOCOL);
		
		mask = 0;
		if (direction.equalsIgnoreCase(JUPSTREAM))
			mask |= UPSTREAM;

		if (direction.equalsIgnoreCase(JDOWNSTREAM))
			mask |= DOWNSTREAM;

		if (direction.equalsIgnoreCase(JBOTH))
			mask |= (UPSTREAM | DOWNSTREAM);

		if (protocol.equalsIgnoreCase(JTCP))
			mask |= TCP;

		if (protocol.equalsIgnoreCase(JUDP))
			mask |= UDP;
		
		if (protocol.equalsIgnoreCase(JBOTH))
			mask |= (TCP | UDP);
	}
	
	private int maskOf(Direction d, Tuple t) {
		int m = 0;
		m |= (d == Direction.UPSTREAM ? UPSTREAM : DOWNSTREAM) | 
			 (t.protocol() == IPProtocol.TCP ? TCP : 0) |
			 (t.protocol() == IPProtocol.UDP ? UDP : 0);
		
		return m;
	}
	
	public boolean valid(Direction d, Tuple t) {
		int cMask = maskOf(d, t) & mask;
		return ((cMask & DIRMASK) != 0) && ((cMask & PROTMASK) != 0);
	}
	
	public String toString() {
		return "0x" + Integer.toHexString(mask);
	}
}
