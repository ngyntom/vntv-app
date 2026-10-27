package com.example.vntv;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {
    public interface OnChannelClick {
        void onClick(Channel channel);
    }

    public interface OnChannelLongClick {
        void onLongClick(Channel channel);
    }

    private List<Channel> channels;
    private final OnChannelClick listener;
    private OnChannelLongClick longClickListener;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());

    public ChannelAdapter(List<Channel> channels, OnChannelClick listener) {
        this.channels = channels;
        this.listener = listener;
    }

    public void setOnChannelLongClickListener(OnChannelLongClick listener) {
        this.longClickListener = listener;
    }

    public void setChannels(List<Channel> channels) {
        this.channels = channels;
        notifyDataSetChanged();
    }

    public int getItemCountPublic() {
        return channels.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Channel c = channels.get(position);
        holder.name.setText(c.name);
        holder.itemView.setOnClickListener(v -> listener.onClick(c));
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.06f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            v.setElevation(hasFocus ? 10f : 0f);
        });
        // A TV remote's OK button never triggers the framework's touch-based long-click
        // detection, so time the key press by hand: schedule a "long press" after the system
        // threshold on key-down, cancel it (and fall back to a normal click) on key-up.
        holder.longPressTriggered = false;
        holder.longPressRunnable = () -> {
            holder.longPressTriggered = true;
            if (longClickListener != null) longClickListener.onLongClick(c);
        };
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    holder.longPressTriggered = false;
                    longPressHandler.removeCallbacks(holder.longPressRunnable);
                    longPressHandler.postDelayed(holder.longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                longPressHandler.removeCallbacks(holder.longPressRunnable);
                if (!holder.longPressTriggered) {
                    listener.onClick(c);
                }
                return true;
            }
            return false;
        });
        if (!TextUtils.isEmpty(c.logoUrl)) {
            holder.logo.setVisibility(View.VISIBLE);
            Glide.with(holder.logo.getContext())
                    .load(c.logoUrl)
                    .transform(new TrimTransparentTransformation())
                    .into(holder.logo);
        } else {
            holder.logo.setVisibility(View.GONE);
            holder.logo.setImageDrawable(null);
        }
        if (!TextUtils.isEmpty(c.quality)) {
            holder.quality.setVisibility(View.VISIBLE);
            holder.quality.setText(c.quality);
        } else {
            holder.quality.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        TextView quality;
        ImageView logo;
        boolean longPressTriggered;
        Runnable longPressRunnable;
        VH(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channel_name);
            quality = itemView.findViewById(R.id.channel_quality);
            logo = itemView.findViewById(R.id.channel_logo);
        }
    }
}
