package com.lazarus.adblock.connections;

/*
 * An X-list item, allowing wildcarded list rules, where either UID and/or
 * tuple parameters are '*'. A match is calculated taking those wildcards into account
 * on both the rule and input checked against the list 
 */
public class XListItem implements Comparable<XListItem> {
	private String uid;

	public XListItem(String uid) {
		this.uid = (uid == null ? null : uid.toLowerCase().trim());
	}

	@Override
	public int compareTo(XListItem o) {
		return (uid.compareTo(o.uid));
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof XListItem) {
			XListItem ox = (XListItem)o;
			return (this.uid != null && ox.uid != null && this.uid.equals(ox.uid));
		}

		return false;
	}
	
	@Override
	public int hashCode() {
		return uid.hashCode();
	}
}