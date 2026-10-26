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
            listener.onStatus("接続中");
            iso.connect();
            iso.setTimeout(5000);

            byte[] select = tx(iso, Protocol.SELECT_AID);
            if (!Protocol.isOk(select)) {
                listener.onStatus(
                        "初期化応答 " + Protocol.sw(select) + "（続行）");
            }

            listener.onStatus("認証中");
            expectOk(tx(iso, Protocol.AUTH), "認証");

            listener.onStatus("機種情報を読み取り中");
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

            listener.onStatus("画面更新中 — そのままかざしてください");
            listener.onProgress(100);
            iso.setTimeout(60000);

            try {
                expectOk(tx(iso, Protocol.REFRESH), "画面更新");
            } catch (TagLostException lost) {
                throw new IOException(
                        "更新待ち中にタグが離れました。"
                                + "画面側は更新を続けている場合があります。",
                        lost);
            }

            listener.onStatus("完了");
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
                    "送信パケット数が上限を超えました: "
                            + chunks);
        }

        listener.onStatus(
                "書き込み中 " + data.length + " バイト / "
                        + chunks + " パケット");

        for (int seq = 0; seq < chunks; seq++) {
            int start = seq * chunkSize;
            int len = Math.min(chunkSize, data.length - start);

            byte[] response = tx(
                    iso,
                    Protocol.rawChunk(
                            0, seq, data, start, len));

            if (!Protocol.isOk(response)) {
                throw new IOException(
                        "書き込みパケット " + seq
                                + " で失敗: SW="
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
                    what + "に失敗しました: SW="
                            + Protocol.sw(response));
        }
    }
}
