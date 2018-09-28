package com.lazarus.adblock.connections;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * AppsMap
 * 
 * Maps an application UID to its connections 5-tuples
 */
class AppsConnections {

	private static AppsConnections instance = null;

	private static Map<String, Set<Tuple>> uidToStreams = new ConcurrentHashMap<String, Set<Tuple>>();

	private static Map<Tuple, String> streamsToUid = new ConcurrentHashMap<Tuple, String>();

	private AppsConnections() {
	}

	public static AppsConnections getInstance() {
		if (instance == null) {
			instance = new AppsConnections();
		}

		return instance;
	}

	public void put(String uid, Tuple t) {
		if (uid == null)
			return;

		uid = uid.toLowerCase();

		Set<Tuple> selected = null;
		if (uidToStreams.containsKey(uid)) {
			selected = uidToStreams.get(uid);
		}
		else {
			selected = new HashSet<Tuple>();
			uidToStreams.put(uid, selected);
		}

		selected.add(t);

		// Now update the inverse map
		streamsToUid.put(t, uid);
	}

	public Set<Tuple> get(String uid) {

		if (uid == null)
			return null;

		return uidToStreams.get(uid.toLowerCase());
	}

	public void remove(Connection connection) {

		Tuple t = connection.tuple;

		if (streamsToUid.containsKey(t)) {
			String uid = streamsToUid.get(t);
			
			if (uid == null)
				return;
			
			// Remove from uid table
			uidToStreams.get(uid).remove(t);

			// Now remove from streams table
			streamsToUid.remove(t);
		}
	}

	public String getUidForStream(Tuple t) {
		return streamsToUid.get(t);
	}

	/*
	 * Returns whether the given connection (by 5-tuple) belongs to the given application UID
	 */
	public boolean belongsTo(Tuple c, String uid) {
		if (uid == null || c == null)
			return false;

		uid = uid.toLowerCase();

		Set<Tuple> t = null;
		if ((t = uidToStreams.get(uid)) != null) {
			return t.contains(c);
		}

		return false;
	}
}