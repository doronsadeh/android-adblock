package com.lazarus.adblock.connections;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lazarus.adblock.exceptions.AdblockException;
import com.lazarus.adblock.filters.Filter;
import com.lazarus.adblock.filters.FilterGraph;
import com.lazarus.adblock.filters.Opinion;
import com.lazarus.adblock.filters.Opinion.Mode;
import com.lazarus.adblock.logic.DetectionLogic;

/*
 * TODO: document
 */
public abstract class Connection {

	private static final String TAG = "Connection";

	protected Set<Filter> rootLevelFilters;

	protected SelectableChannel localChannel;
	protected SelectableChannel remoteChannel;

	//buffers for reading from local/remote channels
	protected ByteBuffer localChannelByteBuffer;
	protected ByteBuffer remoteChannelByteBuffer;

	/*
	 * private incoming/outgoing opinions list.
	 * each buffer processing from local/remote channel uses
	 * the outgoing/incoming opinions list.
	 */
	private List<Opinion> incomingOpinions;
	private List<Opinion> outgoingOpinions;

	// connection tuple (must save - we use it for filtering)
	protected Tuple tuple;

	/*
	 * indicates whether need to process traffic through the filters
	 * initially all traffic is filtered.
	 */
	// DEBUG setting to true to test w/o filters
	protected boolean bypass = false;

	/*
	 * the connection's proxy mode indicates whether to block or pass
	 * traffic from source channel to target channel.
	 * default value is pass, i.e. all packets are passed to the target channel.
	 * this member is valid only when not bypassing traffic (bypass == false).
	 * TODO: document- the code never uses the default value -> this member may be redundant.
	 */
	protected Mode connectionProxyMode = Mode.PASS;

	// List of filters that have already fired (detected) on this connection's traffic
	// This list is kept here and provided on each call of this connection to the singleton filter
	// bank, so it knows not to re-run filters for no need. E.g. an Http filter that has detected this 
	// connection as Http does not need to be run on each Http message
	protected Map<Filter, Filter> detected = new HashMap<Filter, Filter>();

	private int tlsClientHellos = 0;

    protected Connection() throws AdblockException {
		rootLevelFilters = (new FilterGraph()).filters();
		incomingOpinions = new ArrayList<Opinion>();
		outgoingOpinions = new ArrayList<Opinion>();
	}

	protected static Tuple getTuple(int protocol, int sourcePort) throws AdblockException
	{
		Tuple tuple = null;
		String sourcePortStr = Integer.toString(sourcePort);

		final String fileName = "/proc/net/ip_conntrack";
		final String command = "su -c cat " + fileName;

		try {
			// Executes the command.
			Process process = Runtime.getRuntime().exec(command);

			// Reads stdout.
			// NOTE: You can write to stdin of the command using
			//       process.getOutputStream().
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = null;
			String[] strings;

			while ((line = reader.readLine()) != null) {
				strings = line.split("[\\s:]+");

				/*
				 * /proc/net/ip_conntrack structure:
				 * 
				 * 1. Protocol name.
				 * 2. Protocol number. (6 = TCP. 17 = UDP.)
				 * 3. Seconds until this entry expires.
				 * 4. TCP only: TCP connection state.
				 * 5. Source address of original-side packets (packets from the side that initiated the connection).
				 * 6. Destination address of original-side packets.
				 * 7. Source port of original-side packets.
				 * 8. Destination port of original-side packets.
				 * 9. "[UNREPLIED]", if this connection has not seen traffic in both directions. Otherwise not present.
				 * 10. Source address of reply-side packets (packets from the side that received the connection).
				 * 11. Destination address of reply-side packets.
				 * 12. Source port of reply-side packets.
				 * 13. Destination port of reply-side packets.
				 * 14. "[ASSURED]", if this connection has seen traffic in both directions (for UDP) or an ACK in an ESTABLISHED connection (for TCP). Otherwise not present.
				 * 15. Use count of this connection structure.
				 */

				//skip other protocols
				if (!strings[1].equals(Integer.toString(protocol)))
					continue;

				String src = strings[4].substring(4);//"src=x.x.x.x"
				String dst = strings[5].substring(4);//"dst=x.x.x.x"
				String sport = strings[6].substring(6);//"sport=x"
				String dport = strings[7].substring(6);//"dport=x"

				//we found the socket information
				if (sourcePortStr.equals(sport)) {
					tuple = new Tuple(null,
							InetAddress.getByName(src), Integer.valueOf(sport),
							InetAddress.getByName(dst), Integer.valueOf(dport),
							protocol);

					// Will be set on first packet (net tables are not yet set at this point)
					tuple.setUid(null);

					Log.v(TAG, "found destination socket for source port " + sport + ": " + dst + ", port " + dport);
					break;
				}
			}

			reader.close();

			// Waits for the command to finish.
			process.waitFor();

			if (tuple == null)
				throw new AdblockException("cannot find destination socket for source port " + sourcePort);

		} catch (IOException e) {
			throw new AdblockException(e.getMessage());
			//throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new AdblockException(e.getMessage());
			//throw new RuntimeException(e);
		}

		return tuple;
	}

	public SelectableChannel getLocalChannel() {

		return localChannel;
	}
	public SelectableChannel getRemoteChannel() {

		return remoteChannel;
	}

	public void setLocalChannelBuffer(ByteBuffer byteBuffer) {

		this.localChannelByteBuffer = byteBuffer;
	}

	public void setRemoteChannelBuffer(ByteBuffer byteBuffer) {

		this.remoteChannelByteBuffer = byteBuffer;
	}

	public Tuple getTuple() {
		return tuple;
	}

	/*
	 * this method completes the closing flow of the connection.
	 * the local/remote channels are already closed by the connection-
	 * specific subclass.
	 * TODO: document the connection closing flow
	 */
	public void close() {

		/*
		 * connection-specific close() method has been called,
		 * local/remote channels are closed.
		 * 
		 * TODO: implement complete closing of generic connection
		 */

	}

    public void countTLSClientHellos() {
	    tlsClientHellos++;

	    // DEBUG
	    if (tlsClientHellos > 1)
	        Log.d(TAG, "Found more than one TLS ClientHellos [" + tlsClientHellos + "] on connection");
    }

	public void bypass() {
		bypass = true;
	}

	public Opinion process(ByteBuffer data, Tuple tuple, Direction dir, Map<Filter, Filter> detected, Object callerDataObject) {

		// use a dedicated pre-allocated opinions list, clear it before use.
		List<Opinion> opinions = (dir == Direction.DOWNSTREAM ? incomingOpinions : outgoingOpinions);

		opinions.clear();

		for (Filter f : rootLevelFilters) {
			opinions = f.process(this, opinions, data, tuple, dir, detected, callerDataObject);
		}

		return DetectionLogic.resolve(opinions);
	}

	public String toString() {
		if (tuple != null)
			return tuple.toString();

		return "Connection tuple not set!";
	}

	/*
	 * abstract methods to be implemented by the specific subclass.
	 * read and write from/to channels are connection type specific.
	 */
	public abstract void readFromLocalChannel();
	public abstract void readFromRemoteChannel();

}
