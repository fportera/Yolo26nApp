package com.example.yoloapp;

import android.graphics.RectF;

class Recognition {
    public RectF location; // The bounding box
    public int label;   // The class name
    public float score;    // Confidence (0.0 to 1.0)

    RectF getLocation() {return location;}
    public Recognition(int label, float score, RectF location) {
        this.label = label;
        this.score = score;
        this.location = location;
    }
}
