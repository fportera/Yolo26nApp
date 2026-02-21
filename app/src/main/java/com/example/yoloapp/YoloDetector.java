package com.example.yoloapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;

class YoloDetector {
    private Interpreter tflite;
    private ArrayList<String> labels;
    private final int inputSize = 640; // Change to your model's requirement

    public YoloDetector(Context context) throws IOException {
        try {
            // Load the model file
            MappedByteBuffer tfliteModel = FileUtil.loadMappedFile(context, "yolo26n_float32.tflite");

            // Set up Interpreter Options (optional but recommended for speed)
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Use 4 CPU cores

            // Create the Interpreter
            tflite = new Interpreter(tfliteModel, options);

            Log.d("YOLO", "Model loaded successfully!");
        } catch (IOException e) {
            Log.e("YOLO", "Error loading model: " + e.getMessage());
        }
        // Load model and labels
        labels = loadLabelList(context,"labels.txt");
    }

    ArrayList<String> getLabels() { return labels; };

    private ArrayList<String> loadLabelList(Context context, String labelPath) throws IOException {
        ArrayList<String> labelList = new ArrayList<>();
        InputStream is = context.getAssets().open(labelPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                labelList.add(line);
            }
        }
        reader.close();
        return labelList;
    }
    public float[][][] detect(Bitmap bitmap) {
        // 1. Pre-process the image
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 255f)) // Normalize 0-255 to 0.0-1.0 for float32
                .build();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);
        tensorImage = imageProcessor.process(tensorImage);

        // 2. Prepare output buffer
        float[][][] output = new float[1][300][6];

        // 3. Run Inference
        tflite.run(tensorImage.getBuffer(), output);

        // 4. Post-process (See Step 4)
        return output;
    }


}