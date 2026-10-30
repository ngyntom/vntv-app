package com.example.vntv;

import android.os.Handler;
import android.os.Looper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
    public interface OnCategoryClick {
        void onClick(String category);
    }

    public interface OnCategoryLongClick {
        void onLongClick(String category);
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
        ICONS.put(Categories.VTVCAB, R.drawable.ic_cat_tv);
        ICONS.put(Categories.SPORTS, R.drawable.ic_cat_tv);
        ICONS.put("Tổng hợp", R.drawable.ic_cat_tv);
        ICONS.put(ACTION_SETTINGS, R.drawable.ic_settings);
    }

    public interface FocusSelectGate {
        boolean allow();
    }

    private final List<String> categories;
    private final OnCategoryClick listener;
    private int selectedPosition = 0;
    private FocusSelectGate focusSelectGate;
    private OnCategoryLongClick longClickListener;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    /** Row picked up for reordering (NO_POSITION for none). */
    private int movingPosition = RecyclerView.NO_POSITION;

    /** Hold OK on a row: the caller picks that category up for reordering. */
    public void setOnCategoryLongClickListener(OnCategoryLongClick listener) {
        this.longClickListener = listener;
    }

    /** The live row order, so the caller can persist it after a move. */
    public List<String> getCategories() {
        return categories;
    }

    /** Marks the row being reordered (NO_POSITION for none). */
    public void setMovingPosition(int position) {
        int previous = movingPosition;
        movingPosition = position;
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous, MOVING_PAYLOAD);
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position, MOVING_PAYLOAD);
    }

    /** Moves a row in place, so its view (and the D-pad focus on it) travels with it. */
    public void moveItem(int from, int to) {
        categories.add(to, categories.remove(from));
        // The selected category travels with its row; without this every other row's
        // stale bind-time position would point one off.
        if (selectedPosition == from) {
            selectedPosition = to;
        } else if (from < selectedPosition && to >= selectedPosition) {
            selectedPosition--;
        } else if (from > selectedPosition && to <= selectedPosition) {
            selectedPosition++;
        }
        if (movingPosition == from) movingPosition = to;
        notifyItemMoved(from, to);
    }

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
    private static final String MOVING_PAYLOAD = "moving";
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
            if (payloads.contains(MOVING_PAYLOAD)) holder.row.setActivated(position == movingPosition);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        // A pending hold belongs to the content this holder is about to lose.
        longPressHandler.removeCallbacksAndMessages(null);
        String name = categories.get(position);
        boolean isAction = ACTION_SETTINGS.equals(name);
        holder.name.setText(name);
        bindCount(holder, name);
        holder.row.setSelected(!isAction && position == selectedPosition);
        holder.row.setActivated(position == movingPosition);
        bindIcon(holder, name);

        if (isAction) {
            // A plain action: fires only on a real OK/click, never previews on focus pass-through.
            holder.row.setOnClickListener(v -> listener.onClick(name));
            holder.row.setOnFocusChangeListener(null);
            return;
        }

        // Moving D-pad focus onto a category previews it immediately, like a TV side nav;
        // OK/click still works too (touch, or a simulator with no focus events).
        // Resolved by name, not by this bind's position: a row reordered with moveItem()
        // keeps its old listeners, and a captured position would then be stale.
        View.OnClickListener select = v -> selectCategory(name);
        holder.row.setOnClickListener(select);
        holder.row.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                // The row can lose focus while its OK press is still counting down to a
                // long press; drop it so the hold can't fire on an unfocused row.
                longPressHandler.removeCallbacksAndMessages(null);
                return;
            }
            // Checked against the live list: rows reordered by moveItem() keep their old
            // listeners, so the position captured at bind time can be one off.
            if (categories.indexOf(name) != selectedPosition
                    && (focusSelectGate == null || focusSelectGate.allow())) {
                select.onClick(v);
            }
        });

        // A TV remote's OK button never triggers the framework's touch-based long-click
        // detection, so time the key press by hand, exactly like the channel grid: schedule a
        // "long press" after the system threshold on key-down, cancel it (and fall back to a
        // normal click) on key-up.
        holder.longPressTriggered = false;
        holder.longPressRunnable = () -> {
            holder.longPressTriggered = true;
            if (longClickListener != null) longClickListener.onLongClick(name);
        };
        holder.row.setOnKeyListener((v, keyCode, event) -> {
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
                if (!holder.longPressTriggered) select.onClick(v);
                return true;
            }
            return false;
        });
    }

    private void selectCategory(String name) {
        int position = categories.indexOf(name);
        if (position < 0) return;
        if (position != selectedPosition) {
            int previous = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(previous, SELECTION_PAYLOAD);
            notifyItemChanged(position, SELECTION_PAYLOAD);
        }
        listener.onClick(name);
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        longPressHandler.removeCallbacksAndMessages(null);
        super.onViewRecycled(holder);
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
        boolean longPressTriggered;
        Runnable longPressRunnable;
        VH(View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.category_row);
            name = itemView.findViewById(R.id.category_name);
            count = itemView.findViewById(R.id.category_count);
            icon = itemView.findViewById(R.id.category_icon);
        }
    }
}
