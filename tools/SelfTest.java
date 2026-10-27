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

        PixelPacker.Packed groups = PixelPacker.transformAndPack(
                new int[]{0,1,2,3, 3,2,1,0}, 8, 1, 2, DeviceInfo.Transform.NONE);
        check(Arrays.equals(groups.bytes, new byte[]{0x1B, (byte)0xE4}),
                "row byte-group order");

        byte[] raw = Protocol.rawChunk(0, 2, new byte[]{(byte)0xAA,(byte)0xBB,(byte)0xCC}, 0, 3);
        check(Hex.encode(raw).equals("F0D2000203AABBCC"), "F0D2 APDU");
        check(Hex.encode(Protocol.AUTH).equals("002000010420091210"), "AUTH APDU");
        check(Hex.encode(Protocol.REFRESH).equals("F0D4850000"), "refresh APDU");

        // Regression: bright cyan (#00FFFF) previously escaped both
        // pseudo-blue modes, leaving the main image area white.
        check(BlueEmulation.matches(0, 255, 255), "pure cyan detected");
        check(BlueEmulation.matches(0, 253, 246), "aqua ring detected");
        check(BlueEmulation.matches(175, 255, 255), "light cyan detected");
        check(BlueEmulation.matches(35, 90, 230), "blue detected");
        check(!BlueEmulation.matches(0, 255, 0), "green untouched");
        check(!BlueEmulation.matches(255, 220, 0), "yellow untouched");
        check(!BlueEmulation.matches(255, 0, 130), "magenta untouched");
        check(!BlueEmulation.matches(240, 240, 240), "neutral white untouched");
        check(BlueEmulation.convert(182f, 0, 0, false) == 0,
                "cyan solid mode remains visible");
        check(BlueEmulation.convert(182f, 0, 0, true) == 0
                        && BlueEmulation.convert(182f, 2, 0, true) == 1,
                "cyan hatch mode has two contrast levels");

        System.out.println("SelfTest OK");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
