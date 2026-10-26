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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "VnTv";
    private static final String ALL_CATEGORY = "Tất cả";
    private static final String OTHER_CATEGORY = "Khác";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ChannelAdapter adapter;
    private RecyclerView categoryTabs;
    private TextView emptyView;
    private ProgressBar progressBar;
    private List<Channel> allChannels = new ArrayList<>();
    private List<Channel> displayedChannels = new ArrayList<>();
    private boolean autoLaunchedDefault = false;

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
        adapter.setOnChannelLongClickListener(this::setAsDefaultChannel);
        recyclerView.setAdapter(adapter);
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

    private void setAsDefaultChannel(Channel channel) {
        PlaylistStore.setDefaultChannel(this, channel);
        Toast.makeText(this, getString(R.string.default_channel_set, channel.name), Toast.LENGTH_SHORT).show();
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
                displayedChannels = all;
                setupCategoryTabs(all);
                adapter.setChannels(all);
                emptyView.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
                if (all.isEmpty()) {
                    Toast.makeText(this, R.string.load_failed, Toast.LENGTH_LONG).show();
                }
                maybeAutoLaunchDefault(all);
            });
        });
    }

    private void maybeAutoLaunchDefault(List<Channel> all) {
        if (autoLaunchedDefault) return;
        String defaultUrl = PlaylistStore.getDefaultChannelUrl(this);
        if (defaultUrl == null) return;
        for (Channel c : all) {
            if (defaultUrl.equals(c.streamUrl)) {
                autoLaunchedDefault = true;
                openChannel(c);
                break;
            }
        }
    }

    private void setupCategoryTabs(List<Channel> channels) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add(ALL_CATEGORY);
        for (Channel c : channels) {
            String group = c.groupTitle;
            categories.add(group == null || group.trim().isEmpty() ? OTHER_CATEGORY : group.trim());
        }
        List<String> categoryList = new ArrayList<>(categories);
        CategoryAdapter categoryAdapter = new CategoryAdapter(categoryList, this::filterByCategory);
        categoryTabs.setAdapter(categoryAdapter);
    }

    private void filterByCategory(String category) {
        if (ALL_CATEGORY.equals(category)) {
            displayedChannels = allChannels;
            adapter.setChannels(displayedChannels);
            return;
        }
        List<Channel> filtered = new ArrayList<>();
        for (Channel c : allChannels) {
            String group = c.groupTitle;
            String bucket = group == null || group.trim().isEmpty() ? OTHER_CATEGORY : group.trim();
            if (bucket.equals(category)) filtered.add(c);
        }
        displayedChannels = filtered;
        adapter.setChannels(displayedChannels);
    }

    private List<Channel> fetchPlaylist(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setInstanceFollowRedirects(true);
        try (InputStream in = conn.getInputStream()) {
            return M3uParser.parse(in);
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
