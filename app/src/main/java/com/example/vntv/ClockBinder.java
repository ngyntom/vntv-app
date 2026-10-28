package com.example.vntv;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Keeps a time (and optional date) label current, ticking on each minute boundary. */
public class ClockBinder {
    private static final Locale VI = new Locale("vi", "VN");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView timeView;
    private final TextView dateView;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", VI);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd/MM", VI);
    private final Runnable tick = this::tick;

    public ClockBinder(TextView timeView, TextView dateView) {
        this.timeView = timeView;
        this.dateView = dateView;
    }

    public void start() {
        handler.removeCallbacks(tick);
        tick();
    }

    public void stop() {
        handler.removeCallbacks(tick);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        timeView.setText(timeFormat.format(date));
        if (dateView != null) dateView.setText(dateFormat.format(date));
        handler.postDelayed(tick, 60_000 - (now % 60_000) + 50);
    }
}
