package com.moregs.bezeltimer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class RadialProgress extends View {
    private long progress_max;
    private long progress;
    private int progress_min;
    private int progress_color;
    private int background_color;

    private Paint progress_paint;
    private Paint background_paint;

    private RectF mBounds = new RectF();
    private RectF middle_oval = new RectF();

    public RadialProgress(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.RadialProgress,
                0, 0);

        try {
            progress = a.getInteger(R.styleable.RadialProgress_progress, 0);
            progress_max = a.getInteger(R.styleable.RadialProgress_progress_max, 100);
            progress_min = a.getInteger(R.styleable.RadialProgress_progress_min, 100);
            progress_color = a.getColor(R.styleable.RadialProgress_progress_color, Color.parseColor("#ffffff"));
            background_color = a.getColor(R.styleable.RadialProgress_background_color, Color.parseColor("#000000"));
        } finally {
            a.recycle();
        }

        init();
    }

    private void init() {
        progress_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progress_paint.setColor(progress_color);
        progress_paint.setStyle(Paint.Style.FILL);

        background_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        background_paint.setColor(background_color);
        background_paint.setStyle(Paint.Style.FILL);
    }

    public void setProgress_color(int color) {
        progress_paint.setColor(color);
        invalidate();
    }

    public void setProgress_max(long new_max) {
        progress_max = new_max;
    }

    public void setProgress(long new_progress) {
        progress = new_progress;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Try for a width based on our minimum
        int minw = getPaddingLeft() + getPaddingRight() + getSuggestedMinimumWidth();

        int w = Math.max(minw, MeasureSpec.getSize(widthMeasureSpec));

        // Whatever the width ends up being, ask for a height that would let the pie
        // get as big as it can
        int minh = w + getPaddingBottom() + getPaddingTop();
        int h = Math.min(MeasureSpec.getSize(heightMeasureSpec), minh);

        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Account for padding
        float xpad = (float) (getPaddingLeft() + getPaddingRight());
        float ypad = (float) (getPaddingTop() + getPaddingBottom());

        float ww = (float) w - xpad;
        float hh = (float) h - ypad;

        // Figure out how big we can make the pie.
        float diameter = Math.min(ww, hh);
        mBounds = new RectF(
                0.0f,
                0.0f,
                diameter,
                diameter);
        mBounds.offsetTo(getPaddingLeft(), getPaddingTop());
        middle_oval = new RectF(
                mBounds.left + 10.0f,
                mBounds.top + 10.0f,
                mBounds.right - 10.0f,
                mBounds.bottom - 10.0f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float end_angle = (progress_max - progress) * 360.0f / progress_max;
        canvas.drawArc(
                mBounds,
                 -90,
                360.0f - end_angle,
                true,
                progress_paint);
        canvas.drawOval(middle_oval, background_paint);
    }
}