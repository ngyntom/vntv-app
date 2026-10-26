package com.example.vntv;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        EditText input = findViewById(R.id.url_input);
        Button addButton = findViewById(R.id.add_button);
        listView = findViewById(R.id.url_list);

        refreshList();

        addButton.setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            if (!TextUtils.isEmpty(url)) {
                PlaylistStore.addUrl(this, url);
                input.setText("");
                refreshList();
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            String url = adapter.getItem(position);
            PlaylistStore.removeUrl(this, url);
            refreshList();
            Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT).show();
            return true;
        });

        TextView defaultChannelValue = findViewById(R.id.default_channel_value);
        Button clearDefaultButton = findViewById(R.id.clear_default_button);
        refreshDefaultChannel(defaultChannelValue);
        clearDefaultButton.setOnClickListener(v -> {
            PlaylistStore.clearDefaultChannel(this);
            refreshDefaultChannel(defaultChannelValue);
            Toast.makeText(this, R.string.default_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshDefaultChannel(TextView view) {
        String name = PlaylistStore.getDefaultChannelName(this);
        String label = getString(R.string.default_channel_label);
        String value = name != null ? name : getString(R.string.default_channel_none);
        view.setText(label + " " + value);
    }

    private void refreshList() {
        List<String> urls = PlaylistStore.getUrls(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, urls);
        listView.setAdapter(adapter);
    }
}
