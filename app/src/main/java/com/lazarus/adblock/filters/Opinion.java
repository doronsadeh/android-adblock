package com.lazarus.adblock.filters;

import android.util.Log;

import java.util.Map;


public class Opinion {

	public enum Mode {
		UNDEFINED,
		
		/*
		 * indicates that a connection should avoid filtering (bypass)
		 * traffic from now on.
		 */
		BYPASS,
		
		/*
		 * indicates that a connection should pass the traffic (as is)
		 * to the target channel.
		 */
		PASS, 
		
		/*
		 * indicates that a connection should block the traffic coming
		 * from the source channel.
		 * blocking method is chosen based on specific connection.
		 */
		BLOCK,
		
		/*
		 * TODO impl. in the future for non-transactional long-running protocols
		 * indicate an on-going state where we silently drop all
		 * packets over this connection. This is different then BLOCK, as the later
		 * closes the connection (softly or hard), while DROP keeps the connection open
		 * while dropping packets.
		 */
		DROP
	}
	
	public Mode mode;
	public Map<Filter, Filter> detected;
	public String entity;
	
	public Opinion(Mode mode, Map<Filter, Filter> detected, String entity) {
		this.mode = mode;
		this.detected = detected;
		this.entity = entity;
	}
	
}
