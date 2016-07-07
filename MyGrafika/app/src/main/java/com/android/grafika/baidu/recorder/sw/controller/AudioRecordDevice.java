package com.android.grafika.baidu.recorder.sw.controller;

import com.android.grafika.baidu.recorder.api.SessionStateListener;
import com.android.grafika.baidu.recorder.sw.bean.AudioParams;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioRecordDevice extends RecordDevices {

    private static final String TAG = "AudioRecordDevice";
    private AudioRecord audioRecord = null;
    private boolean isRunning = false;
    private Thread thread = null;
    private int bufferLength = 0;
    private IRecorder mRecorder = null;
    private AudioParams audioParams = null;
    private boolean isSendingAudio = true;

    public void setBRecorder(IRecorder recorder) {
        mRecorder = recorder;
    }

    public AudioRecordDevice(AudioParams audioParams) {
        this.audioParams = audioParams;
    }

    /**
     * 获取设置支持的audioRecord
     */
    private boolean choosAudioDevice() {
        int[] sampleRates = { 44100, 22050, 11025 };
        for (int sampleRateInHz : sampleRates) {
            audioParams.setSampleRateInHz(sampleRateInHz);
            audioParams.setChannelConfig(AudioFormat.CHANNEL_IN_STEREO);
            audioParams.setnChannels(2);
            audioParams.setAudioFormat(AudioFormat.ENCODING_PCM_16BIT);

            int bSamples = 16;
            if (audioParams.getAudioFormat() == AudioFormat.ENCODING_PCM_8BIT) {
                bSamples = 8;
            }

            if (audioParams.getChannelConfig() == AudioFormat.CHANNEL_IN_MONO) {
                audioParams.setnChannels(1);
            }
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(audioParams.getSampleRateInHz(),
                    audioParams.getChannelConfig(), audioParams.getAudioFormat());

            AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, audioParams.getSampleRateInHz(),
                    audioParams.getChannelConfig(), audioParams.getAudioFormat(), bufferSizeInBytes);
            if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "initialize the mic failed.");
                continue;
            }

            this.audioRecord = audioRecorder;
            bufferLength = 4096; // bufferSizeInBytes;
            Log.i(TAG, String.format("[audioRecord] mic open rate=%dHZ, channels=%d, bits=%d,buffer:%d, state=%d",
                    sampleRateInHz, audioParams.getnChannels(), bSamples, bufferSizeInBytes, audioRecorder.getState()));
            break;
        }
        
        return this.audioRecord != null;
    }

    /**
     * 开启音频录制设备
     */
    @Override
    public boolean open() {
        if (choosAudioDevice()) {
            try {
                audioRecord.startRecording();
            } catch (Throwable e) {
                e.printStackTrace();
                isRunning = false;
                if (mStateListener != null) {
                    mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_OPEN_MIC_FAILED);
                }
                return false;
            }
            isRunning = true;
            thread = new Thread(audioRunnable, "[aThread]");
            thread.start();
            return true;
        } else {
            Log.e(TAG, "[audioRecord] AudioRecord is null !!");
            if (mStateListener != null) {
                mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_OPEN_MIC_FAILED);
            }
            return false;
        }
    }

    /**
     * 停止录制
     */
    @Override
    public void close() {
        isRunning = false;
        Log.d(TAG, "[======audio stop]");
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
    }

    /**
     * 重置参数
     */
    @Override
    public void reset() {

    }

    // //////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////

    Runnable audioRunnable = new Runnable() {
        @Override
        public void run() {
            while (isRunning) {
                if (!isSendingAudio) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {}
                    continue;
                }
                byte[] tmp = new byte[bufferLength];
                // int size = audioRecord.read(abuffer,0,abuffer.length);

                int size = audioRecord.read(tmp, 0, bufferLength);
                if (size <= 0) {
                    Log.i(TAG, "[audioRecord] audio ignore, no data to read.");
                    break;
                }

                long timestamp = System.nanoTime() / 1000;
                if (mRecorder != null) {
                    mRecorder.feedingAudioFrame(tmp, size, timestamp);
                }
            }

            if (mRecorder != null) {
                mRecorder.setStartTime(0);
            }
            if (audioRecord != null) {
                audioRecord.stop();
            }
            Log.d(TAG, "[===audio] stop finish");
        }
    };

    @Override
    public void release() {
        
    }

    public void setAudioEnabled(boolean isEnableAudio) {
        isSendingAudio = isEnableAudio;
    }
}
