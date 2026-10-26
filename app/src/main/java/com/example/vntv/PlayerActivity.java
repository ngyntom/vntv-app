package com.example.vntv;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
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

import java.util.List;

public class PlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_INDEX = "index";

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView nowPlayingName;
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
        nowPlayingName = findViewById(R.id.now_playing_name);
        channelListOverlay = findViewById(R.id.channel_list_overlay);

        channels = ChannelHolder.current;
        currentIndex = getIntent().getIntExtra(EXTRA_INDEX, -1);

        channelListOverlay.setLayoutManager(new LinearLayoutManager(this));
        overlayAdapter = new ChannelAdapter(channels, this::switchTo);
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
        if (player != null) return;
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this, R.string.playback_error, Toast.LENGTH_LONG).show();
            }
        });

        if (currentIndex >= 0 && currentIndex < channels.size()) {
            play(channels.get(currentIndex));
        } else {
            String url = getIntent().getStringExtra(EXTRA_URL);
            String name = getIntent().getStringExtra(EXTRA_NAME);
            if (url != null) {
                nowPlayingName.setText(name);
                player.setMediaItem(MediaItem.fromUri(url));
                player.prepare();
                player.play();
                PlaylistStore.setLastPlayed(this, url);
            }
        }
    }

    private void play(Channel channel) {
        if (player == null) return;
        nowPlayingName.setText(channel.name);
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl));
        player.prepare();
        player.play();
        PlaylistStore.setLastPlayed(this, channel.streamUrl);
    }

    private void switchTo(Channel channel) {
        int index = channels.indexOf(channel);
        if (index >= 0) currentIndex = index;
        play(channel);
        setOverlayVisible(false);
    }

    private void setOverlayVisible(boolean visible) {
        overlayVisible = visible;
        channelListOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) channelListOverlay.requestFocus();
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
        if (isTaskRoot()) {
            // Nothing beneath us in the stack (e.g. the TV's own launcher jumped straight into
            // playback) - go to the channel picker instead of falling through to Home.
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
