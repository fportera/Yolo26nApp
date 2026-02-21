package com.example.yoloapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowMetrics;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraProvider;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private OverlayView overlayView; // Custom view to draw boxes
    private YoloDetector detector;
    private ExecutorService cameraExecutor;

    private ImageCapture imageCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("A", "Yolo Activity starting...");
        System.out.println("Yolo Yolo");
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);

        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            detector = new YoloDetector(this);
        } catch (IOException e) {
            Log.e("YOLO", "Model loading failed", e);
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, 101);
        startCamera();
    }
    private Bitmap imageToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private Bitmap rotateBitmap(Bitmap source, int degrees) {
        if (degrees == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }


    // 4. CRITICAL: You must close the image or the camera will freeze
    // after the first frame.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted! Start the camera now.
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void startCamera() {

        // Inside your startCamera() method
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // This is the object that has bindToLifecycle
                ProcessCameraProvider cameraProvider = null;
                cameraProvider = cameraProviderFuture.get();

                // 1. Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // 2. ImageAnalysis (for YOLO)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // 3. Select back camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // ✅ The correct call:
                cameraProvider.bindToLifecycle(
                        this, // The LifecycleOwner (Your Activity)
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    // 1. Convert the frame to a format YOLO understands
                    // We use the PreviewView's bitmap for easy scaling and rotation handling
                    Bitmap bitmap = previewView.getBitmap();

                    if (bitmap != null) {
                        int imageWidth = bitmap.getWidth();
                        int imageHeight = bitmap.getHeight();
                        // Scale to 640 x 640?

                        List<Recognition> results = mapOutputToRecognitions(detector.detect(bitmap));

                        // 3. Update the UI (Drawing boxes)
                        // Must run on UI thread because you're touching a View
                        runOnUiThread(() -> {
                            overlayView.setResults(results,imageWidth,imageHeight);
                            overlayView.invalidate(); // Tells the view to redraw itself
                        });
                    }

                    // 4. CRITICAL: Close the imageProxy
                    // If you forget this, the camera will freeze after the first frame!
                    imageProxy.close();
                });
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CAMERA", "Binding failed", e);
            }

            }, ContextCompat.getMainExecutor(this));

        return;
    }

    private List<Recognition> mapOutputToRecognitions(float[][][] output) {
        List<Recognition> recognitions = new ArrayList<>();

        // The first dimension [0] is the batch size (usually 1)
        float[][] detections = output[0];

        int classId = -1;
        float maxClassScore = 0f;
        float[] wrow = detections[0];
        for (int i = 0; i < detections.length; i++) {
            float[] row = detections[i];

            // 1. Get the confidence score
            float confidence = row[4]; // Adjust index based on your YOLO version
            if (confidence > maxClassScore ) {
                maxClassScore = confidence;
                classId = ((int) row[5]) ;
                Log.d("YoloAPP", "classId = " + row[5]);
                wrow = row;
            }
        }
        float cx = wrow[0];
        float cy = wrow[1];
        float w = wrow[2];
        float h = wrow[3];

        Log.d("COORDS MainActivity","cx=" + cx + ", cy=" + cy);
        Log.d("COORDS MainActivity","w=" + w + ", h=" + h);

        RectF rect = new RectF(
                cx - w / 2, // Left
                cy - h / 2, // Top
                cx + w / 2, // Right
                cy + h / 2  // Bottom
        );

        //cx *= 2.5;
        // cy *= 2.5;

        RectF rect1 = new RectF(
                cx, // Left
                cy,  // Top
                cx + w, // Right
                cy+ h   // Bottom
        );

        recognitions.add(new Recognition(classId, maxClassScore, rect));
        // 4. Apply NMS (Non-Maximum Suppression) to remove overlapping boxes
        return applyNMS(recognitions);
    }
    private List<Recognition> applyNMS(List<Recognition> recognitions) {
        List<Recognition> nmsList = new ArrayList<>();
        float threshold = 0.5f; // Adjust this: lower means stricter (fewer boxes)

        for (int i = 0; i < recognitions.size(); i++) {
            Recognition a = recognitions.get(i);
            boolean keep = true;

            for (Recognition b : nmsList) {
                // Calculate Intersection over Union (IoU)
                if (calculateIoU(a.getLocation(), b.getLocation()) > threshold) {
                    keep = false;
                    break;
                }
            }
            if (keep) nmsList.add(a);
        }
        return nmsList;
    }

    private float calculateIoU(RectF a, RectF b) {
        float intersectionArea = Math.max(0f, Math.min(a.right, b.right) - Math.max(a.left, b.left)) *
                Math.max(0f, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));

        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        float unionArea = areaA + areaB - intersectionArea;

        return intersectionArea / unionArea;
    }

}