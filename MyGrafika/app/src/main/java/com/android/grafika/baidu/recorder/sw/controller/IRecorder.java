package com.android.grafika.baidu.recorder.sw.controller;

import com.android.grafika.baidu.recorder.api.SessionStateListener;

public interface IRecorder {

    public int open(String url);

    public void useAudio(boolean hasAudio);

    public int close();

    public void release();

    public int feedingVideoFrame(byte[] pData, long numBytes, long timestamp);

    public int feedingAudioFrame(byte[] samples, long numSamples, long timestamp);

    public void setVideoParams(int fmt, int width, int height, long bitrate);

    public void setAudioParams(int fmt, int channels, long samplerate, long bitrate);

    public void setStartTime(long timestamp);

    public long getStartTime();

    public void setStateListener(SessionStateListener listener);
}
