package com.kiba.audiorecord;

/**
 * Description:
 * <p>
 * Created by Kiba 2018/9/26
 */

public interface RecordStateListener {
    void onState(MediaRecordHelper.State state);
}