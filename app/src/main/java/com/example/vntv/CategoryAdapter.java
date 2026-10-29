package com.example.vntv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

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

    public interface FocusSelectGate {
        boolean allow();
    }

    private final List<String> categories;
    private final OnCategoryClick listener;
    private int selectedPosition = 0;
    private FocusSelectGate focusSelectGate;

    /** Focus-triggered previews only fire when this allows it, so focus the system assigns on
     *  its own (first layout, or a focused tile being rebound) can't silently switch category. */
    public void setFocusSelectGate(FocusSelectGate gate) {
        this.focusSelectGate = gate;
    }

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

    private static final String SELECTION_PAYLOAD = "selection";
    private static final String COUNT_PAYLOAD = "count";
    private static final String LOGO_PAYLOAD = "logo";
    private Map<String, Integer> counts = new HashMap<>();
    private Map<String, String> logos = new HashMap<>();

    /** Logo URL per category (its first channel's logo), replacing the generic icon. */
    public void setLogos(Map<String, String> logos) {
        if (logos.equals(this.logos)) return;
        this.logos = new HashMap<>(logos);
        notifyItemRangeChanged(0, categories.size(), LOGO_PAYLOAD);
    }

    private void bindIcon(VH holder, String name) {
        Integer fallback = ICONS.get(name);
        String logo = logos.get(name);
        if (logo != null) {
            Glide.with(holder.icon.getContext())
                    .load(logo)
                    .transform(new TrimTransparentTransformation())
                    .placeholder(fallback != null ? fallback : 0)
                    .error(fallback != null ? fallback : 0)
                    .into(holder.icon);
        } else {
            Glide.with(holder.icon.getContext()).clear(holder.icon);
            if (fallback != null) holder.icon.setImageResource(fallback);
        }
    }

    /** Channel count shown at the right of each category row. */
    public void setCounts(Map<String, Integer> counts) {
        if (counts.equals(this.counts)) return;
        this.counts = new HashMap<>(counts);
        notifyItemRangeChanged(0, categories.size(), COUNT_PAYLOAD);
    }

    private void bindCount(VH holder, String name) {
        Integer count = ACTION_SETTINGS.equals(name) ? null : counts.get(name);
        holder.count.setVisibility(count == null ? View.GONE : View.VISIBLE);
        if (count != null) holder.count.setText(String.valueOf(count));
    }

    /** Updates which row is highlighted as "selected" without firing the click listener
     *  (the caller already knows what to show, e.g. syncing to the channel that was playing). */
    public void setSelectedPosition(int position) {
        if (position < 0 || position >= categories.size() || position == selectedPosition) return;
        int previous = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(previous, SELECTION_PAYLOAD);
        notifyItemChanged(selectedPosition, SELECTION_PAYLOAD);
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
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            // Partial update (highlight and/or count only): keep the click/focus listeners
            // already attached so a rebind can't disturb live D-pad focus.
            String name = categories.get(position);
            if (payloads.contains(SELECTION_PAYLOAD)) {
                holder.row.setSelected(!ACTION_SETTINGS.equals(name) && position == selectedPosition);
            }
            if (payloads.contains(COUNT_PAYLOAD)) bindCount(holder, name);
            if (payloads.contains(LOGO_PAYLOAD)) bindIcon(holder, name);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        String name = categories.get(position);
        boolean isAction = ACTION_SETTINGS.equals(name);
        holder.name.setText(name);
        bindCount(holder, name);
        holder.row.setSelected(!isAction && position == selectedPosition);
        bindIcon(holder, name);

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
            notifyItemChanged(previous, SELECTION_PAYLOAD);
            notifyItemChanged(selectedPosition, SELECTION_PAYLOAD);
            listener.onClick(name);
        };
        holder.row.setOnClickListener(select);
        holder.row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && position != selectedPosition
                    && (focusSelectGate == null || focusSelectGate.allow())) {
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
        TextView count;
        ImageView icon;
        VH(View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.category_row);
            name = itemView.findViewById(R.id.category_name);
            count = itemView.findViewById(R.id.category_count);
            icon = itemView.findViewById(R.id.category_icon);
        }
    }
}
