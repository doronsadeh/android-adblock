package com.lazarus.adblock.lists;

import android.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

//
// EasyList rule options
//
public class Options {

    private static final String TAG = "Options";

    public enum ThirdParty {
        ON,
        OFF,
        NA
    }

    private class Option {
        private ThirdParty thirdParty = ThirdParty.NA;
        private Map<String, Boolean> domainsList = new HashMap<>();

        private Option(String option) {
            option = option.trim().toLowerCase();

            if (option.contains("third-party")) {
                if (option.startsWith("~")) {
                    thirdParty = ThirdParty.OFF;
                }
                else {
                    thirdParty = ThirdParty.ON;
                }
            }
            else if (option.startsWith("domain")) {
                String[] domainsListStrs = option.split("=")[1].split("|");
                for (String s : domainsListStrs) {
                    domainsList.put(s.trim().toLowerCase(), true);
                }
            }
            else {
                // TODO support other options parsing
            }
        }
    }

    private Map<String, Option> optionsMap= new HashMap<>();

    private Option isOption(String option) {
        return this.optionsMap.get(option);
    }

    public Options(String options) {
        String[] o = options.split(",");
        for (String s : o) {
            optionsMap.put(s.toLowerCase(), new Option(s));
        }
    }

    public int size() {
        return this.optionsMap.size();
    }

    public Collection<String> isDomainList() {
        Option option = null;
        if (null != (option = this.isOption("domain"))) {
            return option.domainsList.keySet();
        }

        return null;
    }

    public ThirdParty isThirdParty() {
        Option option = null;
        if (null != (option = this.isOption("third-party"))) {
            return option.thirdParty;
        }

        return ThirdParty.NA;
    }

    public enum ValidationResults {
        BLOCK,
        PASS,
        OPTION_EXISTS_BUT_NO_DECISION,
        NO_RELEVANT_OPTIONS
    }

    public ValidationResults validate(String urlDomain, String referrer) {
        // Get the pattern original options
        Collection<String> domains = this.isDomainList();
        Options.ThirdParty thirdParty = this.isThirdParty();

        // DEBUG
        if (urlDomain.endsWith("sekindo.com") ||
                urlDomain.endsWith("pagead2.googlesyndication.com")) {
            Log.d(TAG, "DEBUG");
        }

        int optionsCount = 0;

        // Check third party
        if (null != referrer &&
                 null != thirdParty &&
                 thirdParty != Options.ThirdParty.NA) {
            optionsCount++;
            if (thirdParty == Options.ThirdParty.ON) {
                if (!urlDomain.endsWith(referrer)) {
                    return ValidationResults.BLOCK;
                }
            }
            else if (thirdParty == Options.ThirdParty.OFF) {
                if (urlDomain.endsWith(referrer)) {
                    return ValidationResults.BLOCK;
                }
            }
        }

        // Check pattern domain list options, if any
        if (null != domains) {
            optionsCount++;
            for (String d : domains) {
                if (d.startsWith("~")) {
                    d = d.substring(1);
                    if (urlDomain.endsWith(d)) {
                        // If we are specifically told NOT to apply pattern to this page
                        return ValidationResults.PASS;
                    }
                } else {
                    if (urlDomain.endsWith(d)) {
                        // If we are specifically to apply ONLY to this page
                        return ValidationResults.BLOCK;
                    }
                }
            }
        }

        if (optionsCount > 0)
            return ValidationResults.OPTION_EXISTS_BUT_NO_DECISION;

        return ValidationResults.NO_RELEVANT_OPTIONS;

    }
}
