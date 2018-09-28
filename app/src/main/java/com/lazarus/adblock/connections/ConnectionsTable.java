package com.lazarus.adblock.connections;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * ConnectionsTable
 * 
 * Maps a connection 5-tuple to a connection (thread) handler
 */
class ConnectionsTable {
	
	private static ConnectionsTable instance = null;
	
	// A map of connection 5-tuples, to actual Connection threads
	private static Map<Tuple, Connection> table = new ConcurrentHashMap<Tuple, Connection>();
	
	private ConnectionsTable() {
	}

	public static ConnectionsTable getInstance() {
		if (instance == null)
			instance = new ConnectionsTable();
		
		return instance;
	}
	
	public void put(Tuple t, Connection c) {
		table.put(t, c);
	}

	public Connection get(Tuple t) {
		return table.get(t);
	}
	
	public void remove(Connection c) {
		if (table.containsKey(c.tuple))
			table.remove(c.tuple);
	}
}