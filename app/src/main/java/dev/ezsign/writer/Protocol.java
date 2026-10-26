package dev.ezsign.writer;

final class Protocol {
    static final byte[] SELECT_AID = Hex.decode("00A4040007D2760000850101");
    static final byte[] AUTH = Hex.decode("002000010420091210");
    static final byte[] DEVICE_INFO = Hex.decode("00D1000000");
    static final byte[] REFRESH = Hex.decode("F0D4850000");

    private Protocol() {}

    static byte[] rawChunk(int section, int sequence, byte[] data, int offset, int length) {
        if ((section & ~0xff) != 0) throw new IllegalArgumentException("section out of range");
        if ((sequence & ~0xff) != 0) throw new IllegalArgumentException("sequence out of range");
        if (length < 0 || length > 250) throw new IllegalArgumentException("length out of range");
        if (offset < 0 || offset + length > data.length) throw new IllegalArgumentException("bad slice");
        byte[] apdu = new byte[5 + length];
        apdu[0] = (byte) 0xF0;
        apdu[1] = (byte) 0xD2;
        apdu[2] = (byte) section;
        apdu[3] = (byte) sequence;
        apdu[4] = (byte) length;
        System.arraycopy(data, offset, apdu, 5, length);
        return apdu;
    }

    static boolean isOk(byte[] response) {
        return response != null && response.length >= 2 &&
                (response[response.length - 2] & 0xff) == 0x90 &&
                (response[response.length - 1] & 0xff) == 0x00;
    }

    static String sw(byte[] response) {
        if (response == null || response.length < 2) return "----";
        return String.format("%02X%02X", response[response.length - 2] & 0xff,
                response[response.length - 1] & 0xff);
    }
}
