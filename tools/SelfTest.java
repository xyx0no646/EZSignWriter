package dev.ezsign.writer;

import java.util.Arrays;

public final class SelfTest {
    public static void main(String[] args) {
        DeviceInfo a = DeviceInfo.parse(Hex.decode("A007000700025000809000"));
        check(a.width == 296 && a.height == 128 && a.colors == 4 && a.bpp == 2,
                "2.9in 4-color TLV");
        check(a.transform == DeviceInfo.Transform.ROTATE_90_CW, "2.9in transform");

        DeviceInfo b = DeviceInfo.parse(Hex.decode("A007000100012C01909000"));
        check(b.width == 400 && b.height == 300 && b.colors == 2 && b.bpp == 1,
                "4.2in 2-color TLV");
        check(b.transform == DeviceInfo.Transform.FLIP_HORIZONTAL, "4.2in 2-color transform");

        PixelPacker.Packed p1 = PixelPacker.transformAndPack(
                new int[]{0,1,0,1,0,1,0,1}, 8, 1, 1, DeviceInfo.Transform.NONE);
        check(Arrays.equals(p1.bytes, new byte[]{0x55}), "1bpp packing");

        PixelPacker.Packed p2 = PixelPacker.transformAndPack(
                new int[]{0,1,2,3}, 4, 1, 2, DeviceInfo.Transform.NONE);
        check(Arrays.equals(p2.bytes, new byte[]{0x1B}), "2bpp packing");

        byte[] raw = Protocol.rawChunk(0, 2, new byte[]{(byte)0xAA,(byte)0xBB,(byte)0xCC}, 0, 3);
        check(Hex.encode(raw).equals("F0D2000203AABBCC"), "F0D2 APDU");
        check(Hex.encode(Protocol.AUTH).equals("002000010420091210"), "AUTH APDU");
        check(Hex.encode(Protocol.REFRESH).equals("F0D4850000"), "refresh APDU");

        System.out.println("SelfTest OK");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
