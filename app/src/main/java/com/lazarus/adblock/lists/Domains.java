package com.lazarus.adblock.lists;

import android.text.TextUtils;
import android.util.Log;

import com.abahgat.suffixtree.GeneralizedSuffixTree;

import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO support wildcards!!!

public class Domains implements RuleSet {

    private static final String TAG = "Domains";

    // This cost is used to mark that a domain should be (also) matched w/o path pattern
    // It needs to be explicitly checked by apply, and matched to a domain w/o checking its path
    private static final String EMPTY_STRING = "__$__EMPTY__$__";

    private class Data {
        private Options options;
        private PathPatterns pathPatterns;

        private Data(Options options, PathPatterns patterns) {
            this.options = options;
            this.pathPatterns = patterns;
        }
    }

    //
    // Domains as read from data files
    //
    private Map<String, Map<String, Data>> rawDomainsWithPath = new HashMap<>();    // Domains w/paths as read from lists
    private Map<String, Boolean> rawPureDomains = new HashMap<>();                  // Pure domains as read from lists

    //
    // Domains w/o paths (pure domains)
    //
    private Trie pureDomains = null;

    //
    // Domains w/paths
    //
    private GeneralizedSuffixTree domainsWithPath = new GeneralizedSuffixTree();    // Domains index mapping domainsWithPath suffix to int
    private Map<Integer, List<Data>> domainsWithPathDataIndex = new HashMap<>();        // Mapping int to list of Data items linked under domain suffix

    public void add(String domainWorWOPattern, Options options) {

        String domainName = domainWorWOPattern;
        String pathPattern = null;

        if (domainWorWOPattern.contains("/")) {
            String[] fields = domainWorWOPattern.split("/");
            domainName = fields[0].toLowerCase();
            if (fields.length > 1) {
                if (fields[1].equals("^")) {
                    // We are done, this is an exact domain
                } else {
                    // We have a trailing pattern
                    pathPattern = TextUtils.join("/", Arrays.asList(fields).subList(1, fields.length)).toLowerCase();
                    if (pathPattern.endsWith("^"))
                        pathPattern = pathPattern.substring(0, pathPattern.length() - 1);
                }
            }
        }

        if (domainName.endsWith("^"))
            domainName = domainName.substring(0, domainName.length() - 1);

        if (null == pathPattern) {
            // Pure domain
            rawPureDomains.put(domainName, true);
        } else {
            if (rawDomainsWithPath.containsKey(domainName)) {
                if (null != pathPattern) {
                    if (!rawDomainsWithPath.get(domainName).containsKey(pathPattern)) {
                        rawDomainsWithPath.get(domainName).put(pathPattern, new Data(options, null));
                    }
                } else {
                    // We need to match the domain w/o a pattern
                    rawDomainsWithPath.get(domainName).put(EMPTY_STRING, new Data(options, null));
                }
            } else {
                Map<String, Data> m = new HashMap<>();
                if (null != pathPattern) {
                    m.put(pathPattern, new Data(options, null));
                } else {
                    // We need to match the domain w/o a pattern
                    m.put(EMPTY_STRING, new Data(options, null));
                }

                rawDomainsWithPath.put(new String(domainName.getBytes(), Charset.forName("UTF-8")), m);
            }
        }
    }

    public void build() {

        int runningIndex = 0;

        //
        // Domains w/o path-patterns (pure)
        //
        pureDomains = Trie.builder()
                        .ignoreOverlaps()
                        .onlyWholeWords()
                        .addKeywords(rawPureDomains.keySet())
                        .build();

        //
        // Domains w/path-patterns
        //
        for (String domainName : rawDomainsWithPath.keySet()) {
            // Create the Aho-Cor. Trie over the domain list of patterns
            PathPatterns p = new PathPatterns();

            for (String pattern : rawDomainsWithPath.get(domainName).keySet()) {

                Data data = rawDomainsWithPath.get(domainName).get(pattern);

                if (null != data.options && data.options.size() > 0) {
                    // We have a domain pattern that has an option, we MUST create a single rule for it
                    PathPatterns singlePattern = new PathPatterns();
                    singlePattern.add(pattern, data.options);
                    singlePattern.build();

                    Collection<Integer> _existingDomains;
                    if ((_existingDomains = domainsWithPath.search(domainName)).size() > 0) {
                        Data _d = new Data(data.options, singlePattern);
                        for (int i : _existingDomains) {
                            domainsWithPathDataIndex.get(i).add(_d);
                        }
                    } else {
                        try {
                            List<Data> _l = new ArrayList<>();
                            _l.add(new Data(data.options, singlePattern));
                            domainsWithPath.put(domainName, runningIndex);
                            domainsWithPathDataIndex.put(runningIndex, _l);
                        } catch (IllegalArgumentException e) {
                            Log.e(TAG, "Cannot add domain to suffix tree: " + domainName);
                        }
                    }

                    runningIndex++;

                } else {
                    p.add(pattern, null);
                }
            }

            if (p.size() > 0) {
                // Build Trie for all patterns that DO NOT have options
                p.build();

                try {
                    // Add those under the domain
                    List<Data> _l = new ArrayList<>();
                    _l.add(new Data(null, p));
                    domainsWithPath.put(domainName, runningIndex);
                    domainsWithPathDataIndex.put(runningIndex, _l);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Cannot add domain to suffix tree: " + domainName);
                }

                runningIndex++;
            }
        }
    }

    @Override
    public Boolean apply(URL url, String referrer) {

        String urlDomain = url.getHost();

        //
        // Search in pure domains first (optimization)
        //
        if (null != pureDomains) {
            Collection<Emit> emits = pureDomains.parseText(urlDomain);
            if (emits.size() > 0)
                return true;
        }

        //
        // Search in domains w/patterns
        //
        Collection<Integer> _existingDomains;
        if ((_existingDomains = this.domainsWithPath.search(urlDomain)).size() > 0) {
            for (int i : _existingDomains) {
                for (Data data : domainsWithPathDataIndex.get(i)) {
                    if (null != data.pathPatterns &&
                            (data.pathPatterns.apply("http://" + urlDomain + "/" + EMPTY_STRING, referrer) ||
                                    data.pathPatterns.apply(url, referrer))) {
                        if (null != data.options) {
                            Options.ValidationResults vr = data.options.validate(urlDomain, referrer);
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
                    }
                }
            }
        }

        return false;
    }

    @Override
    public Boolean apply(String sUrl, String referrer) {
        try {
            return this.apply(new URL(sUrl), referrer);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL provided, cannot apply over " + sUrl);
            e.printStackTrace();
            return false;
        }
    }
}
