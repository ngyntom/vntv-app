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
    private static final String CAT_OTHER = "Tổng hợp";
    private static final List<String> CATEGORIES = Arrays.asList(
            CAT_FAVORITES, CAT_OTHER, CAT_SCTV, CAT_THVL, CAT_HTV, CAT_VTV,
            CategoryAdapter.ACTION_SETTINGS);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ChannelAdapter adapter;
    private RecyclerView channelGrid;
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
        channelGrid = findViewById(R.id.channel_grid);
        categoryTabs = findViewById(R.id.category_tabs);
        emptyView = findViewById(R.id.empty_view);
        progressBar = findViewById(R.id.progress_bar);
        channelGrid.setLayoutManager(new GridLayoutManager(this, 4));
        categoryTabs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        adapter = new ChannelAdapter(new ArrayList<>(), this::openChannel);
        adapter.setOnChannelLongClickListener(this::toggleFavorite);
        channelGrid.setAdapter(adapter);

        // Default landing tab: first content category after Favorites (usually empty on first run).
        categoryAdapter = new CategoryAdapter(CATEGORIES, 1, this::onCategorySelected);
        categoryTabs.setAdapter(categoryAdapter);

        // Only on a genuine fresh launch - not when Android is recreating this Activity to
        // restore a back stack whose process got killed (e.g. this TV reclaiming RAM while
        // PlayerActivity was in front). That recreation also passes through onCreate(), and
        // auto-resuming there would hijack the user's own Back press into bouncing straight
        // back into playback instead of landing on the channel grid.
        if (savedInstanceState == null) {
            instantResumeIfPossible();
        }
        loadChannels();
    }

    /** Resuming the last-played channel shouldn't wait on a full playlist re-fetch over the
     *  network - the URL and name are already on disk, so jump straight into playback with
     *  those; loadChannels() still runs right after to fill in the real list for Up/Down and
     *  the in-player overlay, picked up live by ChannelHolder once it's ready. */
    private void instantResumeIfPossible() {
        // If the user had already backed out to this grid before leaving the app, that's
        // where they should land again - not back in the channel they'd stepped away from.
        if (PlaylistStore.wasOnMenu(this)) return;
        String lastUrl = PlaylistStore.getLastPlayed(this);
        if (lastUrl == null) return;
        autoLaunched = true;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, lastUrl);
        intent.putExtra(PlayerActivity.EXTRA_NAME, PlaylistStore.getLastPlayedName(this));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allChannels.isEmpty()) {
            loadChannels();
        } else {
            syncSidebarToLastPlayed();
        }
    }

    private void onCategorySelected(String category) {
        if (CategoryAdapter.ACTION_SETTINGS.equals(category)) {
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        filterByCategory(category);
    }

    /** Back from the player (or just resuming): point the sidebar at whatever was last playing,
     *  so Up/Down works immediately instead of landing on an unrelated, unfocused category. */
    private void syncSidebarToLastPlayed() {
        PlaylistStore.setOnMenu(this, true);
        String lastUrl = PlaylistStore.getLastPlayed(this);
        if (lastUrl == null) return;
        for (Channel c : allChannels) {
            if (!lastUrl.equals(c.streamUrl)) continue;
            String category = classify(c);
            int index = categoryAdapter.indexOf(category);
            if (index < 0) return;
            categoryAdapter.setSelectedPosition(index);
            filterByCategory(category);
            categoryTabs.scrollToPosition(index);
            categoryTabs.post(() -> {
                RecyclerView.ViewHolder vh = categoryTabs.findViewHolderForAdapterPosition(index);
                if (vh != null) vh.itemView.requestFocus();
            });
            return;
        }
    }

    private void openChannel(Channel channel) {
        // The in-player overlay and Channel Up/Down should reach every channel, not just the
        // group the grid happened to be filtered to - ordered with the channel's own group
        // first, then the other groups, so Up/Down still feels locally sorted.
        List<Channel> playbackOrder = buildPlaybackOrder(channel);
        int index = playbackOrder.indexOf(channel);
        ChannelHolder.current = playbackOrder;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_URL, channel.streamUrl);
        intent.putExtra(PlayerActivity.EXTRA_NAME, channel.name);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index);
        startActivity(intent);
    }

    private List<Channel> buildPlaybackOrder(Channel playing) {
        String ownCategory = classify(playing);
        List<String> order = new ArrayList<>();
        order.add(ownCategory);
        for (String cat : CATEGORIES) {
            if (CAT_FAVORITES.equals(cat) || CategoryAdapter.ACTION_SETTINGS.equals(cat)) continue;
            if (!cat.equals(ownCategory)) order.add(cat);
        }
        List<Channel> result = new ArrayList<>();
        for (String cat : order) {
            for (Channel c : allChannels) {
                if (cat.equals(classify(c))) result.add(c);
            }
        }
        return result;
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
                if (!finishInstantResume(all) && !maybeAutoResume(all)) {
                    PlaylistStore.setOnMenu(this, true);
                    filterByCategory(currentCategory());
                }
            });
        });
    }

    /** If instantResumeIfPossible() already started playback, this just hands PlayerActivity
     *  the real, ordered channel list (via the listener it registered) now that it's in -
     *  no second launch needed. */
    private boolean finishInstantResume(List<Channel> all) {
        String lastUrl = PlaylistStore.getLastPlayed(this);
        if (lastUrl == null || !autoLaunched) return false;
        for (Channel c : all) {
            if (lastUrl.equals(c.streamUrl)) {
                ChannelHolder.update(buildPlaybackOrder(c));
                return true;
            }
        }
        return false;
    }

    /** First run with no saved "last played" yet: falls back to the first favorite. */
    private boolean maybeAutoResume(List<Channel> all) {
        if (autoLaunched) return false;
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
        if (upper.contains("THVL") || upper.contains("THTV")
                || upper.contains("VINH LONG") || upper.contains("TRA VINH")) return CAT_THVL;
        return CAT_OTHER;
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
        // Deferred: this can be reached from a RecyclerView's own onFocusChangeListener while
        // that RecyclerView (or another one on the same screen) is still mid-layout - e.g. right
        // after MainActivity is freshly created and focus lands on a sidebar row as part of that
        // very layout pass. Calling notifyDataSetChanged() synchronously in that window throws
        // "Cannot call this method while RecyclerView is computing a layout or scrolling", which
        // crashed the whole app (and from the outside looked like Back dropping to Home - the
        // crash killed MainActivity before it could ever show). Posting it clears that window.
        mainHandler.post(() -> adapter.setChannels(displayedChannels));
        boolean showEmpty = filtered.isEmpty() && !allChannels.isEmpty();
        if (showEmpty) {
            emptyView.setText(CAT_FAVORITES.equals(category) ? R.string.no_favorites : R.string.no_channels);
        }
        emptyView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
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
