package com.example.vntv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaylistStore {
    private static final String PREFS = "vntv_prefs";
    private static final String KEY_URLS = "playlist_urls";
    private static final String KEY_FAVORITES = "favorite_urls";
    public static final String DEFAULT_VN_URL = "https://iptv-org.github.io/iptv/countries/vn.m3u";
    public static final String DEFAULT_AIO_URL =
            "https://raw.githubusercontent.com/khanh71/All-In-One-IPTV/main/http-iptv.m3u";
    // Listed first: channels merged across playlists keep the first playlist's order and URL.
    private static final List<String> DEFAULT_URLS = Arrays.asList(DEFAULT_AIO_URL, DEFAULT_VN_URL);

    public static List<String> getFavorites(Context ctx) {
        String stored = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAVORITES, null);
        List<String> list = new ArrayList<>();
        if (stored != null && !stored.trim().isEmpty()) {
            list.addAll(Arrays.asList(stored.split("\n")));
        }
        return list;
    }

    public static boolean isFavorite(Context ctx, String url) {
        return getFavorites(ctx).contains(url);
    }

    public static boolean toggleFavorite(Context ctx, String url) {
        List<String> favs = getFavorites(ctx);
        boolean nowFavorite;
        if (favs.contains(url)) {
            favs.remove(url);
            nowFavorite = false;
        } else {
            favs.add(url);
            nowFavorite = true;
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_FAVORITES, String.join("\n", favs))
                .apply();
        return nowFavorite;
    }

    public static void clearFavorites(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_FAVORITES).apply();
    }

    private static final String KEY_LAST_PLAYED = "last_played_url";
    private static final String KEY_LAST_PLAYED_NAME = "last_played_name";

    public static void setLastPlayed(Context ctx, String url, String name) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_PLAYED, url)
                .putString(KEY_LAST_PLAYED_NAME, name)
                .apply();
    }

    public static String getLastPlayed(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_PLAYED, null);
    }

    public static String getLastPlayedName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_PLAYED_NAME, null);
    }

    private static final String KEY_WANTS_HOME = "wants_home";

    /** Saved alongside everything else so restoring prefs onto a reinstall brings Home back too. */
    public static void setWantsHome(Context ctx, boolean wants) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WANTS_HOME, wants).apply();
    }

    public static boolean wantsHome(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WANTS_HOME, false);
    }

    private static final String WORKING_PREFIX = "working_url:";

    /** The stream that last played for a channel (keyed by its streamUrl), tried first next time. */
    public static String getWorkingUrl(Context ctx, String channelUrl) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(WORKING_PREFIX + channelUrl, null);
    }

    public static void setWorkingUrl(Context ctx, String channelUrl, String workingUrl) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (workingUrl.equals(prefs.getString(WORKING_PREFIX + channelUrl, null))) return;
        prefs.edit().putString(WORKING_PREFIX + channelUrl, workingUrl).apply();
    }

    private static final String KEY_HOTKEY_URL = "hotkey_url";
    private static final String KEY_HOTKEY_NAME = "hotkey_name";

    public static void setHotkeyChannel(Context ctx, String url, String name) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_HOTKEY_URL, url)
                .putString(KEY_HOTKEY_NAME, name)
                .apply();
    }

    public static String getHotkeyUrl(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HOTKEY_URL, null);
    }

    public static String getHotkeyName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HOTKEY_NAME, null);
    }

    private static final String KEY_ON_MENU = "on_menu";

    /** Where the user actually left off: true once the channel grid is genuinely on screen
     *  (including the moment Back is pressed from the player, before the screen even changes),
     *  false once a channel starts playing. A fresh launch only auto-resumes when this is false -
     *  if the user had backed out to the grid, that's where the app should reopen too. */
    public static void setOnMenu(Context ctx, boolean onMenu) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON_MENU, onMenu).apply();
    }

    public static boolean wasOnMenu(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON_MENU, false);
    }

    public static List<String> getUrls(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_URLS, null);
        List<String> urls = new ArrayList<>();
        if (stored == null || stored.trim().isEmpty()) {
            urls.addAll(DEFAULT_URLS);
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
        if (urls.isEmpty()) urls.addAll(DEFAULT_URLS);
        saveUrls(ctx, urls);
    }

    public static void saveUrls(Context ctx, List<String> urls) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_URLS, String.join("\n", urls)).apply();
    }
}
