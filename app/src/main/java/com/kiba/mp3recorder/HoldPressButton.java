package com.kiba.mp3recorder;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.v7.widget.AppCompatButton;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Description:
 * <p>
 * Created by Kiba 2018/8/30
 */
public class HoldPressButton extends AppCompatButton implements View.OnTouchListener {

    private static final String TAG = "HoldPressButton";

    private static final int CANCEL_TOLERANCE = 100;
    private static final int MIN_TIME_SPAN = 1000;

    private HoldPressListener holdPressListener;

    private PointF pressPoint;
    private RectF borderRect;
    private boolean isInside = true;
    private long timestamp;

    public HoldPressButton(Context context) {
        super(context);
        init();
    }

    public HoldPressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HoldPressButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        setOnTouchListener(this);
        pressPoint = new PointF(0, 0);
        borderRect = new RectF();

    }

    private void onStartPress() {
        if (holdPressListener != null) {
            holdPressListener.onStartPress();
        }
    }

    private void onFinishPress() {
        if (holdPressListener != null) {
            holdPressListener.onFinishPress();
        }
    }

    private void onStateChanged(boolean inside) {
        if (holdPressListener != null) {
            holdPressListener.onHoldButtonStateChanged(inside);
        }
    }

    /**
     * @param code {@link HoldPressListener#DURATION_TOO_SHORT}, {@link HoldPressListener#TOUCH_OUTSIDE}
     */
    private void onCancel(int code) {
        if (holdPressListener != null) {
            holdPressListener.onCancel(code);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // init inside
                setInside(true);
                // background
                setBackgroundColor(v.getContext().getResources().getColor(R.color.colorAccent));
                // point
                pressPoint.set(event.getX(), event.getY());
                // border rect
                if (borderRect == null) {
                    borderRect = new RectF(
                            0 - CANCEL_TOLERANCE,
                            0 - CANCEL_TOLERANCE,
                            getWidth() + CANCEL_TOLERANCE,
                            getHeight() + CANCEL_TOLERANCE);
                } else {
                    borderRect.left = 0 - CANCEL_TOLERANCE;
                    borderRect.top = 0 - CANCEL_TOLERANCE;
                    borderRect.right = getWidth() + CANCEL_TOLERANCE;
                    borderRect.bottom = getHeight() + CANCEL_TOLERANCE;
                }

                onStartPress();

                // timestamp
                timestamp = System.currentTimeMillis();

                break;

            case MotionEvent.ACTION_MOVE:
                // point
                pressPoint.set(event.getX(), event.getY());
                if (holdPressListener != null) {
                    boolean inside = isTouchInside();
                    setInside(inside);
                }
                break;

            case MotionEvent.ACTION_UP:
                setBackgroundColor(v.getContext().getResources().getColor(R.color.colorPrimary));
                boolean isTouchInside = isTouchInside();
                boolean isDurationValid = isDurationValid();
                if (!isTouchInside) {
                    onCancel(HoldPressListener.TOUCH_OUTSIDE);
                    return false;
                }
                if (!isDurationValid) {
                    onCancel(HoldPressListener.DURATION_TOO_SHORT);
                    return false;
                }

                onFinishPress();
                break;

            default:
                return false;
        }

        return false;
    }

    private boolean isDurationValid() {
        long span = System.currentTimeMillis() - timestamp;
        return span > MIN_TIME_SPAN;
    }

    private boolean isTouchInside() {
        return borderRect.contains(pressPoint.x, pressPoint.y);
    }

    private void setInside(boolean inside) {
        if (isInside != inside) {
            onStateChanged(inside);
        }
        isInside = inside;
    }

    public HoldPressListener getHoldPressListener() {
        return holdPressListener;
    }

    public void setHoldPressListener(HoldPressListener holdPressListener) {
        this.holdPressListener = holdPressListener;
    }

}
