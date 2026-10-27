package dev.ezsign.writer;

/** Image conversion modes. Blue is represented using black/white only. */
enum ColorMode {
    STANDARD("標準"),
    PSEUDO_BLUE("擬似青"),
    PSEUDO_BLUE_PATTERN("擬似青（パターン）");

    final String label;

    ColorMode(String label) {
        this.label = label;
    }

    @Override public String toString() {
        return label;
    }

    static ColorMode fromOrdinal(int value) {
        ColorMode[] modes = values();
        return value >= 0 && value < modes.length ? modes[value] : STANDARD;
    }
}
