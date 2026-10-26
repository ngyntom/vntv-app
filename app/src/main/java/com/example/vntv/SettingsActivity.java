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

        TextView favoritesValue = findViewById(R.id.default_channel_value);
        Button clearFavoritesButton = findViewById(R.id.clear_default_button);
        clearFavoritesButton.setText(R.string.clear_favorites);
        refreshFavorites(favoritesValue);
        clearFavoritesButton.setOnClickListener(v -> {
            PlaylistStore.clearFavorites(this);
            refreshFavorites(favoritesValue);
            Toast.makeText(this, R.string.favorites_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void refreshFavorites(TextView view) {
        int count = PlaylistStore.getFavorites(this).size();
        view.setText(getString(R.string.favorites_count, count));
    }

    private void refreshList() {
        List<String> urls = PlaylistStore.getUrls(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, urls);
        listView.setAdapter(adapter);
    }
}
