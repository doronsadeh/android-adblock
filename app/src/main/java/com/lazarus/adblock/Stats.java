package com.lazarus.adblock;

public class Stats {
    public static long blockedCount = 0L;

    public static String toCountString() {
        if (blockedCount < 1000L)
            return Long.toString(blockedCount);
        else if (blockedCount < 10000L)
            return Long.toString(blockedCount/1000L) + "K";
        else if (blockedCount < 1000000L)
            return Long.toString(blockedCount/1000000L) + "M";
        else if (blockedCount < 1000000000L)
            return Long.toString(blockedCount/1000000000L) + "G";

        return "---";
    }
}
