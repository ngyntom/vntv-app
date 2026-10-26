package com.example.vntv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaylistStore {
    private static final String PREFS = "vntv_prefs";
    private static final String KEY_URLS = "playlist_urls";
    private static final String KEY_DEFAULT_NAME = "default_channel_name";
    private static final String KEY_DEFAULT_URL = "default_channel_url";
    public static final String DEFAULT_VN_URL = "https://iptv-org.github.io/iptv/countries/vn.m3u";

    public static void setDefaultChannel(Context ctx, Channel channel) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_DEFAULT_NAME, channel.name)
                .putString(KEY_DEFAULT_URL, channel.streamUrl)
                .apply();
    }

    public static void clearDefaultChannel(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_DEFAULT_NAME)
                .remove(KEY_DEFAULT_URL)
                .apply();
    }

    public static String getDefaultChannelUrl(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEFAULT_URL, null);
    }

    public static String getDefaultChannelName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEFAULT_NAME, null);
    }

    public static List<String> getUrls(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_URLS, null);
        List<String> urls = new ArrayList<>();
        if (stored == null || stored.trim().isEmpty()) {
            urls.add(DEFAULT_VN_URL);
        } else {
            urls.addAll(Arrays.asList(stored.split("\n")));
        }
        return urls;
    }

    public static void addUrl(Context ctx, String url) {
        List<String> urls = getUrls(ctx);
        if (!urls.contains(url)) {
            urls.add(url);
            saveUrls(ctx, urls);
        }
    }

    public static void removeUrl(Context ctx, String url) {
        List<String> urls = getUrls(ctx);
        urls.remove(url);
        if (urls.isEmpty()) urls.add(DEFAULT_VN_URL);
        saveUrls(ctx, urls);
    }

    public static void saveUrls(Context ctx, List<String> urls) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_URLS, String.join("\n", urls)).apply();
    }
}
