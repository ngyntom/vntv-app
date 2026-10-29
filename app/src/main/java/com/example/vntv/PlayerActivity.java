package com.example.vntv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_INDEX = "index";

    private ExoPlayer player;
    private PlayerView playerView;
    private View overlayPanel;
    private TextView clockView;
    private TextView overlayCount;
    private NumberEntry numberEntry;
    private ClockBinder clock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sampleClockBackground = this::sampleClockBackground;
    private Bitmap sampleBitmap;
    private boolean clockOnLight = false;
    private String channelKey;
    private List<String> urls = new ArrayList<>();
    private int urlIndex;
    private int triesLeft;
    private RecyclerView channelListOverlay;
    private ChannelAdapter overlayAdapter;
    private List<Channel> channels;
    private int currentIndex;
    private boolean overlayVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        overlayPanel = findViewById(R.id.overlay_panel);
        channelListOverlay = findViewById(R.id.channel_list_overlay);
        overlayCount = findViewById(R.id.overlay_count);
        numberEntry = new NumberEntry(findViewById(R.id.number_input), this::tuneToNumber);
        clockView = findViewById(R.id.playing_time);
        clock = new ClockBinder(clockView, null);

        channels = ChannelHolder.current;
        currentIndex = getIntent().getIntExtra(EXTRA_INDEX, -1);

        channelListOverlay.setLayoutManager(new LinearLayoutManager(this));
        overlayAdapter = new ChannelAdapter(channels, this::switchTo, R.layout.item_channel_row, 1f);
        channelListOverlay.setAdapter(overlayAdapter);

        // Live channel streams: no seek bar, no next/prev transport buttons. Our own
        // overlay (OK button) and Channel Up/Down handle switching instead.
        playerView.setUseController(false);
    }

    // ExoPlayer's own guidance for API 24+: create/bind the player in onStart and tear it down
    // in onStop, rather than tying it to onCreate/onDestroy. Keeping them asymmetric left a
    // stale, released player around if the activity was ever stopped and restarted without
    // being destroyed (e.g. a transient system-driven pause right after the TV resumed it) -
    // any key press after that called methods on a dead player instead of a fresh one.
    @Override
    protected void onStart() {
        super.onStart();
        PlaylistStore.setOnMenu(this, false);
        placeClock(PlaylistStore.getClockPosition(this));
        clock.start();
        handler.postDelayed(sampleClockBackground, 1500);
        if (player != null) return;
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (tryNextUrl()) return;
                Toast.makeText(PlayerActivity.this, R.string.playback_error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && channelKey != null && !urls.isEmpty()) {
                    PlaylistStore.setWorkingUrl(PlayerActivity.this, channelKey, urls.get(urlIndex));
                }
            }
        });

        if (currentIndex >= 0 && currentIndex < channels.size()) {
            play(channels.get(currentIndex));
        } else {
            // Instant-resume path: MainActivity handed us a URL straight from local storage,
            // with no channel list yet (it's still fetching in the background). Play it now;
            // pickUpChannelList() below swaps in the real list (and index) once it's ready.
            String url = getIntent().getStringExtra(EXTRA_URL);
            String name = getIntent().getStringExtra(EXTRA_NAME);
            if (url != null) {
                startUrls(url, Collections.singletonList(url));
                PlaylistStore.setLastPlayed(this, url, name);
            }
        }

        if (channels.isEmpty()) {
            ChannelHolder.setListener(this::pickUpChannelList);
        }
    }

    /** Called once MainActivity's background fetch finishes, if we started with no list yet. */
    private void pickUpChannelList(List<Channel> freshChannels) {
        channels = freshChannels;
        overlayAdapter.setChannels(channels);
        String playingUrl = getIntent().getStringExtra(EXTRA_URL);
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).hasUrl(playingUrl)) {
                currentIndex = i;
                break;
            }
        }
        ChannelHolder.setListener(null);
    }

    private void play(Channel channel) {
        if (player == null) return;
        startUrls(channel.streamUrl, channel.allUrls());
        PlaylistStore.setLastPlayed(this, channel.streamUrl, channel.name);
    }

    /** Plays the channel keyed by key, starting from whichever of its urls last worked. */
    private void startUrls(String key, List<String> channelUrls) {
        channelKey = key;
        urls = new ArrayList<>(channelUrls);
        String working = PlaylistStore.getWorkingUrl(this, key);
        if (working != null && !urls.contains(working)) urls.add(0, working);
        urlIndex = Math.max(0, urls.indexOf(working));
        triesLeft = urls.size() - 1;
        playCurrentUrl();
    }

    private boolean tryNextUrl() {
        if (player == null || triesLeft <= 0) return false;
        triesLeft--;
        urlIndex = (urlIndex + 1) % urls.size();
        playCurrentUrl();
        return true;
    }

    private void playCurrentUrl() {
        player.setMediaItem(MediaItem.fromUri(urls.get(urlIndex)));
        player.prepare();
        player.play();
    }

    private void switchTo(Channel channel) {
        int index = channels.indexOf(channel);
        if (index >= 0) currentIndex = index;
        play(channel);
        setOverlayVisible(false);
    }

    private void setOverlayVisible(boolean visible) {
        overlayVisible = visible;
        overlayPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        overlayCount.setText(getString(R.string.overlay_count, channels.size()));
        if (currentIndex >= 0 && currentIndex < channels.size()) {
            channelListOverlay.scrollToPosition(currentIndex);
            channelListOverlay.post(() -> {
                RecyclerView.ViewHolder vh = channelListOverlay.findViewHolderForAdapterPosition(currentIndex);
                if (vh != null) {
                    vh.itemView.requestFocus();
                } else {
                    channelListOverlay.requestFocus();
                }
            });
        } else {
            channelListOverlay.requestFocus();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (numberEntry.onKeyDown(keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    private void tuneToNumber(int number) {
        Channel channel = ChannelNumbers.byNumber(number);
        if (channel == null) {
            numberEntry.show(getString(R.string.no_channel_number, number));
            return;
        }
        int index = channels.indexOf(channel);
        // channels holds only live channels; one missing from a loaded list was found dead.
        if (index < 0 && !channels.isEmpty()) {
            numberEntry.show(getString(R.string.channel_offline, number));
            return;
        }
        if (index >= 0) currentIndex = index;
        play(channel);
        setOverlayVisible(false);
        numberEntry.show(getString(R.string.channel_number_name, number, channel.name));
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!overlayVisible && !channels.isEmpty()) {
                setOverlayVisible(true);
                return true;
            }
        }
        if (!overlayVisible && !channels.isEmpty() && currentIndex >= 0) {
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                stepChannel(1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                stepChannel(-1);
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    private void stepChannel(int delta) {
        int size = channels.size();
        currentIndex = ((currentIndex + delta) % size + size) % size;
        play(channels.get(currentIndex));
    }

    @Override
    public void onBackPressed() {
        if (overlayVisible) {
            setOverlayVisible(false);
            return;
        }
        // Mark this *before* navigating: if MainActivity ends up having to be recreated from
        // scratch (see the comment below), its onCreate() checks this to decide whether to
        // auto-resume - without it, a fresh MainActivity would see the still-set "last played"
        // URL and immediately relaunch the player, turning this Back press into a no-op loop.
        PlaylistStore.setOnMenu(this, true);
        // Explicitly navigate to MainActivity instead of trusting finish() to reveal whatever
        // the OS thinks is beneath us: on this TV, finishing here sometimes dropped straight to
        // the launcher's Home even with MainActivity still alive in the same task underneath
        // (confirmed via dumpsys - a quirk of this firmware's task/stack handling, not a back
        // stack that was actually empty). CLEAR_TOP+SINGLE_TOP reuses that existing instance
        // rather than recreating it, so its scroll position/selection survive.
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void placeClock(int position) {
        boolean top = position == PlaylistStore.CLOCK_TOP_LEFT || position == PlaylistStore.CLOCK_TOP_RIGHT;
        boolean left = position == PlaylistStore.CLOCK_TOP_LEFT || position == PlaylistStore.CLOCK_BOTTOM_LEFT;
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) clockView.getLayoutParams();
        lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | (left ? Gravity.START : Gravity.END);
        int vertical = Math.round((top ? 24 : 28) * dp);
        int horizontal = Math.round(36 * dp);
        lp.setMargins(left ? horizontal : 0, top ? vertical : 0, left ? 0 : horizontal, top ? 0 : vertical);
        lp.setMarginStart(left ? horizontal : 0);
        lp.setMarginEnd(left ? 0 : horizontal);
        clockView.setLayoutParams(lp);
    }

    /** Samples the video under the clock and flips the clock to dark text over bright frames. */
    private void sampleClockBackground() {
        handler.postDelayed(sampleClockBackground, 3000);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || player == null || !player.isPlaying()) return;
        View surface = playerView.getVideoSurfaceView();
        if (!(surface instanceof SurfaceView)) return;
        SurfaceView sv = (SurfaceView) surface;
        if (sv.getWidth() == 0 || sv.getHeight() == 0 || !sv.getHolder().getSurface().isValid()) return;

        int[] svLoc = new int[2];
        int[] clockLoc = new int[2];
        sv.getLocationOnScreen(svLoc);
        clockView.getLocationOnScreen(clockLoc);
        float left = (clockLoc[0] - svLoc[0]) / (float) sv.getWidth();
        float top = (clockLoc[1] - svLoc[1]) / (float) sv.getHeight();
        float right = left + clockView.getWidth() / (float) sv.getWidth();
        float bottom = top + clockView.getHeight() / (float) sv.getHeight();
        if (right <= 0 || bottom <= 0 || left >= 1 || top >= 1) {
            // Clock sits over the black letterbox bars, not the picture.
            applyClockStyle(false);
            return;
        }
        final float l = Math.max(0, left), t = Math.max(0, top);
        final float r = Math.min(1, right), b = Math.min(1, bottom);

        if (sampleBitmap == null) sampleBitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        PixelCopy.request(sv, sampleBitmap, result -> {
            if (result != PixelCopy.SUCCESS) return;
            double lum = averageLuminance(sampleBitmap, l, t, r, b);
            // Separate on/off thresholds so a frame hovering near the edge doesn't flicker.
            boolean onLight = clockOnLight ? lum > 0.5 : lum > 0.62;
            applyClockStyle(onLight);
        }, handler);
    }

    private static double averageLuminance(Bitmap bmp, float l, float t, float r, float b) {
        int x0 = (int) (l * bmp.getWidth()), x1 = Math.max(x0 + 1, (int) Math.ceil(r * bmp.getWidth()));
        int y0 = (int) (t * bmp.getHeight()), y1 = Math.max(y0 + 1, (int) Math.ceil(b * bmp.getHeight()));
        x1 = Math.min(x1, bmp.getWidth());
        y1 = Math.min(y1, bmp.getHeight());
        double sum = 0;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int c = bmp.getPixel(x, y);
                sum += (0.2126 * ((c >> 16) & 0xff) + 0.7152 * ((c >> 8) & 0xff) + 0.0722 * (c & 0xff)) / 255.0;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    private void applyClockStyle(boolean onLight) {
        if (onLight == clockOnLight) return;
        clockOnLight = onLight;
        clockView.setTextColor(onLight ? 0xFF111418 : 0xFFFFFFFF);
        clockView.setShadowLayer(8, 0, 1, onLight ? 0x99FFFFFF : 0xB3000000);
    }

    @Override
    protected void onStop() {
        super.onStop();
        clock.stop();
        numberEntry.cancel();
        handler.removeCallbacks(sampleClockBackground);
        ChannelHolder.setListener(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
