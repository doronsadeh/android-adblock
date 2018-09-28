package com.lazarus.adblock.filters;

import com.lazarus.adblock.connections.Direction;
import com.lazarus.adblock.exceptions.AdblockException;
import com.lazarus.adblock.filters.Filter.Type;
import com.lazarus.adblock.filters.post.AdBlockFilter;
import com.lazarus.adblock.filters.pre.HttpFilter;
import com.lazarus.adblock.filters.pre.SSLFilter;
import com.lazarus.adblock.logic.DetectionLogic;

import java.util.HashSet;
import java.util.Set;

public class FilterGraph {

	private DetectionLogic detectionLogic = new DetectionLogic();

	private Set<Filter> filters = new HashSet<>();

    private void logFilter(Set<Filter> filterSet, String headeline) {
        for (Filter f : filterSet) {
            f.logFilter("");
        }
    }

    public Set<Filter> filters() {
        return filters;
    }

    public FilterGraph() throws AdblockException {
		try {
		    // Create top level filters
            Filter httpFilter = new HttpFilter(Type.PRE, new Criteria(Direction.UPSTREAM, Criteria.Protocol.TCP));
            Filter sslFilter = new SSLFilter(Type.PRE, new Criteria(Direction.UPSTREAM, Criteria.Protocol.TCP));

            // Create follow up filter
            Filter adblockFilterOverHttp = new AdBlockFilter(Type.POST, new Criteria(Direction.UPSTREAM, Criteria.Protocol.TCP));

            // Chain top level filters filters w/ad block follow up filter
            httpFilter.chain(adblockFilterOverHttp);
            sslFilter.chain(adblockFilterOverHttp);

            // Set the top filters
            filters.add(httpFilter);
            filters.add(sslFilter);

			// DEBUG
			logFilter(filters, "-- FilterGraph --");

		} catch (IllegalArgumentException e) {
			throw new AdblockException(e.getMessage());
		}
    }
}
