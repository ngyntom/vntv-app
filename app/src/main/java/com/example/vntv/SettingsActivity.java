package com.example.vntv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TV-style settings: one vertical list of large rows (up/down to move, OK to act), each showing
 * its current value, with a side panel explaining whichever row has focus.
 */
public class SettingsActivity extends AppCompatActivity {
    private interface ValueSource {
        String get();
    }

    private static final class Row {
        final TextView value;
        final ValueSource source;

        Row(TextView value, ValueSource source) {
            this.value = value;
            this.source = source;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private ViewGroup list;
    private TextView detailTitle;
    private TextView detailText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        list = findViewById(R.id.settings_list);
        detailTitle = findViewById(R.id.detail_title);
        detailText = findViewById(R.id.detail_text);

        addHeader(R.string.section_list);
        View first = addRow(R.drawable.ic_source, R.string.sources_title, R.string.sources_desc,
                () -> getString(R.string.sources_value, PlaylistStore.getUrls(this).size()),
                this::showSources);
        addRow(R.drawable.ic_source, R.string.sort_title, R.string.sort_desc,
                () -> TextUtils.join(", ", Categories.load(this)),
                this::showSortDialog);

        addHeader(R.string.section_watching);
        addRow(R.drawable.ic_cat_tv, R.string.autoplay_title, R.string.autoplay_desc,
                () -> getString(PlaylistStore.getAutoPlay(this) ? R.string.autoplay_on : R.string.autoplay_off),
                () -> {
                    PlaylistStore.setAutoPlay(this, !PlaylistStore.getAutoPlay(this));
                    refreshValues();
                });
        addRow(R.drawable.ic_remote, R.string.hotkey_title, R.string.hotkey_desc,
                () -> {
                    String name = PlaylistStore.getHotkeyName(this);
                    return name != null ? name : getString(R.string.hotkey_unset);
                },
                this::chooseHotkeyChannel);
        addRow(R.drawable.ic_clock, R.string.clock_title, R.string.clock_desc,
                () -> getResources().getStringArray(R.array.clock_positions)[PlaylistStore.getClockPosition(this)],
                () -> {
                    PlaylistStore.setClockPosition(this, nextClockPosition(PlaylistStore.getClockPosition(this)));
                    refreshValues();
                });

        addHeader(R.string.section_device);
        addRow(R.drawable.ic_cat_local, R.string.home_title, R.string.home_desc,
                () -> getString(isHomeAliasEnabled() ? R.string.home_on : R.string.home_off),
                this::toggleHome);
        addRow(R.drawable.ic_settings, R.string.system_title, R.string.system_desc,
                () -> getString(R.string.system_value),
                () -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        first.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshValues();
    }

    private void addHeader(int textRes) {
        TextView header = (TextView) LayoutInflater.from(this).inflate(R.layout.item_setting_header, list, false);
        header.setText(textRes);
        list.addView(header);
    }

    private View addRow(int iconRes, int titleRes, int descRes, ValueSource value, Runnable action) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_setting_row, list, false);
        ((ImageView) row.findViewById(R.id.setting_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.setting_title)).setText(titleRes);
        TextView valueView = row.findViewById(R.id.setting_value);
        valueView.setText(value.get());
        rows.add(new Row(valueView, value));
        row.setOnClickListener(v -> action.run());
        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) return;
            detailTitle.setText(titleRes);
            detailText.setText(descRes);
        });
        list.addView(row);
        return row;
    }

    private void refreshValues() {
        for (Row row : rows) row.value.setText(row.source.get());
    }

    private void chooseHotkeyChannel() {
        List<Channel> channels = ChannelCache.load(this);
        if (channels.isEmpty()) {
            Toast.makeText(this, R.string.hotkey_no_channels, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[channels.size()];
        for (int i = 0; i < names.length; i++) names[i] = channels.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.hotkey_dialog_title)
                .setItems(names, (dialog, which) -> {
                    Channel c = channels.get(which);
                    PlaylistStore.setHotkeyChannel(this, c.streamUrl, c.name);
                    refreshValues();
                })
                .show();
    }

    /**
     * Remote-friendly reordering: OK picks a category up (row turns solid blue), Up/Down moves
     * it, OK puts it down. Rows stay put and only their labels swap, so focus never jumps while
     * a key is being handled. Every move is saved immediately, so Back at any point keeps it.
     */
    private void showSortDialog() {
        List<String> order = new ArrayList<>(Categories.load(this));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        List<View> rowViews = new ArrayList<>();
        int[] moving = {-1};

        for (int i = 0; i < order.size(); i++) {
            final int idx = i;
            View row = LayoutInflater.from(this).inflate(R.layout.item_sort_row, box, false);
            row.setOnClickListener(v -> {
                int previous = moving[0];
                moving[0] = previous == idx ? -1 : idx;
                if (previous >= 0 && previous != idx) bindSortRow(rowViews.get(previous), previous, order, moving[0]);
                bindSortRow(row, idx, order, moving[0]);
            });
            row.setOnKeyListener((v, keyCode, event) -> {
                if (moving[0] != idx || event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int target;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) target = idx - 1;
                else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) target = idx + 1;
                else return false;
                if (target < 0 || target >= order.size()) return true;
                Collections.swap(order, idx, target);
                Categories.save(this, order);
                moving[0] = target;
                bindSortRow(row, idx, order, moving[0]);
                View targetRow = rowViews.get(target);
                bindSortRow(targetRow, target, order, moving[0]);
                targetRow.requestFocus();
                return true;
            });
            rowViews.add(row);
            bindSortRow(row, idx, order, moving[0]);
            box.addView(row);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_title)
                .setView(scroll)
                .setPositiveButton(R.string.done, null)
                .setOnDismissListener(d -> refreshValues())
                .show();
        rowViews.get(0).requestFocus();
    }

    private void bindSortRow(View row, int index, List<String> order, int moving) {
        ((TextView) row.findViewById(R.id.sort_position)).setText(String.valueOf(index + 1));
        ((TextView) row.findViewById(R.id.sort_label)).setText(order.get(index));
        ((TextView) row.findViewById(R.id.sort_hint)).setText(index == moving ? getString(R.string.sort_moving) : "");
        row.setActivated(index == moving);
    }

    /** Lists the playlist URLs plus an "add" entry; picking a URL asks to remove it. */
    private void showSources() {
        List<String> urls = PlaylistStore.getUrls(this);
        String[] items = new String[urls.size() + 1];
        for (int i = 0; i < urls.size(); i++) items[i] = urls.get(i);
        items[urls.size()] = getString(R.string.sources_add);
        new AlertDialog.Builder(this)
                .setTitle(R.string.sources_title)
                .setItems(items, (dialog, which) -> {
                    if (which == urls.size()) {
                        showAddSource();
                    } else {
                        confirmRemoveSource(urls.get(which));
                    }
                })
                .show();
    }

    private void confirmRemoveSource(String url) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.sources_remove_confirm, url))
                .setPositiveButton(R.string.yes, (d, w) -> {
                    PlaylistStore.removeUrl(this, url);
                    refreshValues();
                    Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showAddSource() {
        TvEditText input = (TvEditText) LayoutInflater.from(this).inflate(R.layout.dialog_add_source, null, false);
        FrameLayout wrapper = new FrameLayout(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.sources_add_title)
                .setView(wrapper)
                .setPositiveButton(R.string.add, (d, w) -> addSource(input.getText().toString()))
                .setNegativeButton(R.string.no, null)
                .create();
        input.setOnClickListener(v -> input.openKeyboard());
        input.setOnEditorActionListener((v, actionId, event) -> {
            input.closeKeyboard();
            addSource(input.getText().toString());
            dialog.dismiss();
            return true;
        });
        dialog.show();
    }

    private void addSource(String raw) {
        String url = raw.trim();
        if (TextUtils.isEmpty(url)) return;
        PlaylistStore.addUrl(this, url);
        refreshValues();
        Toast.makeText(this, R.string.added, Toast.LENGTH_SHORT).show();
    }

    private void toggleHome() {
        boolean enable = !isHomeAliasEnabled();
        PlaylistStore.setWantsHome(this, enable);
        getPackageManager().setComponentEnabledSetting(homeAlias(),
                enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
        refreshValues();
        if (enable) {
            // Lets Android ask which Home to use, where "Always" makes VnTV the default.
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        }
    }

    /** Walks the corners clockwise starting from the default: bottom-right, bottom-left, top-left, top-right. */
    private static int nextClockPosition(int current) {
        switch (current) {
            case PlaylistStore.CLOCK_BOTTOM_RIGHT: return PlaylistStore.CLOCK_BOTTOM_LEFT;
            case PlaylistStore.CLOCK_BOTTOM_LEFT: return PlaylistStore.CLOCK_TOP_LEFT;
            case PlaylistStore.CLOCK_TOP_LEFT: return PlaylistStore.CLOCK_TOP_RIGHT;
            default: return PlaylistStore.CLOCK_BOTTOM_RIGHT;
        }
    }

    private ComponentName homeAlias() {
        return new ComponentName(this, getPackageName() + ".HomeAlias");
    }

    private boolean isHomeAliasEnabled() {
        return getPackageManager().getComponentEnabledSetting(homeAlias())
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
}
