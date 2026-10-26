package dev.ezsign.writer;

final class PixelPacker {
    private PixelPacker() {}

    static Packed transformAndPack(int[] src, int w, int h, int bpp, DeviceInfo.Transform transform) {
        int[] pixels;
        int tw;
        int th;
        if (transform == DeviceInfo.Transform.ROTATE_90_CW) {
            tw = h;
            th = w;
            pixels = new int[tw * th];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int nx = h - 1 - y;
                    int ny = x;
                    pixels[ny * tw + nx] = src[y * w + x];
                }
            }
        } else if (transform == DeviceInfo.Transform.FLIP_HORIZONTAL) {
            tw = w;
            th = h;
            pixels = new int[tw * th];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + (w - 1 - x)] = src[y * w + x];
                }
            }
        } else {
            tw = w;
            th = h;
            pixels = src;
        }

        int ppb = bpp == 2 ? 4 : 8;
        if (bpp != 1 && bpp != 2) throw new IllegalArgumentException("Unsupported bpp: " + bpp);
        if (tw % ppb != 0) throw new IllegalArgumentException("Packed width not divisible by " + ppb + ": " + tw);
        byte[] out = new byte[(tw / ppb) * th];
        int o = 0;
        for (int y = 0; y < th; y++) {
            for (int x = tw - 1; x >= 0; x -= ppb) {
                int v = 0;
                for (int k = 0; k < ppb; k++) {
                    int color = pixels[y * tw + (x - k)];
                    if (bpp == 1) v |= (color & 1) << k;
                    else v |= (color & 3) << (k * 2);
                }
                out[o++] = (byte) v;
            }
        }
        return new Packed(out, tw, th);
    }

    static final class Packed {
        final byte[] bytes;
        final int width;
        final int height;
        Packed(byte[] bytes, int width, int height) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }
    }
}
