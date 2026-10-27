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

    private static final int FAMILY_GRAY = 0;
    private static final int FAMILY_YELLOW = 1;
    private static final int FAMILY_RED = 2;
    private static final int FAMILY_BLUE = 3;

    private ImagePipeline() {}

    static Result prepare(Bitmap source, DeviceInfo spec, boolean mirrorCorrection, ColorMode mode) {
        Bitmap fitted = centerCrop(source, spec.width, spec.height);
        int[] indices = atkinson(fitted, spec.colors, mode);

        Bitmap preview = render(indices, spec.width, spec.height);

        int[] wireIndices = mirrorCorrection
                ? flipHorizontal(indices, spec.width, spec.height)
                : indices;

        PixelPacker.Packed packed = PixelPacker.transformAndPack(
                wireIndices, spec.width, spec.height, spec.bpp, spec.transform);
        if (fitted != source && fitted != preview) fitted.recycle();
        return new Result(preview, packed.bytes, packed.width, packed.height);
    }

    static Bitmap preview(Bitmap source, int width, int height, int colors, ColorMode mode) {
        Bitmap fitted = centerCrop(source, width, height);
        int[] indices = atkinson(fitted, colors, mode);
        Bitmap out = render(indices, width, height);
        if (fitted != source && fitted != out) fitted.recycle();
        return out;
    }

    private static int[] flipHorizontal(int[] src, int w, int h) {
        int[] out = new int[src.length];
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                out[row + x] = src[row + (w - 1 - x)];
            }
        }
        return out;
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
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(source, m, p);
        return out;
    }

    private static int[] atkinson(Bitmap bitmap, int colors, ColorMode mode) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int n = w * h;
        float[] r = new float[n];
        float[] g = new float[n];
        float[] b = new float[n];
        int[] family = new int[n];
        float[] originalLum = new float[n];
        int[] argb = new int[n];
        bitmap.getPixels(argb, 0, w, 0, 0, w, h);

        for (int i = 0; i < n; i++) {
            int c = argb[i];
            int a = Color.alpha(c);
            float rr = (Color.red(c) * a + 255 * (255 - a)) / 255f;
            float gg = (Color.green(c) * a + 255 * (255 - a)) / 255f;
            float bb = (Color.blue(c) * a + 255 * (255 - a)) / 255f;
            r[i] = rr;
            g[i] = gg;
            b[i] = bb;
            family[i] = colorFamily(rr, gg, bb);
            originalLum[i] = luminance(rr, gg, bb);
        }

        int[] out = new int[n];
        for (int y = 0; y < h; y++) {
            boolean reverse = (y & 1) != 0;
            int x0 = reverse ? w - 1 : 0;
            int xEnd = reverse ? -1 : w;
            int step = reverse ? -1 : 1;

            for (int x = x0; x != xEnd; x += step) {
                int i = y * w + x;
                float rr = clamp(r[i]);
                float gg = clamp(g[i]);
                float bb = clamp(b[i]);

                // Pseudo-blue uses deterministic black/white so a clear shape
                // or hatch survives. Do not spread its quantization error
                // into nearby pixels. Standard mode is unchanged.
                if (family[i] == FAMILY_BLUE && mode != ColorMode.STANDARD) {
                    out[i] = BlueEmulation.convert(
                            originalLum[i], x, y,
                            mode == ColorMode.PSEUDO_BLUE_PATTERN);
                    continue;
                }

                int chosen = nearestAllowed(rr, gg, bb, colors, family[i]);
                out[i] = chosen;

                int[][] palette = colors == 4 ? PALETTE4 : PALETTE2;
                float er = rr - palette[chosen][0];
                float eg = gg - palette[chosen][1];
                float eb = bb - palette[chosen][2];

                spread(r,g,b,w,h,x + step,     y,     er,eg,eb,1f/8f);
                spread(r,g,b,w,h,x + 2*step,   y,     er,eg,eb,1f/8f);
                spread(r,g,b,w,h,x - step,     y + 1, er,eg,eb,1f/8f);
                spread(r,g,b,w,h,x,            y + 1, er,eg,eb,1f/8f);
                spread(r,g,b,w,h,x + step,     y + 1, er,eg,eb,1f/8f);
                spread(r,g,b,w,h,x,            y + 2, er,eg,eb,1f/8f);
            }
        }
        return out;
    }

    private static int colorFamily(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float chroma = max - min;
        if (max < 1f || chroma < 42f || chroma / max < 0.24f) return FAMILY_GRAY;

        float hue;
        if (max == r) {
            hue = 60f * (((g - b) / chroma) % 6f);
        } else if (max == g) {
            hue = 60f * (((b - r) / chroma) + 2f);
        } else {
            hue = 60f * (((r - g) / chroma) + 4f);
        }
        if (hue < 0f) hue += 360f;

        if (hue >= 38f && hue <= 72f && b < 175f) return FAMILY_YELLOW;
        if ((hue <= 24f || hue >= 342f) && r > g * 1.12f && r > b * 1.12f) return FAMILY_RED;
        if (BlueEmulation.matches(r, g, b)) return FAMILY_BLUE;
        return FAMILY_GRAY;
    }

    private static int nearestAllowed(float r, float g, float b, int colors, int family) {
        if (colors != 4 || family == FAMILY_GRAY || family == FAMILY_BLUE) {
            return luminance(r, g, b) >= 150f ? WHITE : BLACK;
        }

        int accent = family == FAMILY_YELLOW ? YELLOW : RED;
        float blackD = distance(r,g,b,PALETTE4[BLACK]);
        float whiteD = distance(r,g,b,PALETTE4[WHITE]);
        float accentD = distance(r,g,b,PALETTE4[accent]);

        if (accentD <= blackD && accentD <= whiteD) return accent;
        return whiteD < blackD ? WHITE : BLACK;
    }

    private static float distance(float r, float g, float b, int[] p) {
        float dr = r - p[0];
        float dg = g - p[1];
        float db = b - p[2];
        return dr*dr*0.26f + dg*dg*0.66f + db*db*0.08f;
    }

    private static float luminance(float r, float g, float b) {
        return 0.2126f*r + 0.7152f*g + 0.0722f*b;
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
