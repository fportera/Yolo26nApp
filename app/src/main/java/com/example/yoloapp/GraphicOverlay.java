package com.example.yoloapp;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class GraphicOverlay extends View {
    private final Paint paint = new Paint();
    private List<Recognition> boundingBoxes = new ArrayList<>();

    public GraphicOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8.0f);
    }

    // This method is called by MainActivity to update the boxes
    public void drawBoxes(List<Recognition> boxes) {
        this.boundingBoxes = boxes;
        invalidate(); // Tells Android to redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Recognition rect : boundingBoxes) {
            canvas.drawRect(rect.location, paint);
        }
    }
}