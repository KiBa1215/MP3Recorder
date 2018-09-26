package com.kiba.mp3recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.kiba.audiorecord.MediaRecordHelper;
import com.kiba.audiorecord.RecordStateListener;
import com.kiba.audiorecord.RecordStatus;

import java.util.Date;

public class MainActivity extends AppCompatActivity implements HoldPressListener, RecordStateListener {

    private static final String TAG = "MainActivity";

    private TextView textView;
    private ProgressBar progressBar;
    private HoldPressButton button;

    private MediaRecordHelper helper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        helper = MediaRecordHelper.getInstance(getApplicationContext());

        helper.setRecordStateListener(this);

        textView = findViewById(R.id.textView);
        button = findViewById(R.id.holdPressBtn);
        progressBar = findViewById(R.id.progressBar);

        button.setHoldPressListener(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 100);
        }
    }

    @Override
    public void onStartPress() {
        Log.d(TAG, "onStartPress: ");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        helper.startRecording(new Date().toString());
    }

    @Override
    public void onFinishPress() {
        Log.d(TAG, "onFinishPress: ");
        helper.stopRecording();
    }

    @Override
    public void onHoldButtonStateChanged(boolean inside) {
        Log.d(TAG, "onHoldButtonStateChanged: inside = " + inside);
        if (inside) {
            textView.setText("Outside the button");
        } else {
            textView.setText("Inside the button");
        }
    }

    @Override
    public void onCancel(int code) {
        if(code == DURATION_TOO_SHORT){
            textView.setText("Duration too short");
        }
        if(code == TOUCH_OUTSIDE){
            textView.setText("Outside the button");
        }
        Log.d(TAG, "onCancel: ");
        helper.cancelRecording();
    }

    @Override
    public void onState(MediaRecordHelper.State state) {
        if (state != null) {
            RecordStatus status = state.getRecordStatus();
            if (status == RecordStatus.RECORDING) {
                int volume = state.getVolume();
                int maxVolume = state.getMaxVolume();
                final int progress = volume * 100 / maxVolume;
                progressBar.post(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(progress);
                    }
                });
            }
        }
    }
}
