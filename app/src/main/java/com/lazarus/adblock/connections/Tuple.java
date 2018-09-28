package com.lazarus.adblock.connections;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.lazarus.adblock.exceptions.AdblockException;
import com.lazarus.adblock.utils.IPProtocol;

/*
 * ConnectionTuple
 * 
 * The 5-tuple uniquely defining a connection. 
 * 
 * Note that the source and destination (addresses and ports) are set 
 * according to the original 5-tuple as seen by the connection initiator.
 *  
 */	
public class Tuple implements Comparable<Tuple> {

	private static final String TAG = "adblock_tuple";

	/*
	 * taken from RFC793, RFC768
	 */
	public static final int PROTOCOL_TCP = 6;
	public static final int PROTOCOL_UDP = 17;

	private String uid;

	private int protocol;
	private InetAddress srcAddr;
	private InetAddress dstAddr;
	private int srcPort;
	private int dstPort;

	/*
	 * Generates a Map friendly key
	 * 
	 * TODO convert key to bytes array and update the compareTo, and equals to use 
	 * this more compact representation 
	 *       
	 */
	private String key() {
		return uid + "." +
				protocol + "." +
				srcAddr.getHostAddress() + "." +
				srcPort + "." +
				dstAddr.getHostAddress() + "." +
				dstPort;
	}

	public Tuple(String uid, InetAddress srcAddr, int srcPort, InetAddress dstAddr, int dstPort, int protocol) throws AdblockException {
//		// Disallow tuples that contain wildcards (if you need them, they are more like firewall rules, 
//		// implement them in another class NOT here)
//		if (srcAddr == null || dstAddr == null || srcPort <= 0 || dstPort <= 0 || protocol <= 0)
//			throw new AdblockException("Tuple must be specific, and not contain wildcard elements");

		this.uid = uid;
		this.srcAddr = srcAddr;
		this.srcPort = srcPort;
		this.dstAddr = dstAddr;
		this.dstPort = dstPort;
		this.protocol = protocol;
	}

	public IPProtocol protocol() {
		return IPProtocol.valueOf(protocol);
	}

	@Override
	public int compareTo(Tuple o) {
		return key().compareTo(o.key());
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Tuple) {
			return key().equals(((Tuple)o).key());
		}

		return false;
	}

	@Override
	public int hashCode() {
		return key().hashCode();
	}

	public String toString() {
		return key();
	}

	public InetSocketAddress getSrcSocketAddress() {
		InetSocketAddress inetSocketAddress = new InetSocketAddress(srcAddr, srcPort);
		return inetSocketAddress;
	}

	public InetSocketAddress getDstSocketAddress() {
		InetSocketAddress inetSocketAddress = new InetSocketAddress(dstAddr, dstPort);
		return inetSocketAddress;
	}

	public String getUid() {
		if (uid == null) {
			try {
				uid = getUidForTcpTuple(this);
			} catch (AdblockException e) {
				Log.d(TAG, "Cannot get UID for " + toString());
			}
		}

		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}
	
	private InetAddress reverse(String hexReverseIP) throws UnknownHostException {

		String addr = Integer.parseInt(hexReverseIP.substring(6,8), 16) + "." +
				Integer.parseInt(hexReverseIP.substring(4,6), 16) + "." +
				Integer.parseInt(hexReverseIP.substring(2,4), 16) + "." +
				Integer.parseInt(hexReverseIP.substring(0,2), 16);

		return InetAddress.getByName(addr);
	}

	private Map<Tuple, String> uidCache = new ConcurrentHashMap<Tuple, String>();

	private String readTCPxTable(int tSrcPort, String path, int ipVer) throws AdblockException {

		final String command = "su -c cat " + path;

		try {
			// Executes the command.
			Process process = Runtime.getRuntime().exec(command);

			// Reads stdout.
			// NOTE: You can write to stdin of the command using
			//       process.getOutputStream().
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = null;
			String[] fields;

			String fullLine = "";
			while ((line = reader.readLine()) != null) {

				line = line.trim();

				fields = line.trim().split("[\\s:]+");

				try {
					Integer.parseInt(fields[0]);
				} catch (NumberFormatException e) {
					continue;
				}

				if ((line.trim().split("\\s+"))[0].endsWith(":")) {
					fullLine = line;
					if (fields.length < 11) 
						continue;
				}
				else {
					fullLine += " " + line;
					fields = fullLine.trim().split("[\\s:]+");
				}

				if (fields.length < 11)
					continue;

				String srcIpReverse = fields[1];
				String srcPort = fields[2];
				String dstIpReverse = fields[3];
				String dstPort = fields[4];
				String uid = fields[11];
				
				if (ipVer == 6) {
					srcIpReverse = srcIpReverse.substring(24);
					dstIpReverse = dstIpReverse.substring(24);
				}

				InetAddress srcIP = reverse(srcIpReverse);
				InetAddress dstIP = reverse(dstIpReverse);
				int srcPortNumeral = Integer.parseInt(srcPort, 16);
				int dstPortNumeral = Integer.parseInt(dstPort, 16);

				// Tuple nT = new Tuple(uid, srcIP, srcPortNumeral, dstIP, dstPortNumeral, IPProtocol.TCP.value());
				// uidCache.put(nT, uid);
				if (srcPortNumeral == tSrcPort)
					return uid;
			}

			reader.close();

			// Waits for the command to finish.
			process.waitFor();

		} catch (IOException e) {
			throw new AdblockException(e.getMessage());
		} catch (InterruptedException e) {
			throw new AdblockException(e.getMessage());
		}
		
		return null;
	}

	private String getUidForTcpTuple(Tuple t) throws AdblockException {

//		String uidFromCache = null;
//		if ((uidFromCache = uidCache.get(t)) != null) {
//			Log.d(TAG, "Return uid " + uidFromCache + " from cache");
//			return uidFromCache;
//		}

		// Need to refresh and find the new connection
		uidCache.clear();
		String uid = null;
		int srcPortToMatch = t.getSrcSocketAddress().getPort();
		uid = readTCPxTable(srcPortToMatch, "/proc/net/tcp", 4);
		if (uid == null)
			uid = readTCPxTable(srcPortToMatch, "/proc/net/tcp6", 6);

		return uid;
//		return uidCache.get(t);
	}
}