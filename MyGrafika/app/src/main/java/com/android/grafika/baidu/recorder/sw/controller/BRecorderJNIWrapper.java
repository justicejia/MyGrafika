package com.android.grafika.baidu.recorder.sw.controller;

import com.android.grafika.baidu.recorder.api.SessionStateListener;
import com.android.grafika.baidu.recorder.jni.BRecorderJNI;
import com.android.grafika.baidu.recorder.util.VideoUtil;

public class BRecorderJNIWrapper implements IRecorder {

    private BRecorderJNI recorder;
    private boolean useAudio = true;
    private long recorderStartTime; // 推流开始时间
    private boolean isRecording = false;
    private VideoUtil mVideoUtil = null;

    public BRecorderJNIWrapper() {
        recorder = new BRecorderJNI();
    }

    public void setVideoUtil(VideoUtil vu) {
        mVideoUtil = vu;
    }

    @Override
    public int open(String url) {
        int ret = -1;

        if (recorder != null) {
            ret = recorder.open(url, useAudio);
        }
        setRecording(true);
        return ret;
    }

    @Override
    public void useAudio(boolean hasAudio) {
        this.useAudio = hasAudio;
    }

    @Override
    public int close() {
        int ret = -1;
        
        if (recorder != null && isRecording()) {
            ret = recorder.close();
        }
        setRecording(false);
        return ret;
    }

    @Override
    public void release() {
        if (recorder != null) {
            recorder.release();
        }
        recorderStartTime = 0;
    }

    byte[] tmpBufer = null;

    @Override
    public int feedingVideoFrame(byte[] pData, long numBytes, long timestamp) {
        if (recorder != null) {
            if (getStartTime() > 0) {
                tmpBufer = pData;
                if (mVideoUtil != null) {
                    tmpBufer = mVideoUtil.dealVideoFrame(pData);
                }
                return recorder.supplyVideoFrame(tmpBufer, numBytes, timestamp - getStartTime());
            }
        }
        return -1;
    }

    @Override
    public int feedingAudioFrame(byte[] samples, long numSamples, long timestamp) {
        if (recorder != null) {
            if (getStartTime() == 0) {
                setStartTime(timestamp);
            }
            return recorder.supplyAudioSamples(samples, numSamples, timestamp - getStartTime());
        }
        return -1;
    }

    @Override
    public void setVideoParams(int fmt, int width, int height, long bitrate) {
        if (recorder != null) {
            recorder.setVideoOptions(fmt, width, height, bitrate);
        }
    }

    @Override
    public void setAudioParams(int fmt, int channels, long samplerate, long bitrate) {
        if (recorder != null) {
            recorder.setAudioOptions(fmt, channels, samplerate, bitrate);
        }
    }

    @Override
    public void setStartTime(long timestamp) {
        this.recorderStartTime = timestamp;
    }

    @Override
    public long getStartTime() {
        return recorderStartTime;
    }

    public boolean isRecording() {
        return isRecording;
    }

    private void setRecording(boolean isRecording) {
        this.isRecording = isRecording;
    }

    @Override
    public void setStateListener(SessionStateListener listener) {
        if (recorder != null) {
            recorder.setListener(listener);
        }
    }

    public double getCurrentUploadBandwidthKbps() {
        return recorder == null ? 0 : recorder.getCurrentUploadBandwidthKbps();
    }

}
