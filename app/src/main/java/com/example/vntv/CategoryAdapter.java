package com.example.vntv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
    public interface OnCategoryClick {
        void onClick(String category);
    }

    /** A plain nav action (e.g. "Settings"): only reacts to a real OK/click, never to focus
     *  passing over it, and never shows as the persistently "selected" filter. */
    public static final String ACTION_SETTINGS = "Cài đặt";

    private static final Map<String, Integer> ICONS = new HashMap<>();
    static {
        ICONS.put("Yêu thích", R.drawable.ic_cat_favorite);
        ICONS.put("VTV", R.drawable.ic_cat_tv);
        ICONS.put("HTV", R.drawable.ic_cat_tv);
        ICONS.put("SCTV", R.drawable.ic_cat_tv);
        ICONS.put("THVL", R.drawable.ic_cat_tv);
        ICONS.put("Tổng hợp", R.drawable.ic_cat_local);
        ICONS.put(ACTION_SETTINGS, R.drawable.ic_settings);
    }

    private final List<String> categories;
    private final OnCategoryClick listener;
    private int selectedPosition = 0;

    public CategoryAdapter(List<String> categories, OnCategoryClick listener) {
        this(categories, 0, listener);
    }

    public CategoryAdapter(List<String> categories, int initialSelected, OnCategoryClick listener) {
        this.categories = categories;
        this.listener = listener;
        this.selectedPosition = initialSelected;
    }

    public String getSelectedCategory() {
        return categories.get(selectedPosition);
    }

    /** Updates which row is highlighted as "selected" without firing the click listener
     *  (the caller already knows what to show, e.g. syncing to the channel that was playing). */
    public void setSelectedPosition(int position) {
        if (position < 0 || position >= categories.size() || position == selectedPosition) return;
        int previous = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(previous);
        notifyItemChanged(selectedPosition);
    }

    public int indexOf(String category) {
        return categories.indexOf(category);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String name = categories.get(position);
        boolean isAction = ACTION_SETTINGS.equals(name);
        holder.name.setText(name);
        holder.row.setSelected(!isAction && position == selectedPosition);
        Integer iconRes = ICONS.get(name);
        if (iconRes != null) holder.icon.setImageResource(iconRes);

        if (isAction) {
            // A plain action: fires only on a real OK/click, never previews on focus pass-through.
            holder.row.setOnClickListener(v -> listener.onClick(name));
            holder.row.setOnFocusChangeListener(null);
            return;
        }

        // Moving D-pad focus onto a category previews it immediately, like a TV side nav;
        // OK/click still works too (touch, or a simulator with no focus events).
        View.OnClickListener select = v -> {
            int previous = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(previous);
            notifyItemChanged(selectedPosition);
            listener.onClick(name);
        };
        holder.row.setOnClickListener(select);
        holder.row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && position != selectedPosition) {
                select.onClick(v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        View row;
        TextView name;
        ImageView icon;
        VH(View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.category_row);
            name = itemView.findViewById(R.id.category_name);
            icon = itemView.findViewById(R.id.category_icon);
        }
    }
}
