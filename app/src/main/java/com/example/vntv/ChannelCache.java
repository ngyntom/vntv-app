package com.example.vntv;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A local copy of the last successfully fetched playlist, so the grid (and the instant-resume
 * channel lookup) can show up right away on launch instead of waiting on a network fetch and
 * re-parse of the whole M3U every single time. The network fetch still always runs afterward,
 * in the background, to keep this cache fresh for next time.
 */
public class ChannelCache {
    private static final String TAG = "VnTv";
    private static final String FILE_NAME = "channels_cache.json";

    public static void save(Context ctx, List<Channel> channels) {
        try {
            JSONArray arr = new JSONArray();
            for (Channel c : channels) {
                JSONObject o = new JSONObject();
                o.put("name", c.name);
                o.put("quality", c.quality == null ? JSONObject.NULL : c.quality);
                o.put("logoUrl", c.logoUrl == null ? JSONObject.NULL : c.logoUrl);
                o.put("streamUrl", c.streamUrl);
                o.put("groupTitle", c.groupTitle == null ? JSONObject.NULL : c.groupTitle);
                o.put("sourceUrl", c.sourceUrl == null ? JSONObject.NULL : c.sourceUrl);
                arr.put(o);
            }
            try (FileOutputStream out = ctx.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (JSONException | IOException e) {
            Log.w(TAG, "failed to save channel cache", e);
        }
    }

    public static List<Channel> load(Context ctx) {
        List<Channel> result = new ArrayList<>();
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (!f.exists()) return result;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new Channel(
                        o.getString("name"),
                        o.isNull("quality") ? null : o.getString("quality"),
                        o.isNull("logoUrl") ? null : o.getString("logoUrl"),
                        o.getString("streamUrl"),
                        o.isNull("groupTitle") ? null : o.getString("groupTitle"),
                        o.isNull("sourceUrl") ? null : o.getString("sourceUrl")
                ));
            }
        } catch (JSONException | IOException e) {
            Log.w(TAG, "failed to load channel cache", e);
        }
        return result;
    }
}
