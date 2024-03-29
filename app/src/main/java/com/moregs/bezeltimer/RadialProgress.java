package com.moregs.bezeltimer;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * This manages update to the radial progress bar indicator
 */
public class RadialProgress extends View {

    private long progress_max;
    private long progress;
    private int background_color;
    private Paint background_paint;
    private RectF mBounds = new RectF();

    public RadialProgress(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.RadialProgress,
                0, 0);

        try {
            progress = a.getInteger(R.styleable.RadialProgress_progress, 0);
            progress_max = a.getInteger(R.styleable.RadialProgress_progress_max, 100);
            background_color = a.getColor(R.styleable.RadialProgress_background_color, Color.parseColor("#000000"));
        } finally {
            a.recycle();
        }

        init();
    }

    /**
     * Create non-pod globals on init
     */
    private void init() {
        background_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        background_paint.setColor(background_color);
        background_paint.setStyle(Paint.Style.FILL);
    }

    /**
     * Progress maximum is settable so that calling code does not need to calculate percentages
     */
    public void setProgress_max(long new_max) {
        progress_max = new_max;
    }

    /**
     * Call to update the progress to display, should be less than maximum set in setProgress_max()
     */
    public void setProgress(long new_progress) {
        progress = new_progress;
        invalidate();
    }

    /**
     * Called when app starts so that components drawn within bounds
     */
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

    /**
     * Called when app starts so that components drawn within bounds
     */
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
    }

    /**
     * Called every time the screen is updated. Draws the countdown ticks
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float progress_percent = progress / (float) progress_max;

        // Draw remaining section of arc in background color
        // The progress drawn is reversed so we start with a full bar that
        // drains over time.
        float sweep_angle = -((1.0f - progress_percent) * 360.0f);
        canvas.drawArc(mBounds, -90, sweep_angle, true, background_paint);
    }
}
