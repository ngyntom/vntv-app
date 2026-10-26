package com.example.vntv;

import java.util.ArrayList;
import java.util.List;

/**
 * Shares the current channel list with PlayerActivity so it can offer in-player switching.
 * When PlayerActivity is launched straight from a saved URL (instant resume, before the full
 * playlist has even been fetched), it starts with an empty list here; MainActivity calls
 * {@link #update} once its background fetch finishes, and PlayerActivity - if it's still
 * listening - picks the real list up without needing to restart playback.
 */
public class ChannelHolder {
    public interface Listener {
        void onUpdated(List<Channel> channels);
    }

    public static List<Channel> current = new ArrayList<>();
    private static Listener listener;

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void update(List<Channel> channels) {
        current = channels;
        if (listener != null) listener.onUpdated(channels);
    }
}
