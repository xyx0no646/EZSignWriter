package dev.ezsign.writer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public final class CropImageView extends View {
    private static final float MIN_USER_SCALE = 1.0f;
    private static final float MAX_USER_SCALE = 8.0f;

    private final Paint bitmapPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint helpPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cropRect = new RectF();

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private Bitmap bitmap;
    private int targetW = 296;
    private int targetH = 128;
    private int viewW;
    private int viewH;

    private float sourceCenterX;
    private float sourceCenterY;
    private float userScale = 1f;

    private boolean dragging;
    private float lastX;
    private float lastY;

    public CropImageView(Context context) {
        this(context, null);
    }

    public CropImageView(
            Context context,
            AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.BLACK);

        dimPaint.setColor(0x99000000);

        borderPaint.setStyle(
                Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        borderPaint.setColor(Color.WHITE);

        helpPaint.setColor(Color.DKGRAY);
        helpPaint.setTextAlign(
                Paint.Align.CENTER);
        helpPaint.setTextSize(dp(14));

        scaleDetector =
                new ScaleGestureDetector(
                        context,
                        new ScaleGestureDetector
                                .SimpleOnScaleGestureListener() {
            @Override public boolean onScale(
                    ScaleGestureDetector detector) {
                synchronized (CropImageView.this) {
                    if (bitmap == null
                            || cropRect.isEmpty()) {
                        return false;
                    }

                    float oldScale =
                            drawScaleLocked();

                    float focusSourceX =
                            sourceCenterX
                                    + (detector.getFocusX()
                                    - cropRect.centerX())
                                    / oldScale;

                    float focusSourceY =
                            sourceCenterY
                                    + (detector.getFocusY()
                                    - cropRect.centerY())
                                    / oldScale;

                    userScale =
                            clamp(
                                    userScale
                                            * detector
                                            .getScaleFactor(),
                                    MIN_USER_SCALE,
                                    MAX_USER_SCALE);

                    float newScale =
                            drawScaleLocked();

                    sourceCenterX =
                            focusSourceX
                                    - (detector.getFocusX()
                                    - cropRect.centerX())
                                    / newScale;

                    sourceCenterY =
                            focusSourceY
                                    - (detector.getFocusY()
                                    - cropRect.centerY())
                                    / newScale;

                    clampCenterLocked();
                }

                invalidate();
                return true;
            }
        });

        gestureDetector =
                new GestureDetector(
                        context,
                        new GestureDetector
                                .SimpleOnGestureListener() {
            @Override public boolean onDown(
                    MotionEvent event) {
                return true;
            }

            @Override public boolean onDoubleTap(
                    MotionEvent event) {
                resetTransform();
                return true;
            }
        });
    }

    public synchronized void setTargetSize(
            int width,
            int height) {
        targetW = Math.max(1, width);
        targetH = Math.max(1, height);

        recomputeCropRectLocked();
        clampCenterLocked();
        invalidate();
    }

    public synchronized void setImageBitmap(
            Bitmap value) {
        bitmap = value;
        resetTransformLocked();
        invalidate();
    }

    public synchronized boolean hasImage() {
        return bitmap != null;
    }

    public void resetTransform() {
        synchronized (this) {
            resetTransformLocked();
        }

        invalidate();
    }

    private void resetTransformLocked() {
        userScale = 1f;

        if (bitmap != null) {
            sourceCenterX =
                    bitmap.getWidth() * 0.5f;
            sourceCenterY =
                    bitmap.getHeight() * 0.5f;
        } else {
            sourceCenterX = 0f;
            sourceCenterY = 0f;
        }

        recomputeCropRectLocked();
        clampCenterLocked();
    }

    @Override protected synchronized
    void onSizeChanged(
            int w,
            int h,
            int oldw,
            int oldh) {
        viewW = w;
        viewH = h;

        recomputeCropRectLocked();
        clampCenterLocked();
    }

    private void recomputeCropRectLocked() {
        if (viewW <= 0 || viewH <= 0) {
            return;
        }

        float padding = dp(12);
        float availW =
                Math.max(
                        1f,
                        viewW - padding * 2f);
        float availH =
                Math.max(
                        1f,
                        viewH - padding * 2f);

        float aspect =
                targetW / (float) targetH;

        float cropW = availW;
        float cropH =
                cropW / aspect;

        if (cropH > availH) {
            cropH = availH;
            cropW =
                    cropH * aspect;
        }

        float left =
                (viewW - cropW) * 0.5f;
        float top =
                (viewH - cropH) * 0.5f;

        cropRect.set(
                left,
                top,
                left + cropW,
                top + cropH);
    }

    private float baseScaleLocked() {
        if (bitmap == null
                || cropRect.isEmpty()) {
            return 1f;
        }

        return Math.max(
                cropRect.width()
                        / bitmap.getWidth(),
                cropRect.height()
                        / bitmap.getHeight());
    }

    private float drawScaleLocked() {
        return baseScaleLocked()
                * userScale;
    }

    private void clampCenterLocked() {
        if (bitmap == null
                || cropRect.isEmpty()) {
            return;
        }

        float scale =
                drawScaleLocked();

        float halfSourceW =
                cropRect.width()
                        / (2f * scale);
        float halfSourceH =
                cropRect.height()
                        / (2f * scale);

        if (halfSourceW * 2f
                >= bitmap.getWidth()) {
            sourceCenterX =
                    bitmap.getWidth()
                            * 0.5f;
        } else {
            sourceCenterX =
                    clamp(
                            sourceCenterX,
                            halfSourceW,
                            bitmap.getWidth()
                                    - halfSourceW);
        }

        if (halfSourceH * 2f
                >= bitmap.getHeight()) {
            sourceCenterY =
                    bitmap.getHeight()
                            * 0.5f;
        } else {
            sourceCenterY =
                    clamp(
                            sourceCenterY,
                            halfSourceH,
                            bitmap.getHeight()
                                    - halfSourceH);
        }
    }

    @Override protected void onDraw(
            Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(
                Color.rgb(
                        230, 230, 226));

        Bitmap localBitmap;
        RectF localCrop =
                new RectF();
        float centerX;
        float centerY;
        float scale;

        synchronized (this) {
            localBitmap = bitmap;
            localCrop.set(cropRect);
            centerX = sourceCenterX;
            centerY = sourceCenterY;
            scale = drawScaleLocked();
        }

        if (localBitmap != null
                && !localCrop.isEmpty()) {
            int save =
                    canvas.save();

            canvas.clipRect(
                    localCrop);
            canvas.drawColor(
                    Color.WHITE);

            float left =
                    localCrop.centerX()
                            - centerX * scale;
            float top =
                    localCrop.centerY()
                            - centerY * scale;

            canvas.translate(
                    left, top);
            canvas.scale(
                    scale, scale);

            canvas.drawBitmap(
                    localBitmap,
                    0f,
                    0f,
                    bitmapPaint);

            canvas.restoreToCount(save);
        } else if (!localCrop.isEmpty()) {
            Paint white = new Paint();
            white.setColor(Color.WHITE);

            canvas.drawRect(
                    localCrop, white);

            canvas.drawText(
                    "SELECT IMAGE",
                    localCrop.centerX(),
                    localCrop.centerY(),
                    helpPaint);
        }

        if (!localCrop.isEmpty()) {
            canvas.drawRect(
                    0,
                    0,
                    getWidth(),
                    localCrop.top,
                    dimPaint);

            canvas.drawRect(
                    0,
                    localCrop.bottom,
                    getWidth(),
                    getHeight(),
                    dimPaint);

            canvas.drawRect(
                    0,
                    localCrop.top,
                    localCrop.left,
                    localCrop.bottom,
                    dimPaint);

            canvas.drawRect(
                    localCrop.right,
                    localCrop.top,
                    getWidth(),
                    localCrop.bottom,
                    dimPaint);

            canvas.drawRect(
                    localCrop,
                    borderPaint);
        }
    }

    @Override public boolean onTouchEvent(
            MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        synchronized (this) {
            if (bitmap == null) {
                return true;
            }
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                dragging = false;
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()
                        && dragging
                        && event.getPointerCount()
                        == 1) {
                    float x = event.getX();
                    float y = event.getY();

                    synchronized (this) {
                        float scale =
                                drawScaleLocked();

                        sourceCenterX -=
                                (x - lastX)
                                        / scale;

                        sourceCenterY -=
                                (y - lastY)
                                        / scale;

                        clampCenterLocked();
                    }

                    lastX = x;
                    lastY = y;

                    invalidate();
                }

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;

            default:
                return true;
        }
    }

    public synchronized Bitmap renderCroppedBitmap(
            int outW,
            int outH) {
        if (bitmap == null
                || cropRect.isEmpty()) {
            return null;
        }

        float scale =
                drawScaleLocked();

        float halfSourceW =
                cropRect.width()
                        / (2f * scale);
        float halfSourceH =
                cropRect.height()
                        / (2f * scale);

        RectF src =
                new RectF(
                        sourceCenterX
                                - halfSourceW,
                        sourceCenterY
                                - halfSourceH,
                        sourceCenterX
                                + halfSourceW,
                        sourceCenterY
                                + halfSourceH);

        Bitmap out =
                Bitmap.createBitmap(
                        outW,
                        outH,
                        Bitmap.Config.ARGB_8888);

        Canvas canvas =
                new Canvas(out);

        canvas.drawColor(
                Color.WHITE);

        float scaleX =
                outW / src.width();
        float scaleY =
                outH / src.height();

        canvas.scale(
                scaleX, scaleY);

        canvas.translate(
                -src.left,
                -src.top);

        canvas.drawBitmap(
                bitmap,
                0f,
                0f,
                bitmapPaint);

        return out;
    }

    private float dp(float value) {
        return value
                * getResources()
                .getDisplayMetrics()
                .density;
    }

    private static float clamp(
            float value,
            float min,
            float max) {
        return Math.max(
                min,
                Math.min(
                        max, value));
    }
}
