package com.example.vntv;

import android.content.Context;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Probes every channel's streams to find which channels have no working link left. */
public final class ChannelHealth {
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";
    private static final int TIMEOUT_MS = 6000;
    private static final int PARALLEL = 16;
    private static final int RETRY_PARALLEL = 4;
    private static final long RETRY_DELAY_MS = 2000;

    public static final class Result {
        /** streamUrl of each channel with no working link. */
        public final Set<String> dead = new HashSet<>();
        /** streamUrl -> first working link found for it. */
        public final Map<String, String> alive = new HashMap<>();
    }

    private ChannelHealth() {}

    /** Blocking; run off the main thread. */
    public static Result check(Context ctx, List<Channel> channels) {
        Result result = new Result();
        List<Channel> failed = probe(ctx, channels, PARALLEL, result);
        if (!failed.isEmpty()) {
            // Many channels share a host (e.g. dethich.pw) that drops some requests when hit
            // with a burst; give the failures a slower second chance before calling them dead.
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            for (Channel c : probe(ctx, failed, RETRY_PARALLEL, result)) result.dead.add(c.streamUrl);
        }
        return result;
    }

    /** Records working channels in result.alive and returns the ones with no working link. */
    private static List<Channel> probe(Context ctx, List<Channel> channels, int parallel, Result result) {
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        List<Future<String>> futures = new ArrayList<>();
        for (Channel c : channels) futures.add(pool.submit(() -> firstAlive(ctx, c)));
        List<Channel> failed = new ArrayList<>();
        for (int i = 0; i < channels.size(); i++) {
            String url = null;
            try {
                url = futures.get(i).get();
            } catch (Exception ignored) {
                // Same as a link that didn't answer.
            }
            if (url == null) failed.add(channels.get(i));
            else result.alive.put(channels.get(i).streamUrl, url);
        }
        pool.shutdown();
        return failed;
    }

    /** Tries the link that last played first, then the rest in order. */
    private static String firstAlive(Context ctx, Channel channel) {
        List<String> urls = channel.allUrls();
        String working = PlaylistStore.getWorkingUrl(ctx, channel.streamUrl);
        if (working != null) {
            urls.remove(working);
            urls.add(0, working);
        }
        for (String url : urls) {
            if (isAlive(url)) return url;
        }
        return null;
    }

    /** A link is alive when it answers 200 with an HLS playlist. */
    static boolean isAlive(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            if (conn.getResponseCode() != 200) return false;
            byte[] buf = new byte[2048];
            int total = 0;
            try (InputStream in = conn.getInputStream()) {
                int n;
                while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) total += n;
            }
            return new String(buf, 0, total, StandardCharsets.UTF_8).contains("#EXTM3U");
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
