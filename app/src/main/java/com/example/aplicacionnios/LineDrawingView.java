package com.example.aplicacionnios;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class LineDrawingView extends View {
    private Paint linePaint;
    private List<float[]> lines = new ArrayList<>();

    public LineDrawingView(Context context) {
        super(context);
        init();
    }

    public LineDrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#7E57C2")); // Purple
        linePaint.setStrokeWidth(8f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setAntiAlias(true);
    }

    public void addLine(float startX, float startY, float endX, float endY) {
        lines.add(new float[]{startX, startY, endX, endY});
        invalidate();
    }

    public void clearLines() {
        lines.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (float[] line : lines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], linePaint);
        }
    }
}
