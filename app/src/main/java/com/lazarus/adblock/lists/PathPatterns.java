package com.lazarus.adblock.lists;

import android.util.Log;

import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class PathPatterns implements RuleSet {

    private static final String TAG = "PathPatterns";

    private Map<String, Options> rawPatterns = new HashMap<>();

    private Trie trie;

    public void add(String pattern, Options options) {
        rawPatterns.put(pattern, options);
    }

    public void build() {
        // Build the Aho-Corasick Trie for patterns
        trie = Trie.builder()
                .ignoreOverlaps()
                .onlyWholeWords()
                .addKeywords(rawPatterns.keySet())
                .build();
    }

    public int size() {
        return rawPatterns.size();
    }

    @Override
    public Boolean apply(URL url, String referrer) {

        String sUrl = url.toExternalForm();

        String urlDomain = url.getHost();
        String path = (null == url.getPath() ? "" : url.getPath()) + (null == url.getQuery() ? "" : ("?" + url.getQuery()));

        if (null == trie)
            return false;

        Collection<Emit> emits = trie.parseText(path);

        // Check out domain list options
        for (Emit e : emits) {

            String pattern = e.getKeyword();

            if (rawPatterns.containsKey(pattern)) {
                // If we have options related to pattern, validate and trigger accordingly
                if (null != rawPatterns.get(pattern)) {
                    Options.ValidationResults vr = rawPatterns.get(pattern).validate(urlDomain, referrer);
                    switch (vr) {
                        case BLOCK:
                            return true;
                        case PASS:
                            return false;
                        case OPTION_EXISTS_BUT_NO_DECISION:
                            // If there were relevant options, but non of them took a decision
                            // then we MUST NOT block, e.g. domain options exists but no relevant domains
                            // were found that trigger the blocking pattern
                            return false;
                        case NO_RELEVANT_OPTIONS:
                            // If no relevant options, we block as a pattern was detected
                            return true;
                    }
                }
                else {
                    // If no options, we have a pattern, so block
                    return true;
                }
            }
        }

        // If no emitted pattern, don't block
        return false;
    }

    @Override
    public Boolean apply(String sUrl, String referrer) {
        URL url = null;
        try {
            url = new URL(sUrl);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed url: " + sUrl + " (cannot block based on it, returning false)");
            e.printStackTrace();
            return false;
        }

        return this.apply(url, referrer);
    }


}
