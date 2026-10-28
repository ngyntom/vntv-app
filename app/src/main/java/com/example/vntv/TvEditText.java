package com.example.vntv;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * A text field that behaves on a TV remote: the keyboard only opens on OK, and until then the
 * arrow keys move focus away. Gboard for TV keeps its input view "shown" even while hidden and
 * eats the arrow keys before a plain EditText ever sees them, trapping focus in the field.
 */
public class TvEditText extends AppCompatEditText {
    private boolean keyboardOpen;

    public TvEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setShowSoftInputOnFocus(false);
    }

    public void openKeyboard() {
        keyboardOpen = true;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(this, 0);
    }

    public void closeKeyboard() {
        keyboardOpen = false;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            keyboardOpen = false;
        }
        if (!keyboardOpen) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) performClick();
                return true;
            }
            int direction = directionFor(keyCode);
            if (direction != 0) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    View next = focusSearch(direction);
                    if (next != null) next.requestFocus(direction);
                }
                return true;
            }
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (!focused) keyboardOpen = false;
    }

    private static int directionFor(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return 0;
        }
    }
}
