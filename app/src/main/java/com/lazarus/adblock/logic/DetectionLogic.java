package com.lazarus.adblock.logic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lazarus.adblock.filters.Filter;
import com.lazarus.adblock.filters.Opinion;
import com.lazarus.adblock.filters.Opinion.Mode;

/*
 * TODO: document
 */
public class DetectionLogic {

	public static Opinion resolve(List<Opinion> opinions) {
		
		boolean bypass = false;
		boolean block = false;
		boolean drop = false;
		Map<Filter, Filter> detected = new HashMap<Filter, Filter>();

		String blockedEntity = "<none>";

		for (Opinion o : opinions) {
			if (o.mode == Mode.BYPASS) {
				bypass = true;
			}

			if (o.mode == Mode.BLOCK) {
		        blockedEntity = o.entity;
				block = true;
			}

			if (o.mode == Mode.DROP) {
				drop = true;
			}

			// Collect all detections from all opinions
			for (Filter fo : o.detected.keySet())
				detected.put(fo, fo);
		}
		
		if (block)
			return new Opinion(Mode.BLOCK, detected, blockedEntity);

		if (drop)
			return new Opinion(Mode.DROP, detected, "<NA>");

		if (bypass)
			return new Opinion(Mode.BYPASS, detected, "<NA>");
		
		return new Opinion(Mode.PASS, detected, "<NA>");
	}
}
