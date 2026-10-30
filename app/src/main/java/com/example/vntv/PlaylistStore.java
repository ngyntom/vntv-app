package com.example.vntv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /** Replaces the favorites with exactly these URLs, in this order. */
    public static void setFavorites(Context ctx, List<String> urls) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_FAVORITES, String.join("\n", urls))
                .apply();
    }

    /** Removes every stream of the channel from favorites if any is there, otherwise adds it.
     *  Favorites saved before playlists were merged may hold a URL that is now only one of the
     *  channel's fallbacks, so checking streamUrl alone would add a duplicate instead. */
    public static boolean toggleFavorite(Context ctx, Channel channel) {
        List<String> favs = getFavorites(ctx);
        boolean wasFavorite = favs.removeAll(channel.allUrls());
        if (!wasFavorite) favs.add(channel.streamUrl);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_FAVORITES, String.join("\n", favs))
                .apply();
        return !wasFavorite;
    }

    private static final String KEY_DEAD_CHANNELS = "dead_channels";

    /** streamUrls of channels the last health check found with no working link. */
    public static Set<String> getDeadChannels(Context ctx) {
        String stored = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DEAD_CHANNELS, null);
        Set<String> set = new HashSet<>();
        if (stored != null && !stored.trim().isEmpty()) set.addAll(Arrays.asList(stored.split("\n")));
        return set;
    }

    public static void setDeadChannels(Context ctx, Set<String> dead) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_DEAD_CHANNELS, String.join("\n", dead))
                .apply();
    }

    private static final String KEY_AUTO_PLAY = "auto_play";

    /** Whether opening the app resumes a channel on its own; on by default. */
    public static boolean getAutoPlay(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_PLAY, true);
    }

    public static void setAutoPlay(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_PLAY, enabled).apply();
    }

    private static final String KEY_CATEGORY_ORDER = "category_order";

    public static List<String> getCategoryOrder(Context ctx) {
        String stored = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CATEGORY_ORDER, null);
        List<String> list = new ArrayList<>();
        if (stored != null && !stored.trim().isEmpty()) list.addAll(Arrays.asList(stored.split("\n")));
        return list;
    }

    public static void setCategoryOrder(Context ctx, List<String> order) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CATEGORY_ORDER, String.join("\n", order))
                .apply();
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

    public static final int CLOCK_TOP_LEFT = 0;
    public static final int CLOCK_TOP_RIGHT = 1;
    public static final int CLOCK_BOTTOM_LEFT = 2;
    public static final int CLOCK_BOTTOM_RIGHT = 3;
    private static final String KEY_CLOCK_POSITION = "clock_position";

    /** Corner of the in-playback clock; bottom-right by default, where channel logos rarely sit. */
    public static int getClockPosition(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CLOCK_POSITION, CLOCK_BOTTOM_RIGHT);
    }

    public static void setClockPosition(Context ctx, int position) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_CLOCK_POSITION, position).apply();
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
