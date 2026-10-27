package dev.ezsign.writer;

/**
 * Detects blue AND cyan, including bright aqua (hue near 180 degrees).
 * Only black (index 0) and white (index 1) are used for simulation.
 * Kept free of Android dependencies so the color boundaries can be tested
 * by the protocol self-test using plain Java.
 */
final class BlueEmulation {
    private BlueEmulation() {}

    static boolean matches(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float chroma = max - min;
        if (max < 1f || chroma < 34f || chroma / max < 0.16f) {
            return false;
        }

        float hue;
        if (max == r) {
            hue = 60f * (((g - b) / chroma) % 6f);
        } else if (max == g) {
            hue = 60f * (((b - r) / chroma) + 2f);
        } else {
            hue = 60f * (((r - g) / chroma) + 4f);
        }
        if (hue < 0f) hue += 360f;

        // Cyan has B ~= G, NOT B > G. The old strict comparison accidentally
        // excluded #00FFFF, so neither pseudo-blue mode affected aqua images.
        // B >= 0.88 * G excludes ordinary green while including cyan/teal.
        return hue >= 170f && hue <= 270f
                && b >= g * 0.88f
                && b >= r + 25f;
    }

    static int convert(float luminance, int x, int y, boolean hatched) {
        if (!hatched) {
            // Solid black makes a cyan logo visible even against white paper.
            return luminance < 248f ? 0 : 1;
        }

        // Alternating diagonal black/white stripes. Density preserves a
        // rough impression of light/dark blue without using warm pigments.
        int blackParts = luminance < 100f ? 3 : (luminance > 190f ? 1 : 2);
        return ((x + y) & 3) < blackParts ? 0 : 1;
    }
}
