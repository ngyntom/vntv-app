package com.example.vntv;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Folds the same channel coming from several playlists into one entry: the first occurrence
 * keeps its place in the list and its stream URL (so favorites stay put), and the other
 * playlists' URLs become its fallbacks.
 */
public final class ChannelMerger {
    private ChannelMerger() {}

    public static List<Channel> merge(List<Channel> all) {
        Map<String, List<Channel>> groups = new LinkedHashMap<>();
        for (Channel c : all) {
            String key = key(c.name);
            List<Channel> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(c);
        }
        List<Channel> merged = new ArrayList<>(groups.size());
        for (List<Channel> group : groups.values()) {
            merged.add(group.size() == 1 ? group.get(0) : combine(group));
        }
        return merged;
    }

    private static Channel combine(List<Channel> group) {
        Channel first = group.get(0);
        String name = first.name;
        String logo = first.logoUrl;
        String quality = first.quality;
        List<String> fallbacks = new ArrayList<>();
        for (Channel c : group) {
            // Prefer the spelling with Vietnamese accents ("Vĩnh Long 1" over "Vinh Long TV 1").
            if (!hasDiacritics(name) && hasDiacritics(c.name)) name = c.name;
            if (isEmpty(logo)) logo = c.logoUrl;
            if (isEmpty(quality)) quality = c.quality;
            for (String url : c.allUrls()) {
                if (!url.equals(first.streamUrl) && !fallbacks.contains(url)) fallbacks.add(url);
            }
        }
        return new Channel(name, quality, logo, first.streamUrl, first.groupTitle, first.sourceUrl, fallbacks);
    }

    /** "Vĩnh Long 1", "Vinh Long TV 1" and "VINHLONG-TV1" all map to "vinhlong1". */
    static String key(String name) {
        String s = stripDiacritics(name).toLowerCase(Locale.ROOT)
                .replaceAll("\\bhd\\b", "")
                .replaceAll("[^a-z0-9]", "");
        return s.replace("tv", "");
    }

    /** Accent-free form for matching, keeping the base letters ("Đà Nẵng" -> "Da Nang"). */
    public static String stripDiacritics(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
    }

    private static boolean hasDiacritics(String s) {
        return !s.equals(stripDiacritics(s));
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
