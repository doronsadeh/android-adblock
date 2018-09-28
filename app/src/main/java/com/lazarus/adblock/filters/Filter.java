package com.lazarus.adblock.filters;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lazarus.adblock.connections.Connection;
import com.lazarus.adblock.connections.Direction;
import com.lazarus.adblock.connections.Tuple;

/*
 * TODO: document
 */
public abstract class Filter implements Comparable<Filter> {

	private static final String TAG = "Filter";

	protected Filter() {
		// Empty, allows only subclasses to be instantiated
	}
	
	//
	// Filter configuration parameters
	//
	
	// Filter type
	public enum Type {
		ONESHOT("ONESHOT"),
		PRE("PRE"),
		POST("POST");
		
		private String value;
		
		private Type(String t) {
			this.value = t;
		}
		
		public String value() {
			return value;
		}
	};

	// The connection of this stream
    protected Connection connection;

	//
	// Filter state
	//
	protected Type type;
	
	// Filter criteria for processing data
	protected Criteria criteria;
	
	// Chained filter list 
	protected Set<Filter> chained = new HashSet<>();
	
	// Is detected
	protected boolean on;
	
	// Filter specific data object
	protected Object filterData;
	
	//
	// Filter methods
	//
	
	public Filter(Type type, Criteria criteria, Object filterData) {
		this.type = type;
		this.criteria = criteria;
		this.on = false;
		this.filterData = filterData;
	}
	
	/*
	 * Chains a filter to this filter
	 */
	public void chain(Filter filter) {
		chained.add(filter);
	}
	
	public Type type() {
		return type;
	}
	
	public boolean isOn() {
		return on;
	}
	
	public Object filterData() {
		return filterData;
	}
	
	public void filterData(Object o) {
		this.filterData = o;
	}
	
	/*
	 * Main filter method, processing data returning an opinion
	 */
	public abstract List<Opinion> process(Connection connection,
										  List<Opinion> opinions,
										  ByteBuffer data,
										  Tuple tuple,
										  Direction dir,
										  Map<Filter, Filter> detected,
										  Object callerDataObject);
		
	protected boolean skipMe(Map<Filter, Filter> detected) {
		return detected.containsKey(this) && detected.get(this).on;
	}
	
	private String key() {
		String ccn = getClass().getCanonicalName();
		return ccn + "_" + type;
	}
	
	@Override
	public int compareTo(Filter another) {
		return this.key().compareTo(another.key());
	}
	
	@Override
	public boolean equals(Object another) {
		if (another instanceof Filter)
			return (this.key().equals(((Filter)another).key()));
		
		return false;
	}

	@Override
	public int hashCode() {
		return this.key().hashCode();
	}
	
	public void logFilter(String indent) {
		String s = "";
		s += indent + "name: " + getClass().getSimpleName() + ", " +
			 "type: " + type + ", " +  
			 "criteria: " + criteria.toString();
		
		Log.d(TAG, s);
		
		for (Filter c : chained) {
			c.logFilter(indent + "-");
		}
	}

}
