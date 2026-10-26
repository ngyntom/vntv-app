package com.example.vntv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
    public interface OnCategoryClick {
        void onClick(String category);
    }

    private final List<String> categories;
    private final OnCategoryClick listener;
    private int selectedPosition = 0;

    public CategoryAdapter(List<String> categories, OnCategoryClick listener) {
        this.categories = categories;
        this.listener = listener;
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
        holder.name.setText(name);
        holder.name.setSelected(position == selectedPosition);
        holder.name.setOnClickListener(v -> {
            int previous = selectedPosition;
            selectedPosition = position;
            notifyItemChanged(previous);
            notifyItemChanged(selectedPosition);
            listener.onClick(name);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        VH(View itemView) {
            super(itemView);
            name = (TextView) itemView;
        }
    }
}
