package dev.ezsign.writer;

final class Hex {
    private Hex() {}

    static byte[] decode(String s) {
        String hex = s.replace(" ", "").replace("\n", "");
        if ((hex.length() & 1) != 0) throw new IllegalArgumentException("Odd hex length");
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Bad hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    static String encode(byte[] data) {
        final char[] h = "0123456789ABCDEF".toCharArray();
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xff;
            out[i * 2] = h[v >>> 4];
            out[i * 2 + 1] = h[v & 15];
        }
        return new String(out);
    }
}
