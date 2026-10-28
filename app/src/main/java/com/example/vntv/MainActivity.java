package com.example.vntv;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
    private boolean loading = false;
    private long lastNavKeyAt = 0;
    private boolean pendingHotkey = false;
    private static boolean instantResumeDone = false;

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
        // Focus navigation runs right after the key is dispatched, so a short window is enough
        // to tell a user's D-pad move apart from focus the system hands out on its own.
        categoryAdapter.setFocusSelectGate(() -> SystemClock.uptimeMillis() - lastNavKeyAt < 500);
        categoryTabs.setAdapter(categoryAdapter);

        // Only on a genuine fresh launch - not when Android is recreating this Activity to
        // restore a back stack whose process got killed (e.g. this TV reclaiming RAM while
        // PlayerActivity was in front). That recreation also passes through onCreate(), and
        // auto-resuming there would hijack the user's own Back press into bouncing straight
        // back into playback instead of landing on the channel grid.
        if (savedInstanceState == null) {
            if (isHotkeyIntent(getIntent())) {
                pendingHotkey = true;
            } else if (!instantResumeDone) {
                instantResumeIfPossible();
            }
        }
        // Once per process: as the box's Home, pressing Home mid-playback creates a fresh
        // instance here, and that should land on the grid rather than replay the channel.
        instantResumeDone = true;
        loadChannels();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (isHotkeyIntent(intent)) {
            pendingHotkey = true;
            if (!allChannels.isEmpty()) consumeHotkey();
        }
    }

    private static boolean isHotkeyIntent(Intent intent) {
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null && "nflx".equals(intent.getData().getScheme());
    }

    /** Opens the channel assigned to the Netflix key (falls back to the last-played one). */
    private boolean consumeHotkey() {
        pendingHotkey = false;
        String url = PlaylistStore.getHotkeyUrl(this);
        if (url == null) url = PlaylistStore.getLastPlayed(this);
        if (url == null) return false;
        for (Channel c : allChannels) {
            if (url.equals(c.streamUrl)) {
                autoLaunched = true;
                openChannel(c);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        // As the box's Home screen there's nothing behind this to go back to.
        if (isDefaultHome()) return;
        super.onBackPressed();
    }

    private boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null
                && getPackageName().equals(info.activityInfo.packageName);
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
        selectCategoryForUrl(PlaylistStore.getLastPlayed(this));
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
        // onCreate() and the onResume() that immediately follows it both call this when
        // allChannels is still empty (the first fetch hasn't posted its result back yet) -
        // without this guard a fresh launch fired it twice, racing two fetches against
        // each other and against the sidebar sync each one does once it completes.
        if (loading) return;
        loading = true;
        emptyView.setVisibility(View.GONE);
        executor.execute(() -> {
            // Cached copy from last time, read off disk - near-instant, so the grid (or an
            // instant-resume lookup) doesn't have to sit on the network fetch below just to
            // show something. It's posted on its own, separately from the fetch, so the UI
            // updates the moment this is ready instead of waiting for both.
            List<Channel> cached = ChannelCache.load(this);
            if (!cached.isEmpty()) {
                mainHandler.post(() -> applyLoadedChannels(cached, false));
            } else {
                mainHandler.post(() -> progressBar.setVisibility(View.VISIBLE));
            }

            List<Channel> all = new ArrayList<>();
            for (String url : PlaylistStore.getUrls(this)) {
                try {
                    all.addAll(fetchPlaylist(url));
                } catch (Exception e) {
                    Log.w(TAG, "failed to load " + url, e);
                }
            }
            if (!all.isEmpty()) {
                ChannelCache.save(this, all);
            }
            mainHandler.post(() -> {
                loading = false;
                progressBar.setVisibility(View.GONE);
                if (!all.isEmpty()) {
                    applyLoadedChannels(all, true);
                } else if (allChannels.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.load_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /** Wires up a freshly available channel list, whether it came from the on-disk cache or the
     *  network fetch: tries an instant-resume launch first, otherwise just refreshes the grid.
     *  isNetwork is only used to decide whether to mark "on the menu" - a cache-triggered pass
     *  that found nothing to auto-launch still leaves that decision to the network pass that
     *  follows right after, so a stale cache can't flip it incorrectly if the network list (or
     *  the launch it triggers) hasn't resolved yet. */
    private void applyLoadedChannels(List<Channel> channels, boolean isNetwork) {
        allChannels = channels;
        if (pendingHotkey && consumeHotkey()) return;
        boolean launched = finishInstantResume(channels) || maybeAutoResume(channels);
        if (launched) return;
        if (isNetwork && !autoLaunched) {
            PlaylistStore.setOnMenu(this, true);
        }
        // A brand new MainActivity instance (e.g. the app was fully backed out of and reopened)
        // starts with the sidebar's "selected" row hardcoded to Tổng hợp, but nothing has ever
        // asked for D-pad focus yet - Android lands it on whatever row is first in traversal
        // order instead, so the bright "focused" highlight and the "selected" highlight end up
        // on two different rows. Point both at the same place: the last-played channel's
        // category when there is one, otherwise whatever's already marked selected.
        if (!selectCategoryForUrl(PlaylistStore.getLastPlayed(this))) {
            filterByCategory(currentCategory());
            focusCurrentCategory();
        }
    }

    /** Finds the channel at url, filters the grid to its category, and puts D-pad focus
     *  directly on that channel's tile - so returning from playback (or reopening the app)
     *  lands right back where the user was, not just on the right sidebar category. Returns
     *  false (leaving the sidebar untouched) if the URL isn't in the list. */
    private boolean selectCategoryForUrl(String url) {
        if (url == null) return false;
        for (Channel c : allChannels) {
            if (url.equals(c.streamUrl)) {
                String category = classify(c);
                int catIndex = categoryAdapter.indexOf(category);
                if (catIndex < 0) return false;
                categoryAdapter.setSelectedPosition(catIndex);
                filterByCategory(category, () -> focusChannelInGrid(url));
                return true;
            }
        }
        return false;
    }

    private void focusChannelInGrid(String url) {
        int index = -1;
        for (int i = 0; i < displayedChannels.size(); i++) {
            if (url.equals(displayedChannels.get(i).streamUrl)) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        final int pos = index;
        channelGrid.scrollToPosition(pos);
        channelGrid.post(() -> {
            RecyclerView.ViewHolder vh = channelGrid.findViewHolderForAdapterPosition(pos);
            if (vh != null) vh.itemView.requestFocus();
        });
    }

    private void focusCurrentCategory() {
        int index = categoryAdapter.indexOf(currentCategory());
        if (index >= 0) focusCategoryRow(index);
    }

    private void focusCategoryRow(int index) {
        categoryTabs.scrollToPosition(index);
        categoryTabs.post(() -> {
            RecyclerView.ViewHolder vh = categoryTabs.findViewHolderForAdapterPosition(index);
            if (vh != null) vh.itemView.requestFocus();
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
        filterByCategory(category, null);
    }

    private void filterByCategory(String category, Runnable afterApplied) {
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
        mainHandler.post(() -> {
            adapter.setChannels(displayedChannels);
            if (afterApplied != null) afterApplied.run();
        });
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
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    lastNavKeyAt = SystemClock.uptimeMillis();
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
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
