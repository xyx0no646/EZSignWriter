package dev.ezsign.writer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
    private static final String PREF_COLOR_MODE = "color_mode";
    private static final String PREF_DISTANCE = "distance_mode";
    private static final String PREF_DITHER = "dither_mode";

    private NfcAdapter nfc;
    private PendingIntent nfcIntent;

    private Bitmap sourceBitmap;
    private Bitmap processedPreview;

    private volatile boolean armed;
    private volatile boolean busy;

    private boolean previewLarge;
    private int previewColors = 4;
    private ColorMode colorMode = ColorMode.STANDARD;
    private DistanceMode distanceMode = DistanceMode.RGB;
    private DitherMode ditherMode = DitherMode.ATKINSON;

    private CropImageView cropView;
    private TextView status;
    private Button pickButton;
    private Button writeButton;
    private Button previewSizeButton;
    private Button resetButton;
    private Button outputPreviewButton;
    private CheckBox mirrorCheck;
    private Spinner colorModeSpinner;
    private Spinner distanceSpinner;
    private Spinner ditherSpinner;
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
        colorMode = ColorMode.fromOrdinal(
                prefs.getInt(PREF_COLOR_MODE, 0));
        distanceMode = DistanceMode.fromOrdinal(
                prefs.getInt(PREF_DISTANCE, 0));
        ditherMode = DitherMode.fromOrdinal(
                prefs.getInt(PREF_DITHER, 0));

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        buildUi();

        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            status.setText("NFCに対応していません");
            writeButton.setEnabled(false);
            handleSharedImageIntent(getIntent());
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

        handleSharedImageIntent(getIntent());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(
                Color.rgb(238, 238, 234));

        TextView title = new TextView(this);
        title.setText("EZ Sign ライター");
        title.setTextColor(Color.BLACK);
        title.setTextSize(24);
        title.setTypeface(
                Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(
                title,
                lp(-1, -2, 0));

        TextView sub = new TextView(this);
        sub.setText(
                "ピンチで拡大縮小・ドラッグで移動・ダブルタップでリセット");
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
        status.setText("画像を選択");
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
                makeButton("リセット");
        options.addView(
                space(dp(8)),
                lp(dp(8), 1, 0));
        options.addView(
                resetButton,
                lp(0, dp(44), 1));

        outputPreviewButton =
                makeButton("仕上がり");
        options.addView(
                space(dp(8)),
                lp(dp(8), 1, 0));
        options.addView(
                outputPreviewButton,
                lp(0, dp(44), 1));

        mirrorCheck =
                new CheckBox(this);
        mirrorCheck.setText("左右反転補正");
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

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("色変換モード");
        modeLabel.setTextSize(13);
        modeLabel.setTextColor(Color.BLACK);
        modeRow.addView(modeLabel, lp(-2, -2, 0));

        colorModeSpinner = makeChoiceSpinner(
                ColorMode.values(), colorMode.ordinal());
        modeRow.addView(space(dp(8)), lp(dp(8), 1, 0));
        modeRow.addView(spinnerField(colorModeSpinner), lp(0, dp(44), 1));
        root.addView(modeRow, margins(lp(-1, -2, 0), 0, 0, 0, dp(5)));

        colorModeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                ColorMode selected = ColorMode.fromOrdinal(position);
                if (colorMode == selected) return;
                colorMode = selected;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_COLOR_MODE, selected.ordinal()).apply();
                cropView.clearOutputPreview();
                outputPreviewButton.setText("仕上がり");
                if (cropView.hasImage()) {
                    status.setText("色変換モード: " + selected.label);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout processRow = new LinearLayout(this);
        processRow.setOrientation(LinearLayout.HORIZONTAL);
        processRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView distanceLabel = new TextView(this);
        distanceLabel.setText("色差");
        distanceLabel.setTextSize(13);
        distanceLabel.setTextColor(Color.BLACK);
        processRow.addView(distanceLabel, lp(-2, -2, 0));
        processRow.addView(space(dp(7)), lp(dp(7), 1, 0));

        distanceSpinner = makeChoiceSpinner(
                DistanceMode.values(), distanceMode.ordinal());
        processRow.addView(spinnerField(distanceSpinner), lp(0, dp(44), 1));
        processRow.addView(space(dp(12)), lp(dp(12), 1, 0));

        TextView ditherLabel = new TextView(this);
        ditherLabel.setText("ディザ");
        ditherLabel.setTextSize(13);
        ditherLabel.setTextColor(Color.BLACK);
        processRow.addView(ditherLabel, lp(-2, -2, 0));
        processRow.addView(space(dp(7)), lp(dp(7), 1, 0));

        ditherSpinner = makeChoiceSpinner(
                DitherMode.values(), ditherMode.ordinal());
        processRow.addView(spinnerField(ditherSpinner), lp(0, dp(44), 1));
        root.addView(processRow, margins(lp(-1, -2, 0), 0, 0, 0, dp(6)));

        distanceSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                DistanceMode selected = DistanceMode.fromOrdinal(position);
                if (distanceMode == selected) return;
                distanceMode = selected;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_DISTANCE, selected.ordinal()).apply();
                resetProcessingPreview("色差: " + selected.label);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        ditherSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                DitherMode selected = DitherMode.fromOrdinal(position);
                if (ditherMode == selected) return;
                ditherMode = selected;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(PREF_DITHER, selected.ordinal()).apply();
                resetProcessingPreview("ディザ: " + selected.label);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout buttons =
                new LinearLayout(this);
        buttons.setOrientation(
                LinearLayout.HORIZONTAL);

        pickButton =
                makeButton("画像を選択");
        writeButton =
                makeButton("NFCに書き込む");
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
                        cropView.clearOutputPreview();
                        cropView.resetTransform();
                        outputPreviewButton.setText("仕上がり");
                        status.setText(
                                "リセット · PINCH / DRAG");
                    }
                });

        outputPreviewButton.setOnClickListener(
                v -> toggleOutputPreview());

        writeButton.setOnClickListener(
                v -> toggleArm());

        setContentView(root);
        applyPreviewSize();
    }

    private void toggleOutputPreview() {
        if (busy || armed || !cropView.hasImage()) {
            return;
        }

        if (cropView.isShowingOutputPreview()) {
            cropView.clearOutputPreview();
            outputPreviewButton.setText("仕上がり");
            status.setText("編集 · PINCH / DRAG");
            return;
        }

        outputPreviewButton.setEnabled(false);
        colorModeSpinner.setEnabled(false);
        distanceSpinner.setEnabled(false);
        ditherSpinner.setEnabled(false);
        final ColorMode selectedMode = colorMode;
        final DistanceMode selectedDistance = distanceMode;
        final DitherMode selectedDither = ditherMode;
        int w = previewLarge ? 400 : 296;
        int h = previewLarge ? 300 : 128;
        int colors = previewColors == 2 ? 2 : 4;
        status.setText(
                "変換中 " + w + "×" + h
                        + " · " + colors + "色");

        FutureTask<Bitmap> cropTask =
                new FutureTask<>(
                        () -> cropView
                                .renderCroppedBitmap(
                                        w, h));

        ui.post(cropTask);

        worker.execute(() -> {
            try {
                Bitmap crop = cropTask.get();
                if (crop == null) {
                    throw new IOException(
                            "Crop render failed");
                }

                Bitmap rendered =
                        ImagePipeline.preview(
                                crop,
                                w,
                                h,
                                colors,
                                selectedMode,
                                selectedDistance,
                                selectedDither);

                if (crop != rendered) {
                    crop.recycle();
                }

                ui.post(() -> {
                    if (processedPreview != null
                            && processedPreview
                            != rendered
                            && processedPreview
                            != sourceBitmap) {
                        processedPreview.recycle();
                    }

                    processedPreview = rendered;
                    cropView.setOutputPreview(rendered);
                    outputPreviewButton.setText("編集");
                    outputPreviewButton.setEnabled(true);
                    colorModeSpinner.setEnabled(true);
                    distanceSpinner.setEnabled(true);
                    ditherSpinner.setEnabled(true);
                    status.setText(
                            "仕上がり PREVIEW · "
                                    + colors
                                    + "色");
                });
            } catch (Exception e) {
                ui.post(() -> {
                    outputPreviewButton.setEnabled(true);
                    colorModeSpinner.setEnabled(true);
                    distanceSpinner.setEnabled(true);
                    ditherSpinner.setEnabled(true);
                    status.setText(
                            "プレビューエラー: "
                                    + compact(
                                    e.getMessage()));
                });
            }
        });
    }

    private void togglePreviewSize() {
        if (busy || armed) return;

        previewLarge = !previewLarge;
        savePreviewPrefs();

        cropView.clearOutputPreview();
        outputPreviewButton.setText("仕上がり");
        applyPreviewSize();

        if (sourceBitmap != null) {
            status.setText(
                    previewLarge
                            ? "4.2インチ枠 · ピンチ/ドラッグで調整"
                            : "2.9インチ枠 · ピンチ/ドラッグで調整");
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
                        ? "4.2インチ"
                        : "2.9インチ");
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

    /** Light gray selection field with a visible dropdown indicator. */
    private <T> Spinner makeChoiceSpinner(T[] values, int selected) {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<T> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected, false);
        spinner.setBackground(roundedFill(Color.rgb(228, 229, 230)));
        spinner.setPopupBackgroundDrawable(
                roundedFill(Color.rgb(239, 240, 241)));
        spinner.setPadding(dp(10), 0, dp(25), 0);
        return spinner;
    }

    private GradientDrawable roundedFill(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), Color.rgb(195, 198, 200));
        return bg;
    }

    private View spinnerField(Spinner spinner) {
        FrameLayout frame = new FrameLayout(this);
        frame.addView(spinner, new FrameLayout.LayoutParams(-1, -1));

        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextSize(17);
        arrow.setTextColor(Color.DKGRAY);
        arrow.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                dp(27), -1, Gravity.END | Gravity.CENTER_VERTICAL);
        frame.addView(arrow, p);
        arrow.setOnClickListener(v -> {
            if (spinner.isEnabled()) spinner.performClick();
        });
        return frame;
    }

    private void resetProcessingPreview(String message) {
        cropView.clearOutputPreview();
        outputPreviewButton.setText("仕上がり");
        if (cropView.hasImage()) status.setText(message);
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
                        intent, "画像を選択"),
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
        loadImage(uri);
    }

    private void handleSharedImageIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        if (busy) {
            status.setText("書き込み中は共有画像を受け取れません");
            return;
        }
        if (intent.getType() == null || !intent.getType().startsWith("image/")) {
            status.setText("画像ファイルを共有してください");
            return;
        }
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null && intent.getClipData() != null
                && intent.getClipData().getItemCount() > 0) {
            uri = intent.getClipData().getItemAt(0).getUri();
        }
        if (uri == null) {
            status.setText("共有された画像を取得できませんでした");
            return;
        }

        if (armed) {
            armed = false;
            disableForegroundNfc();
            setEditorEnabled(true);
            writeButton.setText("NFCに書き込む");
        }
        loadImage(uri);
    }

    private void loadImage(Uri uri) {
        try {
            Bitmap decoded =
                    decodeSampled(uri, 2048);

            if (decoded == null) {
                throw new IOException(
                        "画像を読み込めませんでした");
            }

            if (sourceBitmap != null) {
                sourceBitmap.recycle();
            }

            sourceBitmap = decoded;
            cropView.setImageBitmap(
                    sourceBitmap);
            outputPreviewButton.setText("仕上がり");

            progress.setProgress(0);
            writeButton.setEnabled(nfc != null);

            status.setText(
                    "ピンチで拡大縮小・ドラッグで移動・ダブルタップでリセット");
        } catch (Exception e) {
            status.setText(
                    "画像エラー: "
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
                    "書き込み待機中 — EZ Signをスマホにかざしてください");
            writeButton.setText("キャンセル");
            enableForegroundNfc();
        } else {
            status.setText(
                    "位置を調整するか、書き込みを開始してください");
            writeButton.setText("NFCに書き込む");
            disableForegroundNfc();
        }
    }

    private void setEditorEnabled(
            boolean enabled) {
        pickButton.setEnabled(enabled);
        previewSizeButton.setEnabled(enabled);
        resetButton.setEnabled(enabled);
        outputPreviewButton.setEnabled(enabled);
        colorModeSpinner.setEnabled(enabled);
        distanceSpinner.setEnabled(enabled);
        ditherSpinner.setEnabled(enabled);
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
                    "NFCがオフです");
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
                    "NFCがオフです — ENABLE NFC");
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

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            setIntent(intent);
            handleSharedImageIntent(intent);
            return;
        }

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
        final ColorMode selectedMode = colorMode;
        final DistanceMode selectedDistance = distanceMode;
        final DitherMode selectedDither = ditherMode;

        cropView.clearOutputPreview();
        outputPreviewButton.setText("仕上がり");

        busy = true;
        armed = false;

        disableForegroundNfc();

        setEditorEnabled(false);
        writeButton.setEnabled(false);
        writeButton.setText("書き込み中");
        status.setText("NFCを検出しました");
        progress.setProgress(1);

        worker.execute(() -> {
            try {
                EzSignNfc.write(
                        tag,
                        this::createCropForDevice,
                        mirrorCorrection,
                        selectedMode,
                        selectedDistance,
                        selectedDither,
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
                                            ? " · 左右反転補正"
                                            : "")
                                            + " — WRITE");
                        });
                    }
                });

                ui.post(
                        () -> finishWrite(
                                true,
                                "完了 — EZ Signを離してください"));
            } catch (Exception e) {
                ui.post(
                        () -> finishWrite(
                                false,
                                "エラー — "
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
                        "タグは "
                                + info.width
                                + "×"
                                + info.height
                                + " です — 枠を調整してもう一度書き込んでください");
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
        outputPreviewButton.setText("仕上がり");

        writeButton.setEnabled(
                cropView.hasImage());
        writeButton.setText("NFCに書き込む");
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
