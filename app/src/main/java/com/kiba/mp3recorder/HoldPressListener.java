package com.kiba.mp3recorder;

/**
 * Description:
 * <p>
 * Created by Kiba 2018/8/30
 */
public interface HoldPressListener {

    int DURATION_TOO_SHORT = 0x01;
    int TOUCH_OUTSIDE = 0x02;

    void onStartPress();

    void onFinishPress();

    void onHoldButtonStateChanged(boolean inside);

    void onCancel(int code);
}
