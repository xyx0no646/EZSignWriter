package dev.ezsign.writer;

final class DeviceInfo {
    enum Transform { NONE, ROTATE_90_CW, FLIP_HORIZONTAL }

    final int width;
    final int height;
    final int colors;
    final int bpp;
    final Transform transform;
    final String name;

    private DeviceInfo(int width, int height, int colors, int bpp, Transform transform, String name) {
        this.width = width;
        this.height = height;
        this.colors = colors;
        this.bpp = bpp;
        this.transform = transform;
        this.name = name;
    }

    static DeviceInfo parse(byte[] response) {
        if (!Protocol.isOk(response)) throw new IllegalArgumentException("Device info SW != 9000");
        int end = response.length - 2;
        int reportedWidth = -1;
        int reportedHeight = -1;
        int colors = 2;
        int bpp = 1;
        int orientation = -1;
        String name = "EZ Sign";

        int p = 0;
        while (p + 2 <= end) {
            int tag = response[p++] & 0xff;
            int len = response[p++] & 0xff;
            if (p + len > end) break;
            if (tag == 0xA0 && len >= 7) {
                int colorCode = response[p + 1] & 0xff;
                colors = colorCode == 0x07 ? 4 : 2;
                bpp = colors == 4 ? 2 : 1;
                int heightBits = ((response[p + 3] & 0xff) << 8) | (response[p + 4] & 0xff);
                reportedWidth = ((response[p + 5] & 0xff) << 8) | (response[p + 6] & 0xff);
                reportedHeight = heightBits / bpp;
            } else if (tag == 0xA1 && len >= 1) {
                orientation = response[p] & 0xff;
            } else if (tag == 0xC0 && len > 0) {
                int n = 0;
                while (n < len && response[p + n] != 0) n++;
                try { name = new String(response, p, n, "US-ASCII").trim(); } catch (Exception ignored) {}
            }
            p += len;
        }

        if (reportedWidth <= 0 || reportedHeight <= 0) {
            throw new IllegalArgumentException("A0 display TLV not found: " + Hex.encode(response));
        }

        if ((reportedWidth == 296 && reportedHeight == 128) ||
            (reportedWidth == 128 && reportedHeight == 296)) {
            return new DeviceInfo(296, 128, colors, bpp, Transform.ROTATE_90_CW, name);
        }
        if ((reportedWidth == 400 && reportedHeight == 300) ||
            (reportedWidth == 300 && reportedHeight == 400)) {
            Transform t = colors == 2 ? Transform.FLIP_HORIZONTAL : Transform.NONE;
            return new DeviceInfo(400, 300, colors, bpp, t, name);
        }

        Transform fallback = orientation == 0 ? Transform.ROTATE_90_CW : Transform.NONE;
        return new DeviceInfo(reportedWidth, reportedHeight, colors, bpp, fallback, name);
    }

    String summary() {
        return width + "×" + height + " / " + colors + " colors";
    }
}
