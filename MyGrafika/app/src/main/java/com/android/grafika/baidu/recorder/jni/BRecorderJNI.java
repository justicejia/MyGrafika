package com.android.grafika.baidu.recorder.jni;

import com.android.grafika.baidu.recorder.api.SessionStateListener;

import android.util.Log;

public final class BRecorderJNI {

    private static final String TAG = "FFRecorder";
    private SessionStateListener mSessionStateListener;

    public BRecorderJNI() {
        setup();
    }

    public void postNativeError(int err) {
        System.out.println("sourceData:" + "postNativeError");
        Log.d(TAG, "Received native error, errno is : " + err);
        int code = SessionStateListener.ERROR_CODE_OF_UNKNOWN_STREAMING_ERROR;
        switch (err) {
            case -32: // Server refused!
                code = SessionStateListener.ERROR_CODE_OF_PACKET_REFUSED_BY_SERVER;
                break;
            case -35: // Network is poor!
                code = SessionStateListener.ERROR_CODE_OF_WEAK_CONNECTION;
                break;
            case -104: // Server error!
                code = SessionStateListener.ERROR_CODE_OF_SERVER_INTERNAL_ERROR;
                break;
            case -110: // Connection timeout!
                code = SessionStateListener.ERROR_CODE_OF_CONNECTION_TIMEOUT;
                break;
            default:
                break;
        }
        if (mSessionStateListener != null) {
            mSessionStateListener.onSessionError(code);
        }
    }

    public void setListener(SessionStateListener listener) {
        mSessionStateListener = listener;
    }

    public native void setup();

    public native void release();

    public native int setVideoOptions(int fmt, int width, int height, long bitrate);

    public native int setAudioOptions(int fmt, int channels, long samplerate, long bitrate);

    public native int open(String file, Boolean hasAudio);

    public native int close();

    public native int supplyVideoFrame(byte[] pData, long numBytes, long timestamp);

    public native int supplyAudioSamples(byte[] samples, long numSamples, long timestamp);
    
    public native double getCurrentUploadBandwidthKbps();

    public enum VideoFrameFormat {
        VideoFrameFormatYUV420P,
        VideoFrameFormatYUV420V,
        VideoFrameFormatYUV420F,
        VideoFrameFormatNV12,
        VideoFrameFormatNV21,
        VideoFrameFormatRGB24,
        VideoFrameFormatBGR24,
        VideoFrameFormatARGB,
        VideoFrameFormatRGBA,
        VideoFrameFormatABGR,
        VideoFrameFormatBGRA,
        VideoFrameFormatRGB565LE,
        VideoFrameFormatRGB565BE,
        VideoFrameFormatBGR565LE,
        VideoFrameFormatBGR565BE,
        VideoFrameFormatMax
    }

    public enum AudioSampleFormat {
        AudioSampleFormatU8,
        AudioSampleFormatS16,
        AudioSampleFormatS32,
        AudioSampleFormatFLT,
        AudioSampleFormatDBL,
        AudioSampleFormatMax
    }
}