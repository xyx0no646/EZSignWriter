package dev.ezsign.writer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGE = 7;

    private static final String PREFS = "ezsign";
    private static final String PREF_LARGE = "preview_large";
    private static final String PREF_COLORS = "preview_colors";

    private NfcAdapter nfc;
    private PendingIntent nfcIntent;

    private Bitmap sourceBitmap;
    private Bitmap processedPreview;

    private volatile boolean armed;
    private volatile boolean busy;

    private boolean previewLarge;
    private int previewColors = 4;

    private CropImageView cropView;
    private TextView status;
    private Button pickButton;
    private Button writeButton;
    private Button previewSizeButton;
    private Button resetButton;
    private CheckBox mirrorCheck;
    private ProgressBar progress;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor();
    private final Handler ui =
            new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        SharedPreferences prefs =
                getSharedPreferences(PREFS, MODE_PRIVATE);
        previewLarge =
                prefs.getBoolean(PREF_LARGE, false);
        previewColors =
                prefs.getInt(PREF_COLORS, 4);

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        buildUi();

        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            status.setText("NO NFC HARDWARE");
            writeButton.setEnabled(false);
            return;
        }

        Intent intent =
                new Intent(this, getClass())
                        .addFlags(
                                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        nfcIntent =
                PendingIntent.getActivity(
                        this, 0, intent, flags);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(
                Color.rgb(238, 238, 234));

        TextView title = new TextView(this);
        title.setText("EZ SIGN WRITER");
        title.setTextColor(Color.BLACK);
        title.setTextSize(24);
        title.setTypeface(
                Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(
                title,
                lp(-1, -2, 0));

        TextView sub = new TextView(this);
        sub.setText(
                "PINCH / DRAG · DOUBLE TAP RESET");
        sub.setTextColor(Color.DKGRAY);
        sub.setTextSize(12);
        sub.setTypeface(Typeface.MONOSPACE);
        root.addView(
                sub,
                margins(
                        lp(-1, -2, 0),
                        0, dp(4), 0, dp(14)));

        cropView = new CropImageView(this);
        root.addView(
                cropView,
                lp(-1, 0, 1));

        progress =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr
                                .progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        root.addView(
                progress,
                margins(
                        lp(-1, dp(8), 0),
                        0, dp(14), 0, dp(10)));

        status = new TextView(this);
        status.setText("SELECT IMAGE");
        status.setTextColor(Color.BLACK);
        status.setTextSize(14);
        status.setMinHeight(dp(44));
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setTypeface(
                Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(
                status,
                lp(-1, -2, 0));

        LinearLayout options =
                new LinearLayout(this);
        options.setOrientation(
                LinearLayout.HORIZONTAL);

        previewSizeButton =
                makeButton("");
        updatePreviewButtonText();
        options.addView(
                previewSizeButton,
                lp(0, dp(44), 1));

        resetButton =
                makeButton("RESET");
        options.addView(
                space(dp(8)),
                lp(dp(8), 1, 0));
        options.addView(
                resetButton,
                lp(0, dp(44), 1));

        mirrorCheck =
                new CheckBox(this);
        mirrorCheck.setText("MIRROR FIX");
        mirrorCheck.setTextColor(Color.BLACK);
        mirrorCheck.setTextSize(12);
        mirrorCheck.setTypeface(
                Typeface.MONOSPACE,
                Typeface.BOLD);
        mirrorCheck.setChecked(true);
        options.addView(
                mirrorCheck,
                lp(0, dp(44), 1));

        root.addView(
                options,
                margins(
                        lp(-1, -2, 0),
                        0, 0, 0, dp(6)));

        LinearLayout buttons =
                new LinearLayout(this);
        buttons.setOrientation(
                LinearLayout.HORIZONTAL);

        pickButton =
                makeButton("SELECT IMAGE");
        writeButton =
                makeButton("WRITE NFC");
        writeButton.setEnabled(false);

        buttons.addView(
                pickButton,
                lp(0, dp(56), 1));
        buttons.addView(
                space(dp(10)),
                lp(dp(10), 1, 0));
        buttons.addView(
                writeButton,
                lp(0, dp(56), 1));

        root.addView(
                buttons,
                margins(
                        lp(-1, -2, 0),
                        0, dp(4), 0, 0));

        pickButton.setOnClickListener(
                v -> chooseImage());

        previewSizeButton.setOnClickListener(
                v -> togglePreviewSize());

        resetButton.setOnClickListener(
                v -> {
                    if (!busy) {
                        cropView.resetTransform();
                        status.setText(
                                "RESET · PINCH / DRAG");
                    }
                });

        writeButton.setOnClickListener(
                v -> toggleArm());

        setContentView(root);
        applyPreviewSize();
    }

    private void togglePreviewSize() {
        if (busy || armed) return;

        previewLarge = !previewLarge;
        savePreviewPrefs();

        applyPreviewSize();

        if (sourceBitmap != null) {
            status.setText(
                    previewLarge
                            ? "4.2\" FRAME · PINCH / DRAG"
                            : "2.9\" FRAME · PINCH / DRAG");
        }
    }

    private void applyPreviewSize() {
        int w =
                previewLarge ? 400 : 296;
        int h =
                previewLarge ? 300 : 128;

        cropView.setTargetSize(w, h);
        updatePreviewButtonText();
    }

    private void updatePreviewButtonText() {
        if (previewSizeButton == null) return;

        previewSizeButton.setText(
                previewLarge
                        ? "PREVIEW 4.2\""
                        : "PREVIEW 2.9\"");
    }

    private void savePreviewPrefs() {
        getSharedPreferences(
                PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(
                        PREF_LARGE, previewLarge)
                .putInt(
                        PREF_COLORS, previewColors)
                .apply();
    }

    private Button makeButton(String text) {
        Button button =
                new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTypeface(
                Typeface.MONOSPACE,
                Typeface.BOLD);
        button.setAllCaps(false);
        return button;
    }

    private View space(int px) {
        View view =
                new View(this);
        view.setMinimumWidth(px);
        return view;
    }

    private void chooseImage() {
        if (busy || armed) return;

        Intent intent =
                new Intent(
                        Intent.ACTION_GET_CONTENT);

        intent.setType("image/*");
        intent.addCategory(
                Intent.CATEGORY_OPENABLE);

        startActivityForResult(
                Intent.createChooser(
                        intent, "Select image"),
                PICK_IMAGE);
    }

    @Override protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        super.onActivityResult(
                requestCode, resultCode, data);

        if (requestCode != PICK_IMAGE
                || resultCode != RESULT_OK
                || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            Bitmap decoded =
                    decodeSampled(uri, 2048);

            if (decoded == null) {
                throw new IOException(
                        "Image decode failed");
            }

            if (sourceBitmap != null) {
                sourceBitmap.recycle();
            }

            sourceBitmap = decoded;
            cropView.setImageBitmap(
                    sourceBitmap);

            progress.setProgress(0);
            writeButton.setEnabled(true);

            status.setText(
                    "PINCH / DRAG · DOUBLE TAP RESET");
        } catch (Exception e) {
            status.setText(
                    "IMAGE ERROR: "
                            + compact(e.getMessage()));
        }
    }

    private Bitmap decodeSampled(
            Uri uri, int maxSide)
            throws Exception {
        BitmapFactory.Options bounds =
                new BitmapFactory.Options();

        bounds.inJustDecodeBounds = true;

        try (InputStream in =
                     getContentResolver()
                             .openInputStream(uri)) {
            BitmapFactory.decodeStream(
                    in, null, bounds);
        }

        if (bounds.outWidth <= 0
                || bounds.outHeight <= 0) {
            return null;
        }

        int sample = 1;

        while (Math.max(
                bounds.outWidth / sample,
                bounds.outHeight / sample)
                > maxSide) {
            sample *= 2;
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();

        options.inSampleSize = sample;
        options.inPreferredConfig =
                Bitmap.Config.ARGB_8888;

        try (InputStream in =
                     getContentResolver()
                             .openInputStream(uri)) {
            return BitmapFactory.decodeStream(
                    in, null, options);
        }
    }

    private void toggleArm() {
        if (!cropView.hasImage()
                || busy) {
            return;
        }

        armed = !armed;
        setEditorEnabled(!armed);

        if (armed) {
            status.setText(
                    "ARMED — TOUCH EZ SIGN TO PHONE");
            writeButton.setText("CANCEL");
            enableForegroundNfc();
        } else {
            status.setText(
                    "READY — ADJUST OR WRITE");
            writeButton.setText("WRITE NFC");
            disableForegroundNfc();
        }
    }

    private void setEditorEnabled(
            boolean enabled) {
        pickButton.setEnabled(enabled);
        previewSizeButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        mirrorCheck.setEnabled(enabled);
        cropView.setEnabled(enabled);
    }

    @Override protected void onResume() {
        super.onResume();

        if (armed && !busy) {
            enableForegroundNfc();
        }

        if (nfc != null
                && !nfc.isEnabled()) {
            status.setText(
                    "NFC IS OFF");
        }
    }

    @Override protected void onPause() {
        disableForegroundNfc();
        super.onPause();
    }

    private void enableForegroundNfc() {
        if (nfc == null
                || nfcIntent == null
                || !nfc.isEnabled()) {
            status.setText(
                    "NFC IS OFF — ENABLE NFC");
            return;
        }

        nfc.enableForegroundDispatch(
                this,
                nfcIntent,
                null,
                null);
    }

    private void disableForegroundNfc() {
        if (nfc != null) {
            try {
                nfc.disableForegroundDispatch(this);
            } catch (Exception ignored) {}
        }
    }

    @Override protected void onNewIntent(
            Intent intent) {
        super.onNewIntent(intent);

        boolean tagAction =
                NfcAdapter.ACTION_TAG_DISCOVERED
                        .equals(intent.getAction())
                || NfcAdapter.ACTION_TECH_DISCOVERED
                        .equals(intent.getAction())
                || NfcAdapter.ACTION_NDEF_DISCOVERED
                        .equals(intent.getAction());

        if (!armed
                || busy
                || !tagAction) {
            return;
        }

        Tag tag =
                intent.getParcelableExtra(
                        NfcAdapter.EXTRA_TAG);

        if (tag == null) return;

        startWrite(tag);
    }

    private void startWrite(Tag tag) {
        final boolean mirrorCorrection =
                mirrorCheck.isChecked();

        busy = true;
        armed = false;

        disableForegroundNfc();

        setEditorEnabled(false);
        writeButton.setEnabled(false);
        writeButton.setText("WRITING");
        status.setText("NFC DETECTED");
        progress.setProgress(1);

        worker.execute(() -> {
            try {
                EzSignNfc.write(
                        tag,
                        this::createCropForDevice,
                        mirrorCorrection,
                        new EzSignNfc.Listener() {
                    @Override public void onStatus(
                            String text) {
                        ui.post(
                                () -> status.setText(text));
                    }

                    @Override public void onProgress(
                            int percent) {
                        ui.post(
                                () -> progress
                                        .setProgress(percent));
                    }

                    @Override public void onDevice(
                            DeviceInfo info,
                            Bitmap finalPreview) {
                        ui.post(() -> {
                            previewColors =
                                    info.colors;

                            if (processedPreview != null
                                    && processedPreview
                                    != finalPreview) {
                                processedPreview.recycle();
                            }

                            processedPreview =
                                    finalPreview;

                            status.setText(
                                    info.summary()
                                            + (mirrorCorrection
                                            ? " · MIRROR FIX"
                                            : "")
                                            + " — WRITE");
                        });
                    }
                });

                ui.post(
                        () -> finishWrite(
                                true,
                                "DONE — REMOVE TAG"));
            } catch (Exception e) {
                ui.post(
                        () -> finishWrite(
                                false,
                                "ERROR — "
                                        + compact(
                                        e.getMessage())));
            }
        });
    }

    private Bitmap createCropForDevice(
            DeviceInfo info)
            throws Exception {
        int selectedW =
                previewLarge ? 400 : 296;
        int selectedH =
                previewLarge ? 300 : 128;

        if (selectedW != info.width
                || selectedH != info.height) {
            previewLarge =
                    info.width >= 400;
            previewColors =
                    info.colors;

            ui.post(() -> {
                savePreviewPrefs();
                applyPreviewSize();

                status.setText(
                        "TAG IS "
                                + info.width
                                + "×"
                                + info.height
                                + " — ADJUST FRAME & TRY AGAIN");
            });

            throw new IOException(
                    "タグサイズがプレビューと違います。"
                            + "枠を実機サイズへ切り替えました。"
                            + "位置を確認してもう一度書き込んでください。");
        }

        previewColors =
                info.colors;
        savePreviewPrefs();

        FutureTask<Bitmap> task =
                new FutureTask<>(
                        () -> cropView
                                .renderCroppedBitmap(
                                        info.width,
                                        info.height));

        ui.post(task);

        Bitmap crop =
                task.get();

        if (crop == null) {
            throw new IOException(
                    "Crop render failed");
        }

        return crop;
    }

    private void finishWrite(
            boolean ok,
            String message) {
        busy = false;

        setEditorEnabled(true);

        writeButton.setEnabled(
                cropView.hasImage());
        writeButton.setText("WRITE NFC");
        status.setText(message);

        if (!ok) {
            progress.setProgress(0);
        }
    }

    private static String compact(
            String text) {
        if (text == null) {
            return "UNKNOWN";
        }

        return text.length() > 150
                ? text.substring(0, 150)
                : text;
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();

        if (sourceBitmap != null) {
            sourceBitmap.recycle();
        }

        if (processedPreview != null
                && processedPreview
                != sourceBitmap) {
            processedPreview.recycle();
        }

        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density);
    }

    private LinearLayout.LayoutParams lp(
            int width,
            int height,
            float weight) {
        return new LinearLayout.LayoutParams(
                width,
                height,
                weight);
    }

    private LinearLayout.LayoutParams margins(
            LinearLayout.LayoutParams params,
            int left,
            int top,
            int right,
            int bottom) {
        params.setMargins(
                left, top, right, bottom);
        return params;
    }
}
