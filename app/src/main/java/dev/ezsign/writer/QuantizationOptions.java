package dev.ezsign.writer;

/** Independent controls for palette distance and error diffusion. */
enum DistanceMode {
    RGB("RGB"), LAB("Lab");

    final String label;
    DistanceMode(String label) { this.label = label; }
    @Override public String toString() { return label; }

    static DistanceMode fromOrdinal(int ordinal) {
        DistanceMode[] modes = values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : RGB;
    }
}

enum DitherMode {
    ATKINSON("Atkinson"), STUCKI("Stucki");

    final String label;
    DitherMode(String label) { this.label = label; }
    @Override public String toString() { return label; }

    static DitherMode fromOrdinal(int ordinal) {
        DitherMode[] modes = values();
        return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : ATKINSON;
    }
}

/** CIELAB conversion for D65 sRGB, compared using squared Delta E 1976. */
final class ColorMetrics {
    private ColorMetrics() {}

    static float[] rgbToLab(float red, float green, float blue) {
        double r = linear(red / 255.0);
        double g = linear(green / 255.0);
        double b = linear(blue / 255.0);
        double x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047;
        double y =  r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883;

        double fx = labF(x);
        double fy = labF(y);
        double fz = labF(z);
        return new float[]{
                (float) (116.0 * fy - 16.0),
                (float) (500.0 * (fx - fy)),
                (float) (200.0 * (fy - fz))
        };
    }

    static float squaredDeltaE(float[] a, float[] b) {
        float dl = a[0] - b[0];
        float da = a[1] - b[1];
        float db = a[2] - b[2];
        return dl * dl + da * da + db * db;
    }

    private static double linear(double x) {
        x = Math.max(0.0, Math.min(1.0, x));
        return x <= 0.04045 ? x / 12.92
                : Math.pow((x + 0.055) / 1.055, 2.4);
    }

    private static double labF(double x) {
        double delta = 6.0 / 29.0;
        return x > delta * delta * delta ? Math.cbrt(x)
                : x / (3.0 * delta * delta) + 4.0 / 29.0;
    }
}

/** Error diffusion kernels. Mirror horizontal offsets for serpentine rows. */
final class DitherKernel {
    private static final int[][] ATKINSON = {
        { 1, 0, 1}, { 2, 0, 1},
        {-1, 1, 1}, { 0, 1, 1}, { 1, 1, 1},
        { 0, 2, 1}
    };

    // Normalized Stucki weights, sum = 42.
    private static final int[][] STUCKI = {
        { 1, 0, 8}, { 2, 0, 4},
        {-2, 1, 2}, {-1, 1, 4}, { 0, 1, 8}, { 1, 1, 4}, { 2, 1, 2},
        {-2, 2, 1}, {-1, 2, 2}, { 0, 2, 4}, { 1, 2, 2}, { 2, 2, 1}
    };

    private DitherKernel() {}

    static int totalWeight(DitherMode mode) {
        int sum = 0;
        for (int[] item : (mode == DitherMode.STUCKI ? STUCKI : ATKINSON)) {
            sum += item[2];
        }
        return sum;
    }

    static void spread(
            float[] red, float[] green, float[] blue,
            int width, int height, int x, int y, int scanDirection,
            float errorR, float errorG, float errorB, DitherMode mode) {
        int[][] kernel = mode == DitherMode.STUCKI ? STUCKI : ATKINSON;
        float divisor = mode == DitherMode.STUCKI ? 42f : 8f;
        for (int[] offset : kernel) {
            int tx = x + offset[0] * scanDirection;
            int ty = y + offset[1];
            if (tx < 0 || ty < 0 || tx >= width || ty >= height) continue;
            int i = ty * width + tx;
            float scale = offset[2] / divisor;
            red[i] += errorR * scale;
            green[i] += errorG * scale;
            blue[i] += errorB * scale;
        }
    }
}
