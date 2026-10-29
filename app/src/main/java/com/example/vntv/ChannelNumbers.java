package com.example.vntv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stable channel numbers for the number keys: position (1-based) in the full merged list. */
public final class ChannelNumbers {
    private static List<Channel> ordered = new ArrayList<>();
    private static Map<Channel, Integer> numbers = new HashMap<>();

    private ChannelNumbers() {}

    /** Returns whether any channel's number changed. */
    public static boolean set(List<Channel> all) {
        if (all.equals(ordered)) return false;
        Map<Channel, Integer> map = new HashMap<>();
        for (int i = 0; i < all.size(); i++) map.put(all.get(i), i + 1);
        ordered = new ArrayList<>(all);
        numbers = map;
        return true;
    }

    /** 0 when the channel isn't in the current list. */
    public static int numberOf(Channel channel) {
        Integer n = numbers.get(channel);
        return n == null ? 0 : n;
    }

    public static Channel byNumber(int number) {
        return number >= 1 && number <= ordered.size() ? ordered.get(number - 1) : null;
    }
}
