package com.lazarus.adblock.lists;

import java.util.HashMap;
import java.util.Map;

public class CSSRules {

    private Map<String, Boolean> cssTags = new HashMap<>();

    private String style = null;

    public void add(String selector) {
        cssTags.put(selector, true);
    }

    public void build() {
        style = "<style> {";
        for (String selector : cssTags.keySet()) {
            style += (selector + ";");
        }
        style += "}!important</style>";
    }

    public String get() {
        return style;
    }
}
