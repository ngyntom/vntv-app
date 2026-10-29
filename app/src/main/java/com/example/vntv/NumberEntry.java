package com.example.vntv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

/** Collects remote number-key presses (up to 3 digits) into a channel number, TV-style. */
public class NumberEntry {
    public interface OnNumber {
        void onNumber(int number);
    }

    private static final long COMMIT_DELAY_MS = 1500;
    private static final long MESSAGE_MS = 2500;
    private static final int MAX_DIGITS = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView display;
    private final OnNumber callback;
    private final StringBuilder digits = new StringBuilder();
    private final Runnable commit = this::commit;
    private final Runnable hide;

    public NumberEntry(TextView display, OnNumber callback) {
        this.display = display;
        this.callback = callback;
        this.hide = () -> display.setVisibility(View.GONE);
    }

    /** Returns true when keyCode was a digit and has been taken. */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int digit = digitFor(keyCode);
        if (digit < 0) return false;
        if (event.getRepeatCount() > 0) return true;
        if (digits.length() >= MAX_DIGITS) digits.setLength(0);
        digits.append(digit);
        handler.removeCallbacks(hide);
        handler.removeCallbacks(commit);
        display.setText(digits);
        display.setVisibility(View.VISIBLE);
        handler.postDelayed(commit, digits.length() == MAX_DIGITS ? 300 : COMMIT_DELAY_MS);
        return true;
    }

    /** Briefly shows a message (e.g. the channel just tuned) in the same spot. */
    public void show(String text) {
        handler.removeCallbacks(hide);
        display.setText(text);
        display.setVisibility(View.VISIBLE);
        handler.postDelayed(hide, MESSAGE_MS);
    }

    public void cancel() {
        handler.removeCallbacksAndMessages(null);
        digits.setLength(0);
        display.setVisibility(View.GONE);
    }

    private void commit() {
        String typed = digits.toString();
        digits.setLength(0);
        if (typed.isEmpty()) return;
        callback.onNumber(Integer.parseInt(typed));
    }

    private static int digitFor(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) return keyCode - KeyEvent.KEYCODE_0;
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }
}
