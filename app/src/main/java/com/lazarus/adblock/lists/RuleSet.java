package com.lazarus.adblock.lists;

import java.net.URL;

public interface RuleSet {
    Boolean apply(URL url, String referrer);
    Boolean apply(String sUrl, String referrer);
}
