package com.example.vntv;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The sidebar's content categories and the user's chosen order for them. */
public final class Categories {
    public static final String FAVORITES = "Yêu thích";
    public static final String OTHER = "Tổng hợp";
    public static final String SCTV = "SCTV";
    public static final String THVL = "THVL";
    public static final String HTV = "HTV";
    public static final String VTV = "VTV";
    public static final String VTVCAB = "VTVcab";

    public static final List<String> DEFAULT_ORDER =
            Arrays.asList(FAVORITES, OTHER, SCTV, THVL, HTV, VTV, VTVCAB);

    private Categories() {}

    /** Saved order, with unknown names dropped and categories added since then appended. */
    public static List<String> load(Context ctx) {
        List<String> order = new ArrayList<>();
        for (String name : PlaylistStore.getCategoryOrder(ctx)) {
            if (DEFAULT_ORDER.contains(name) && !order.contains(name)) order.add(name);
        }
        for (String name : DEFAULT_ORDER) {
            if (!order.contains(name)) order.add(name);
        }
        return order;
    }

    public static void save(Context ctx, List<String> order) {
        PlaylistStore.setCategoryOrder(ctx, order);
    }
}
