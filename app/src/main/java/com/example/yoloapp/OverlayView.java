package com.example.yoloapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowMetrics;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class OverlayView extends View {
    private List<Recognition> results;
    private int width;
    private int height;
    private String[] labels;
    private final Paint boxPaint = new Paint();
    private final Paint textBackgroundPaint = new Paint();
    private final Paint textPaint = new Paint();

    public OverlayView(Context context) {
        super(context);

    }
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("labels.txt")))) {
            String line;
            labels = new String[8000];
            int c = 0;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    labels[c] = line;
                    Log.d("open labels","line");
                    c += 1;
                }
            }
        } catch (IOException e) {
            Log.e("TFLite", "Error reading label file!", e);
        }
        // Style for the Bounding Box
        boxPaint.setColor(Color.CYAN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8.0f);

        // Style for the Label text
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(50.0f);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // Style for the text background (makes it readable)
        textBackgroundPaint.setColor(Color.CYAN);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setResults(List<Recognition> results, int w, int h) {
        this.results = results;
        this.width = w;
        this.height = h;

        // Invalidate tells Android to call onDraw() again
        postInvalidate();
    }

    public RectF mapYoloToDisplay(
            float x_center, float y_center, float w, float h,
            int imgW, int imgH,      // Camera Bitmap Resolution (e.g., 1920x1080)
            int viewW, int viewH     // UI View Dimensions (e.g., 1080x2100)
    ) {
        // 1. Calculate the scale used to fit the bitmap into the 640x640 YOLO input
        float gain = Math.min(640f / imgW, 640f / imgH);

        // 2. Calculate the padding (black bars) added during preprocessing
        float padX = (640f - imgW * gain) / 2f;
        float padY = (640f - imgH * gain) / 2f;

        // 3. Convert normalized YOLO (0-1) to 640-scale pixels
        float xYolo = x_center * 640f;
        float yYolo = y_center * 640f;
        float wYolo = w * 640f;
        float hYolo = h * 640f;

        // 4. Map back to Original Bitmap coordinates (undo padding and gain)
        float xImg = (xYolo - padX) / gain;
        float yImg = (yYolo - padY) / gain;
        float wImg = wYolo / gain;
        float hImg = hYolo / gain;

        // 5. Map to UI View coordinates (scale to screen size)
        float screenX = xImg * ((float) viewW / imgW);
        float screenY = yImg * ((float) viewH / imgH);
        float screenW = wImg * ((float) viewW / imgW);
        float screenH = hImg * ((float) viewH / imgH);

        // 6. Return as a RectF (Left, Top, Right, Bottom) for drawing
        return new RectF(
                screenX - (screenW / 2f),
                screenY - (screenH / 2f),
                screenX + (screenW / 2f),
                screenY + (screenH / 2f)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results == null) return;

        for (Recognition res : results) {
            RectF box = res.getLocation();

//            box = mapYoloToDisplay(
  //                  box.left, box.top, box.right, box.bottom,
    //                canvas.getWidth(), canvas.getHeight(),
      //              canvas.getWidth(), canvas.getHeight()
        //    );

            Log.d("labels", String.valueOf(res.label));
            Log.d("labels", labels[res.label]);
            String labelString = labels[res.label] + " " + String.format("%.1f%%", res.score * 100);
            Paint paint = new Paint();
            paint.setColor(Color.GRAY); // Standard grey
            paint.setStyle(Paint.Style.STROKE); // STROKE for an outline, FILL for a solid box
            paint.setStrokeWidth(20f); // Thickness of the border

            int imageWidth = width;
            int imageHeight = height;
            Log.d("COORDS","imageWidth=" + imageWidth + ", imageHeight=" + imageHeight);
            Log.d("COORDS","canvasWidth=" + canvas.getWidth() + ", canvasHeight=" + canvas.getHeight());
            // 1. Calculate how much the image was scaled to fit the screen
            float scale = Math.min(
                    (float) canvas.getWidth() / imageWidth,
                    (float) canvas.getHeight() / imageHeight
            );
            Log.d("COORDS","scale=" + scale);
// 2. Calculate the "Black Bar" offsets
            float xOffset = canvas.getWidth() - (imageWidth * scale) / 2f;
            float yOffset = canvas.getHeight() - (imageHeight * scale) / 2f;
            Log.d("COORDS","xOffset=" + xOffset + ", yOffset=" + yOffset);

// 3. Apply scale and offset to the box
            float left   = (box.left * width * scale) + xOffset;
            float top    = (box.top * height * scale) + yOffset;
            float right  = (box.right * width * scale) + xOffset;
            float bottom = (box.bottom * height *  scale) + yOffset;
            Log.d("COORDS","left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);
;
            canvas.drawRect(left, top, right, bottom, paint);

           Log.d("COORDS","x=" + box.left * canvas.getWidth() + 150 + ", y=" + box.top * getHeight()+250);

            canvas.drawText(labelString, left, top, textPaint);
        }
    }

    public static class MainActivity extends AppCompatActivity {
        private PreviewView previewView;
        private void startCamera() {
            // Get a CameraProvider
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                    ProcessCameraProvider.getInstance(this);

            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    // 1️⃣ Preview Use Case
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    // 2️⃣ Select Back Camera
                    CameraSelector cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build();

                    // 3️⃣ Bind to lifecycle
                    cameraProvider.unbindAll(); // unbind old use cases
                    cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, ContextCompat.getMainExecutor(this));
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            Log.d("YOLO", "Java com.example.yoloapp.OverlayView.MainActivity Started!");
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            previewView = findViewById(R.id.previewView);
            startCamera();
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

    // Use a background executor to keep the UI smooth
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), imageProxy -> {
                // This runs for every frame captured
               // sendToYolo(imageProxy);
            });
        }
    }
}