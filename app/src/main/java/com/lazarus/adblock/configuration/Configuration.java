package com.lazarus.adblock.configuration;

import android.content.Context;

import com.lazarus.adblock.lists.EasyList;

/*
 * TODO: document
 */
public class Configuration {

	private static final String TAG = "Configuration";

	public static Context context;

	private static Configuration instance = null;

	private static EasyList filterLists = null;

	private Configuration(Context _context) {
	    context = _context;
    }

    public static Configuration getInstance(Context context) {
		if (instance == null)
			instance = new Configuration(context);
			
		return instance;
	}

    public static void registerFilterLists(EasyList _filterLists) {
		filterLists = _filterLists;
    }

    public static EasyList getFilterLists() {
		return filterLists;
	}
}
