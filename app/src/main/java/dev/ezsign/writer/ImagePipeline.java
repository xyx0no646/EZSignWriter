package dev.ezsign.writer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

final class ImagePipeline {
    static final int BLACK = 0;
    static final int WHITE = 1;
    static final int YELLOW = 2;
    static final int RED = 3;

    private static final int[][] PALETTE4 = {
        {0, 0, 0}, {255, 255, 255}, {255, 220, 0}, {220, 0, 0}
    };
    private static final int[][] PALETTE2 = {
        {0, 0, 0}, {255, 255, 255}
    };

    private ImagePipeline() {}

    static Result prepare(Bitmap source, DeviceInfo spec) {
        Bitmap fitted = centerCrop(source, spec.width, spec.height);
        int[] indices = floydSteinberg(fitted, spec.colors);
        Bitmap preview = render(indices, spec.width, spec.height);
        PixelPacker.Packed packed = PixelPacker.transformAndPack(indices, spec.width, spec.height, spec.bpp, spec.transform);
        if (fitted != source && fitted != preview) fitted.recycle();
        return new Result(preview, packed.bytes, packed.width, packed.height);
    }

    static Bitmap centerCrop(Bitmap source, int targetW, int targetH) {
        float scale = Math.max(targetW / (float) source.getWidth(), targetH / (float) source.getHeight());
        float dx = (targetW - source.getWidth() * scale) * 0.5f;
        float dy = (targetH - source.getHeight() * scale) * 0.5f;
        Bitmap out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        c.drawBitmap(source, m, p);
        return out;
    }

    private static int[] floydSteinberg(Bitmap bitmap, int colors) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int n = w * h;
        float[] r = new float[n];
        float[] g = new float[n];
        float[] b = new float[n];
        int[] argb = new int[n];
        bitmap.getPixels(argb, 0, w, 0, 0, w, h);
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            int a = Color.alpha(c);
            r[i] = (Color.red(c) * a + 255 * (255 - a)) / 255f;
            g[i] = (Color.green(c) * a + 255 * (255 - a)) / 255f;
            b[i] = (Color.blue(c) * a + 255 * (255 - a)) / 255f;
        }

        int[][] palette = colors == 4 ? PALETTE4 : PALETTE2;
        int[] out = new int[n];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float rr = clamp(r[i]);
                float gg = clamp(g[i]);
                float bb = clamp(b[i]);
                int best = 0;
                float bestD = Float.MAX_VALUE;
                for (int p = 0; p < palette.length; p++) {
                    float dr = rr - palette[p][0];
                    float dg = gg - palette[p][1];
                    float db = bb - palette[p][2];
                    float d = dr * dr * 0.30f + dg * dg * 0.59f + db * db * 0.11f;
                    if (d < bestD) { bestD = d; best = p; }
                }
                out[i] = best;
                float er = rr - palette[best][0];
                float eg = gg - palette[best][1];
                float eb = bb - palette[best][2];
                spread(r, g, b, w, h, x + 1, y,     er, eg, eb, 7f / 16f);
                spread(r, g, b, w, h, x - 1, y + 1, er, eg, eb, 3f / 16f);
                spread(r, g, b, w, h, x,     y + 1, er, eg, eb, 5f / 16f);
                spread(r, g, b, w, h, x + 1, y + 1, er, eg, eb, 1f / 16f);
            }
        }
        return out;
    }

    private static void spread(float[] r, float[] g, float[] b, int w, int h,
                               int x, int y, float er, float eg, float eb, float f) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        r[i] += er * f;
        g[i] += eg * f;
        b[i] += eb * f;
    }

    private static float clamp(float x) { return Math.max(0f, Math.min(255f, x)); }

    private static Bitmap render(int[] idx, int w, int h) {
        int[] out = new int[idx.length];
        for (int i = 0; i < idx.length; i++) {
            int[] p = PALETTE4[idx[i]];
            out[i] = Color.rgb(p[0], p[1], p[2]);
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
    }

    static final class Result {
        final Bitmap preview;
        final byte[] bytes;
        final int packedWidth;
        final int packedHeight;
        Result(Bitmap preview, byte[] bytes, int packedWidth, int packedHeight) {
            this.preview = preview;
            this.bytes = bytes;
            this.packedWidth = packedWidth;
            this.packedHeight = packedHeight;
        }
    }
}
