package com.example.hanziime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import com.google.mlkit.vision.digitalink.recognition.Ink;

import java.util.ArrayList;
import java.util.List;

/** Captures both visible paths and time-aware digital ink strokes. */
public final class HandwritingPad extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Path> paths = new ArrayList<>();
    private Ink.Builder inkBuilder = Ink.builder();
    private Ink.Stroke.Builder strokeBuilder;
    private Path activePath;

    public HandwritingPad(Context context) {
        super(context);
        paint.setColor(Color.rgb(23, 33, 43));
        paint.setStrokeWidth(dp(5));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Path path : paths) canvas.drawPath(path, paint);
        if (activePath != null) canvas.drawPath(activePath, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        long timestamp = System.currentTimeMillis();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                activePath = new Path();
                activePath.moveTo(x, y);
                strokeBuilder = Ink.Stroke.builder();
                strokeBuilder.addPoint(Ink.Point.create(x, y, timestamp));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (activePath != null && strokeBuilder != null) {
                    activePath.lineTo(x, y);
                    strokeBuilder.addPoint(Ink.Point.create(x, y, timestamp));
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (activePath != null && strokeBuilder != null) {
                    activePath.lineTo(x, y);
                    strokeBuilder.addPoint(Ink.Point.create(x, y, timestamp));
                    paths.add(activePath);
                    inkBuilder.addStroke(strokeBuilder.build());
                    activePath = null;
                    strokeBuilder = null;
                    invalidate();
                }
                return true;
            }
            default -> { return super.onTouchEvent(event); }
        }
    }

    public Ink getInk() {
        return inkBuilder.build();
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public void clear() {
        paths.clear();
        activePath = null;
        strokeBuilder = null;
        inkBuilder = Ink.builder();
        invalidate();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
