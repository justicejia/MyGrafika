package com.android.grafika.baidu.recorder.hw.device;

import com.android.grafika.baidu.recorder.hw.encoder.AudioEncoder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Created by andy on 5/3/16.
 */
public class AudioCaptureDevice {
    private static final String TAG = "AudioCaptureDevice";
    private AudioEncoder mEncoder = null;
    private byte[] mAudioBuffer = null;
    private AudioRecord mRecorder = null;
    private int mChannelCount = 2;
    private int mSampleRate = 44100;
    private int mAudioFormat = 8;
    private volatile boolean isSendingAudio = true;
    private long mPresentationTimeNs = 0;

    // use worker thread to get audio packet.
    private Thread aworker;
    private volatile boolean aloop;

    public AudioCaptureDevice(AudioEncoder encoder) {
        mEncoder = encoder;
        isSendingAudio = true;
    }

    public void setEncoder(AudioEncoder encoder) {
        mEncoder = encoder;
    }

    private void setupDevice() {
        int[] sampleRates = {44100, 22050, 11025};
        for (int sampleRate : sampleRates) {
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;

            int bSamples = 8;
            if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                bSamples = 16;
            }

            int nChannels = 2;
            if (channelConfig == AudioFormat.CHANNEL_CONFIGURATION_MONO) {
                nChannels = 1;
            }

            //int bufferSize = 2 * bSamples * nChannels / 8;
            int bufferSize = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);

            if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "initialize the mic failed.");
                continue;
            }

            mSampleRate = sampleRate;
            mAudioFormat = audioFormat;
            mChannelCount = nChannels;
            mRecorder = audioRecorder;
            mAudioBuffer = new byte[Math.min(4096, bufferSize)];
            Log.i(TAG, String.format("mic open rate=%dHZ, channels=%d, bits=%d, buffer=%d/%d, state=%d",
                    sampleRate, nChannels, bSamples, bufferSize, mAudioBuffer.length, audioRecorder.getState()));
            break;
        }
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getChannelCount() {
        return mChannelCount;
    }

    public int getAudioFormat() {
        return mAudioFormat;
    }

    public boolean openRecorder() {
        setupDevice();
        // start audio worker thread.
        aworker = new Thread(new Runnable() {
            @Override
            public void run() {
                fetchAudioFromDevice();
            }
        });
        Log.i(TAG, "start audio worker thread.");
        mRecorder.startRecording();
        aloop = true;
        aworker.start();
        return true;
    }

    public void setAudioEnabled(boolean flag) {
        isSendingAudio = flag;
    }

    private void fetchAudioFromDevice() {
        while (aloop && mRecorder != null && !Thread.interrupted()) {
            if (!isSendingAudio) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {}
                continue;
            }
            int size = mRecorder.read(mAudioBuffer, 0, mAudioBuffer.length);
            if (size <= 0) {
                Log.i(TAG, "audio ignore, no data to read.");
                continue;
            }

            byte[] audio = new byte[size];
            System.arraycopy(mAudioBuffer, 0, audio, 0, size);

            if (mEncoder != null && isSendingAudio) {
                // pts of input sample for mediacodec should be in us
                mEncoder.push(audio, (System.nanoTime() - mPresentationTimeNs) / 1000);
            }
        }
    }

    public void setEpoch(long pts) {
        mPresentationTimeNs = pts;
    }

    public void closeRecorder() {
        aloop = false;
        mRecorder.setRecordPositionUpdateListener(null);
        mRecorder.stop();
        if (aworker != null) {
            Log.i(TAG, "stop audio worker thread");
            aworker.interrupt();
            try {
                aworker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            aworker = null;
        }
    }

    public void release() {

    }
}
