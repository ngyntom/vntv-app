package com.example.vntv;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Channel logos arrive with wildly different amounts of transparent padding around the mark, so
 * fitting them all into the same ImageView box still makes some read as tiny and others fill it.
 * Cropping to the opaque content first gives every logo roughly the same visual weight once
 * fitCenter scales that tight box up to the tile.
 */
public class TrimTransparentTransformation extends BitmapTransformation {
    private static final String ID = "com.example.vntv.TrimTransparentTransformation";
    private static final int ALPHA_THRESHOLD = 10;

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        if (!toTransform.hasAlpha()) return toTransform;
        int w = toTransform.getWidth();
        int h = toTransform.getHeight();
        if (w <= 0 || h <= 0) return toTransform;

        int[] pixels = new int[w * h];
        toTransform.getPixels(pixels, 0, w, 0, 0, w, h);

        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                if ((pixels[row + x] >>> 24) > ALPHA_THRESHOLD) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) return toTransform; // fully transparent
        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        if (minX == 0 && minY == 0 && cropW == w && cropH == h) return toTransform; // nothing to trim
        return Bitmap.createBitmap(toTransform, minX, minY, cropW, cropH);
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TrimTransparentTransformation;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }
}
