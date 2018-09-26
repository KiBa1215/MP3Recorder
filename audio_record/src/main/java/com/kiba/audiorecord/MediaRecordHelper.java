package com.kiba.audiorecord;

import android.Manifest;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.annotation.RequiresPermission;
import android.text.TextUtils;
import android.util.Log;

import org.lame.util.LameUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Description:
 * <p>
 * Created by Kiba 2018/8/30
 */
public class MediaRecordHelper {

    public static class State {

        private RecordStatus recordStatus = RecordStatus.IDLE;

        private int volume;

        private int maxVolume = MediaRecordHelper.MAX_VOLUME;

        public RecordStatus getRecordStatus() {
            return recordStatus;
        }

        private void setRecordStatus(RecordStatus recordStatus) {
            this.recordStatus = recordStatus;
        }

        public int getVolume() {
            return volume;
        }

        private void setVolume(int volume) {
            this.volume = volume;
        }

        public int getMaxVolume() {
            return maxVolume;
        }
    }

    private static final String TAG = "MediaRecordHelper";

    private static final int DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int DEFAULT_LAME_IN_CHANNEL = 1; // 与DEFAULT_CHANNEL_CONFIG相关，因为是mono单声道，所以是1
    private static final int DEFAULT_LAME_MP3_QUALITY = 7; // MP3 压缩质量
    private static final int DEFAULT_LAME_MP3_BIT_RATE = 32; // Encoded bit rate. MP3 file will be encoded with bit rate 32kbps

    private static final int SAMPLE_RATE_IN_HZ = 8000; // 采样率

    public static final int MAX_VOLUME = 2000; // 最大音量

    private ExecutorService recordExecutor = Executors.newFixedThreadPool(1); // 单个线程

    private static MediaRecordHelper helper; // singleton

    private AudioRecord audioRecord;

    private FileOutputStream fos;
    private int minBufferSize; // 最小audio空间
    private byte[] mp3Buffer; // 转换mp3 开辟的空间

    private String audioRecordDirectory;
    private boolean isRecordPathAvailable;
    private String currentRecordingFilePath;

    private boolean isRecording = false; // 循环标志位

    private State state;
    private RecordStateListener stateListener;

    private MediaRecordHelper(Context context, String storageDirectory) {

        minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE_IN_HZ,
                DEFAULT_CHANNEL_CONFIG,
                AudioFormat.ENCODING_PCM_16BIT);

        if (!TextUtils.isEmpty(storageDirectory)) {
            audioRecordDirectory = storageDirectory;
        } else {
            // 创建录音文件目录
            Log.d(TAG, "MediaRecordHelper: " + context.getApplicationContext().getExternalFilesDir("audioRecord"));
            File baseDirectoryFile = context.getApplicationContext().getExternalFilesDir("audioRecord");
            if (baseDirectoryFile == null) {
                audioRecordDirectory = context.getApplicationContext().getFilesDir().getPath() + "/audioRecord";
            } else {
                audioRecordDirectory = baseDirectoryFile.getPath();
            }
        }

        File audioRecordDirectory = new File(this.audioRecordDirectory);
        if (!audioRecordDirectory.isDirectory()) {
            isRecordPathAvailable = false;
            return;
        }
        if (audioRecordDirectory.exists()) {
            isRecordPathAvailable = true;
        } else {
            boolean success = audioRecordDirectory.mkdirs();
            if (success) {
                isRecordPathAvailable = true;
            } else {
                isRecordPathAvailable = false;
                Log.w(TAG, "Path: " + this.audioRecordDirectory + " not successfully created.");
            }
        }

        // mp3 文件buffer
        mp3Buffer = new byte[(int) (7200 + (minBufferSize * 2 * 1.25))];
        // 初始化State对象
        state = new State();
    }

    public MediaRecordHelper(Context context) {
        this(context, null);
    }

    private void initAudioRecord() {
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN_HZ, // 采样率
                DEFAULT_CHANNEL_CONFIG, // 声道
                AudioFormat.ENCODING_PCM_16BIT, // 录制音频格式 -> PCM
                minBufferSize);

        // 初始化lame
        LameUtil.init(
                SAMPLE_RATE_IN_HZ, // 输入采样率
                DEFAULT_LAME_IN_CHANNEL,
                SAMPLE_RATE_IN_HZ, // 输出采样率
                DEFAULT_LAME_MP3_BIT_RATE, // MP3输出比特率
                DEFAULT_LAME_MP3_QUALITY // 音频质量
        );
    }

    public synchronized static MediaRecordHelper getInstance(Context context) {
        if (helper == null) {
            helper = new MediaRecordHelper(context);
        }
        return helper;
    }

    public synchronized static MediaRecordHelper getInstance(Context context, String storageDirectory) {
        if (helper == null) {
            helper = new MediaRecordHelper(context, storageDirectory);
        }
        return helper;
    }

    /**
     * Note: Need audio permission granted.
     * @param filename record file name
     * @throws IllegalStateException
     */
    @RequiresPermission(value = Manifest.permission.RECORD_AUDIO)
    public synchronized void startRecording(final String filename) throws IllegalStateException {
        if (isRecording) {
            Log.w(TAG, "A recording task already exists");
            return;
        }
        if (isRecordPathAvailable) {
            Log.d(TAG, ": ==============startRecording=================");
            initAudioRecord();
            audioRecord.startRecording();
            // 更新状态
            postState(RecordStatus.START_RECORDING, 0);
            recordExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        record(filename);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        cancelRecording();
                    }
                }
            });
        } else {
            throw new IllegalStateException("Record path not available.");
        }
    }

    /**
     * 更新状态
     * @param recordStatus {@link RecordStatus}
     * @param volume volume
     */
    private void postState(RecordStatus recordStatus, int volume) {
        if (state == null) {
            state = new State();
        }
        state.setRecordStatus(recordStatus);
        state.setVolume(volume);
        if (stateListener != null) {
            stateListener.onState(state);
        }
    }

    private void record(String filename) throws FileNotFoundException {
        isRecording = true;
        // set recording file path
        currentRecordingFilePath = audioRecordDirectory + "/" + filename;

        if (!currentRecordingFilePath.endsWith(".mp3")) {
            currentRecordingFilePath = currentRecordingFilePath.concat(".mp3");
        }

        short[] data = new short[minBufferSize];

        fos = new FileOutputStream(new File(currentRecordingFilePath));

        while (isRecording) {

            int read = audioRecord.read(data, 0, minBufferSize);
            if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                cancelRecording();
                break;
            }
            try {
                // lame mp3 转换
                int size = LameUtil.encode(data, data, minBufferSize, mp3Buffer);
                if (size < 0) {
                    throw new IOException("Lame encode error: " + size);
                }
                fos.write(mp3Buffer, 0, size);
                int volume = calculateRealVolume(data, read);
                // 更新状态
                postState(RecordStatus.RECORDING, volume);
            } catch (IOException e) {
                e.printStackTrace();
                cancelRecording();
                break;
            }
        }
    }

    /**
     * Failed to record, will delete what has been recorded.
     */
    public void cancelRecording() {
        releaseInternal(RecordStatus.CANCEL_RECORDING);
        // delete recorded file
        if (currentRecordingFilePath != null) {
            File file = new File(currentRecordingFilePath);
            if (file.exists()) {
                file.delete();
            }
        }
        Log.d(TAG, ": ==============cancelRecording=================");
    }

    /**
     * Success to record.
     */
    public void stopRecording() {
        releaseInternal(RecordStatus.COMPLETE_RECORDING);
        Log.d(TAG, ": ==============stopRecording=================");
    }

    private void releaseInternal(RecordStatus state) {
        if (isRecording) {
            isRecording = false;
            audioRecord.release();
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                    fos = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // 将MP3结尾信息写入buffer中
            LameUtil.flush(mp3Buffer);
            // close lame encoder
            LameUtil.close();
            Log.d(TAG, ": ==============released=================");
            // 更新状态
            postState(state, 0);
        }
    }

    /**
     * average the volume per frame
     * @param buffer read buffer
     * @param readSize read size
     * @return volume
     */
    private int calculateRealVolume(short[] buffer, int readSize) {
        int sum = 0;
        for (int i = 0; i < readSize; i++) {
            sum += buffer[i] * buffer[i];
        }
        if (readSize > 0) {
            double amplitude = sum / readSize;
            return (int) Math.sqrt(amplitude);
        }
        return 0;
    }

    public void setRecordStateListener(RecordStateListener stateListener) {
        this.stateListener = stateListener;
    }
}
