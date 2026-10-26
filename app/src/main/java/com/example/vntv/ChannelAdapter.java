package com.example.vntv;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
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
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(c);
                return true;
            }
            return false;
        });
        if (!TextUtils.isEmpty(c.logoUrl)) {
            holder.logo.setVisibility(View.VISIBLE);
            Glide.with(holder.logo.getContext())
                    .load(c.logoUrl)
                    .into(holder.logo);
        } else {
            holder.logo.setVisibility(View.GONE);
            holder.logo.setImageDrawable(null);
        }
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        ImageView logo;
        VH(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channel_name);
            logo = itemView.findViewById(R.id.channel_logo);
        }
    }
}
