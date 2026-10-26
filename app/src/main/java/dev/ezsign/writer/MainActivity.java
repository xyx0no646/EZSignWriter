package dev.ezsign.writer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGE = 7;

    private NfcAdapter nfc;
    private PendingIntent nfcIntent;
    private Bitmap sourceBitmap;
    private Bitmap processedPreview;
    private volatile boolean armed;
    private volatile boolean busy;

    private ImageView preview;
    private TextView status;
    private Button pickButton;
    private Button writeButton;
    private CheckBox mirrorCheck;
    private ProgressBar progress;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildUi();

        nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            status.setText("NO NFC HARDWARE");
            writeButton.setEnabled(false);
            return;
        }
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        nfcIntent = PendingIntent.getActivity(this, 0, intent, flags);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(238, 238, 234));

        TextView title = new TextView(this);
        title.setText("EZ SIGN WRITER");
        title.setTextColor(Color.BLACK);
        title.setTextSize(24);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(title, lp(-1, -2, 0));

        TextView sub = new TextView(this);
        sub.setText("NO CLOUD · NO ACCOUNT · NFC ONLY");
        sub.setTextColor(Color.DKGRAY);
        sub.setTextSize(12);
        sub.setTypeface(Typeface.MONOSPACE);
        root.addView(sub, margins(lp(-1, -2, 0), 0, dp(4), 0, dp(14)));

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.WHITE);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        root.addView(frame, lp(-1, 0, 1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        root.addView(progress, margins(lp(-1, dp(8), 0), 0, dp(14), 0, dp(10)));

        status = new TextView(this);
        status.setText("SELECT IMAGE");
        status.setTextColor(Color.BLACK);
        status.setTextSize(14);
        status.setMinHeight(dp(42));
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(status, lp(-1, -2, 0));

        mirrorCheck = new CheckBox(this);
        mirrorCheck.setText("MIRROR FIX");
        mirrorCheck.setTextColor(Color.BLACK);
        mirrorCheck.setTextSize(13);
        mirrorCheck.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mirrorCheck.setChecked(true);
        root.addView(mirrorCheck, margins(lp(-1, dp(44), 0), 0, 0, 0, dp(6)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        pickButton = makeButton("SELECT IMAGE");
        writeButton = makeButton("WRITE NFC");
        writeButton.setEnabled(false);
        buttons.addView(pickButton, lp(0, dp(56), 1));
        buttons.addView(space(dp(10)), lp(dp(10), 1, 0));
        buttons.addView(writeButton, lp(0, dp(56), 1));
        root.addView(buttons, margins(lp(-1, -2, 0), 0, dp(4), 0, 0));

        pickButton.setOnClickListener(v -> chooseImage());
        writeButton.setOnClickListener(v -> toggleArm());
        setContentView(root);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setAllCaps(false);
        return b;
    }

    private View space(int px) { View v = new View(this); v.setMinimumWidth(px); return v; }

    private void chooseImage() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "Select image"), PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            Bitmap b = decodeSampled(uri, 2048);
            if (b == null) throw new IllegalArgumentException("Image decode failed");
            if (sourceBitmap != null) sourceBitmap.recycle();
            sourceBitmap = b;
            preview.setImageBitmap(sourceBitmap);
            status.setText("READY — PRESS WRITE NFC");
            writeButton.setEnabled(true);
            progress.setProgress(0);
        } catch (Exception e) {
            status.setText("IMAGE ERROR: " + e.getMessage());
        }
    }

    private Bitmap decodeSampled(Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxSide) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    private void toggleArm() {
        if (sourceBitmap == null || busy) return;
        armed = !armed;
        pickButton.setEnabled(!armed);
        mirrorCheck.setEnabled(!armed);
        if (armed) {
            status.setText("ARMED — TOUCH EZ SIGN TO PHONE");
            writeButton.setText("CANCEL");
            enableForegroundNfc();
        } else {
            status.setText("READY — PRESS WRITE NFC");
            writeButton.setText("WRITE NFC");
            disableForegroundNfc();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (armed && !busy) enableForegroundNfc();
        if (nfc != null && !nfc.isEnabled()) status.setText("NFC IS OFF");
    }

    @Override protected void onPause() {
        disableForegroundNfc();
        super.onPause();
    }

    private void enableForegroundNfc() {
        if (nfc == null || nfcIntent == null || !nfc.isEnabled()) {
            status.setText("NFC IS OFF — ENABLE NFC");
            return;
        }
        nfc.enableForegroundDispatch(this, nfcIntent, null, null);
    }

    private void disableForegroundNfc() {
        if (nfc != null) {
            try { nfc.disableForegroundDispatch(this); } catch (Exception ignored) {}
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!armed || busy || !NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) &&
                !NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()) &&
                !NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) return;
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) return;
        startWrite(tag);
    }

    private void startWrite(Tag tag) {
        final boolean mirrorCorrection = mirrorCheck.isChecked();

        busy = true;
        armed = false;
        disableForegroundNfc();
        pickButton.setEnabled(false);
        mirrorCheck.setEnabled(false);
        writeButton.setEnabled(false);
        writeButton.setText("WRITING");
        status.setText("NFC DETECTED");
        progress.setProgress(1);

        worker.execute(() -> {
            try {
                EzSignNfc.write(tag, sourceBitmap, mirrorCorrection, new EzSignNfc.Listener() {
                    @Override public void onStatus(String text) { ui.post(() -> status.setText(text)); }
                    @Override public void onProgress(int percent) { ui.post(() -> progress.setProgress(percent)); }
                    @Override public void onDevice(DeviceInfo info, Bitmap p) {
                        ui.post(() -> {
                            if (processedPreview != null && processedPreview != p) processedPreview.recycle();
                            processedPreview = p;
                            preview.setImageBitmap(p);
                            status.setText(info.summary() + (mirrorCorrection ? " · MIRROR FIX" : "") + " — WRITE");
                        });
                    }
                });
                ui.post(() -> finishWrite(true, "DONE — REMOVE TAG"));
            } catch (Exception e) {
                ui.post(() -> finishWrite(false, "ERROR — " + compact(e.getMessage())));
            }
        });
    }

    private void finishWrite(boolean ok, String message) {
        busy = false;
        pickButton.setEnabled(true);
        mirrorCheck.setEnabled(true);
        writeButton.setEnabled(sourceBitmap != null);
        writeButton.setText("WRITE NFC");
        status.setText(message);
        if (!ok) progress.setProgress(0);
    }

    private static String compact(String s) {
        if (s == null) return "UNKNOWN";
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        if (sourceBitmap != null) sourceBitmap.recycle();
        if (processedPreview != null && processedPreview != sourceBitmap) processedPreview.recycle();
        super.onDestroy();
    }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }
    private LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(l, t, r, b); return p;
    }
}
