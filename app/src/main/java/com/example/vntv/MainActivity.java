package com.example.vntv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "VnTv";
    private static final String CAT_FAVORITES = "Yêu thích";
    private static final String CAT_VTV = "VTV";
    private static final String CAT_HTV = "HTV";
    private static final String CAT_SCTV = "SCTV";
    private static final String CAT_THVL = "THVL";
    private static final String CAT_LOCAL = "Địa phương";
    private static final String CAT_FOREIGN = "Nước ngoài";
    private static final List<String> CATEGORIES = Arrays.asList(
            CAT_FAVORITES, CAT_VTV, CAT_HTV, CAT_SCTV, CAT_THVL, CAT_LOCAL, CAT_FOREIGN);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ChannelAdapter adapter;
    private RecyclerView categoryTabs;
    private CategoryAdapter categoryAdapter;
    private TextView emptyView;
    private ProgressBar progressBar;
    private List<Channel> allChannels = new ArrayList<>();
    private List<Channel> displayedChannels = new ArrayList<>();
    private boolean autoLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RecyclerView recyclerView = findViewById(R.id.channel_grid);
        categoryTabs = findViewById(R.id.category_tabs);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        categoryTabs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new ChannelAdapter(new ArrayList<>(), this::openChannel);
        adapter.setOnChannelLongClickListener(this::toggleFavorite);
        recyclerView.setAdapter(adapter);

        // VTV comes up first by default (after Favorites, which is usually empty on first run).
        categoryAdapter = new CategoryAdapter(CATEGORIES, 1, this::filterByCategory);
        categoryTabs.setAdapter(categoryAdapter);

        loadChannels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allChannels.isEmpty()) loadChannels();
    }

    private void openChannel(Channel channel) {
        int index = displayedChannels.indexOf(channel);
        ChannelHolder.current = displayedChannels;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, channel.streamUrl);
        intent.putExtra(PlayerActivity.EXTRA_NAME, channel.name);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }

    private void toggleFavorite(Channel channel) {
        boolean nowFavorite = PlaylistStore.toggleFavorite(this, channel.streamUrl);
        Toast.makeText(this, nowFavorite
                ? getString(R.string.added_favorite, channel.name)
                : getString(R.string.removed_favorite, channel.name), Toast.LENGTH_SHORT).show();
        if (CAT_FAVORITES.equals(currentCategory())) {
            filterByCategory(CAT_FAVORITES);
        }
    }

    private String currentCategory() {
        return categoryAdapter != null ? categoryAdapter.getSelectedCategory() : CAT_VTV;
    }

    private void loadChannels() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        executor.execute(() -> {
            List<Channel> all = new ArrayList<>();
            for (String url : PlaylistStore.getUrls(this)) {
                try {
                    all.addAll(fetchPlaylist(url));
                } catch (Exception e) {
                    Log.w(TAG, "failed to load " + url, e);
                }
            }
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);
                allChannels = all;
                emptyView.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
                if (all.isEmpty()) {
                    Toast.makeText(this, R.string.load_failed, Toast.LENGTH_LONG).show();
                }
                if (!maybeAutoResume(all)) {
                    filterByCategory(currentCategory());
                }
            });
        });
    }

    /** Resumes the channel that was last playing; falls back to the first favorite. */
    private boolean maybeAutoResume(List<Channel> all) {
        if (autoLaunched) return false;
        String lastUrl = PlaylistStore.getLastPlayed(this);
        if (lastUrl != null && launchIfPresent(all, lastUrl)) return true;
        List<String> favorites = PlaylistStore.getFavorites(this);
        if (!favorites.isEmpty() && launchIfPresent(all, favorites.get(0))) return true;
        return false;
    }

    private boolean launchIfPresent(List<Channel> all, String streamUrl) {
        for (Channel c : all) {
            if (streamUrl.equals(c.streamUrl)) {
                autoLaunched = true;
                displayedChannels = all;
                openChannel(c);
                return true;
            }
        }
        return false;
    }

    private String classify(Channel c) {
        String upper = c.name.toUpperCase();
        if (upper.contains("VTV")) return CAT_VTV;
        if (upper.contains("HTV")) return CAT_HTV;
        if (upper.contains("SCTV")) return CAT_SCTV;
        if (upper.contains("THVL") || upper.contains("THTV")) return CAT_THVL;
        if (PlaylistStore.DEFAULT_VN_URL.equals(c.sourceUrl)) return CAT_LOCAL;
        return CAT_FOREIGN;
    }

    private void filterByCategory(String category) {
        List<Channel> filtered = new ArrayList<>();
        if (CAT_FAVORITES.equals(category)) {
            List<String> favUrls = PlaylistStore.getFavorites(this);
            for (String url : favUrls) {
                for (Channel c : allChannels) {
                    if (url.equals(c.streamUrl)) {
                        filtered.add(c);
                        break;
                    }
                }
            }
        } else {
            for (Channel c : allChannels) {
                if (category.equals(classify(c))) filtered.add(c);
            }
        }
        displayedChannels = filtered;
        adapter.setChannels(displayedChannels);
        emptyView.setVisibility(filtered.isEmpty() && !allChannels.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<Channel> fetchPlaylist(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);
        try (InputStream in = conn.getInputStream()) {
            return M3uParser.parse(in, urlStr);
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
