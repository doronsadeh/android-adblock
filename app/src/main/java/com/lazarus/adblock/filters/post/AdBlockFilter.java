package com.lazarus.adblock.filters.post;

import android.util.Log;

import com.lazarus.adblock.connections.Connection;
import com.lazarus.adblock.connections.Direction;
import com.lazarus.adblock.connections.Tuple;
import com.lazarus.adblock.filters.Criteria;
import com.lazarus.adblock.filters.Filter;
import com.lazarus.adblock.filters.Opinion;
import com.lazarus.adblock.filters.Opinion.Mode;
import com.lazarus.adblock.filters.pre.HttpFilter.HttpData;
import com.lazarus.adblock.filters.pre.SSLFilter.SSLData;
import com.lazarus.adblock.lists.EasyList;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class AdBlockFilter extends Filter {

	private static final String TAG = "AdBlockFilter";

	@Override
	public List<Opinion> process(Connection connection, List<Opinion> opinions, ByteBuffer data, Tuple tuple, Direction dir, Map<Filter, Filter> detected, Object callerDataObject) {

		if (!criteria.valid(dir, tuple))
			return opinions;

		if (!skipMe(detected)) {

			// Do THIS filter's processing
			if (callerDataObject != null) {

				URL url = null;
				String referrer = null;

				if (callerDataObject instanceof HttpData) {
					// Called from Http pre-filter
					url = ((HttpData)callerDataObject).getURL();
					referrer = ((HttpData)callerDataObject).getReferrer();
				}

				if (callerDataObject instanceof SSLData) {
					// Called from Dns pre-filter
					url = ((SSLData)callerDataObject).getURL();
                    referrer = ((SSLData)callerDataObject).getReferrer();
				}

				if (url != null && EasyList.getInstance().isBlocked(url, referrer)) {
					// Ad blocked on URI, but don't modify detected filters (as this is POST filter)
					opinions.add(new Opinion(Mode.BLOCK, detected, url.toExternalForm()));

					Log.d(TAG, "AdBlock blocking ... " + url.toExternalForm());
				}
			}
		}

		// TODO: do we have to process the chained filters if block ?
		// ANSWER: yes, since this is just his opinion, and tomorrow it will have to consult 
		//         some other logic, so no, you can't make local decisions here as it will fuck
        //         the architecture around the corner

		// Do chained filters' processing
		for (Filter c : chained) {
			opinions = c.process(connection, opinions, data, tuple, dir, detected, null);
		}

		return opinions;

	}

	public AdBlockFilter(Type type, Criteria criteria) {
        // Create a copy of the filter (make sure to USE the EXISTING ad block list so we won't allocate it again and again!
		super(type, criteria, null);
	}
}
