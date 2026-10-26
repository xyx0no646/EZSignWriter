package dev.ezsign.writer;

import android.graphics.Bitmap;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;

import java.io.IOException;

final class EzSignNfc {
    interface Listener {
        void onStatus(String text);
        void onProgress(int percent);
        void onDevice(DeviceInfo info, Bitmap preview);
    }

    interface SourceProvider {
        Bitmap create(DeviceInfo info) throws Exception;
    }

    private EzSignNfc() {}

    static void write(Tag tag, SourceProvider sourceProvider,
                      boolean mirrorCorrection, Listener listener)
            throws Exception {
        IsoDep iso = IsoDep.get(tag);
        if (iso == null) {
            throw new IOException("IsoDepではないNFCタグです");
        }

        try {
            listener.onStatus("CONNECT");
            iso.connect();
            iso.setTimeout(5000);

            byte[] select = tx(iso, Protocol.SELECT_AID);
            if (!Protocol.isOk(select)) {
                listener.onStatus(
                        "SELECT " + Protocol.sw(select) + " (continue)");
            }

            listener.onStatus("AUTH");
            expectOk(tx(iso, Protocol.AUTH), "AUTH");

            listener.onStatus("READ DEVICE");
            DeviceInfo info =
                    DeviceInfo.parse(tx(iso, Protocol.DEVICE_INFO));
            listener.onStatus(info.summary());

            Bitmap source = sourceProvider.create(info);
            if (source == null) {
                throw new IOException("画像の切り出しに失敗しました");
            }

            ImagePipeline.Result image =
                    ImagePipeline.prepare(source, info, mirrorCorrection);
            listener.onDevice(info, image.preview);

            writeRaw(iso, image.bytes, listener);

            listener.onStatus("REFRESH — KEEP TOUCHING");
            listener.onProgress(100);
            iso.setTimeout(60000);

            try {
                expectOk(tx(iso, Protocol.REFRESH), "REFRESH");
            } catch (TagLostException lost) {
                throw new IOException(
                        "更新待ち中にタグが離れました。"
                                + "画面側は更新を続けている場合があります。",
                        lost);
            }

            listener.onStatus("DONE");
        } finally {
            try {
                if (iso.isConnected()) iso.close();
            } catch (Exception ignored) {}
        }
    }

    private static void writeRaw(
            IsoDep iso, byte[] data, Listener listener)
            throws IOException {
        final int chunkSize = 250;
        int chunks = (data.length + chunkSize - 1) / chunkSize;

        if (chunks > 256) {
            throw new IOException(
                    "Raw transfer exceeds one-byte sequence space: "
                            + chunks);
        }

        listener.onStatus(
                "WRITE " + data.length + " bytes / "
                        + chunks + " packets");

        for (int seq = 0; seq < chunks; seq++) {
            int start = seq * chunkSize;
            int len = Math.min(chunkSize, data.length - start);

            byte[] response = tx(
                    iso,
                    Protocol.rawChunk(
                            0, seq, data, start, len));

            if (!Protocol.isOk(response)) {
                throw new IOException(
                        "WRITE packet " + seq
                                + " failed: SW="
                                + Protocol.sw(response));
            }

            listener.onProgress(
                    (seq + 1) * 95 / chunks);
        }
    }

    private static byte[] tx(IsoDep iso, byte[] apdu)
            throws IOException {
        return iso.transceive(apdu);
    }

    private static void expectOk(
            byte[] response, String what)
            throws IOException {
        if (!Protocol.isOk(response)) {
            throw new IOException(
                    what + " failed: SW="
                            + Protocol.sw(response));
        }
    }
}
