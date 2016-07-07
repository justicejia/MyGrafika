package com.android.grafika.baidu.recorder.hw.rtmp;

import com.android.grafika.baidu.recorder.jni.RtmpHelperJNI;

import android.util.Log;

public class RtmpSocket {
    private static final String TAG = "RtmpSocket";
    private boolean isConnected = false;
    private RtmpConnectedListener mListener = null;

    public RtmpSocket() {
        RtmpHelperJNI.setup();
        isConnected = false;
        mListener = null;
    }

    public void release() {
        RtmpHelperJNI.release();
        isConnected = false;
        mListener = null;
    }

    public void setOnConnectedListener(RtmpConnectedListener listener) {
        mListener = listener;
    }

    public int connectAsync(final String target_url) {
        new Thread(new Runnable(){
            @Override
            public void run() {
                int ret = RtmpHelperJNI.connect(target_url);
                if (ret >= 0) {
                    isConnected = true;
                    if (mListener != null)
                        mListener.onConnected();
                }
            }}).start();
        return 0;
    }

    public int connect(final String target_url) {
        int ret = RtmpHelperJNI.connect(target_url);
        if (ret >= 0) {
            isConnected = true;
        }
        return ret;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public int sendAVCSPSnPPS(byte[] sps, int sps_len, byte[] pps, int pps_len, long pts) {
        return RtmpHelperJNI.sendAVCSPSnPPS(sps, sps_len, pps, pps_len, pts);
    }

    public int sendVideoPacket(byte[] buf, int len, long pts) {
        return RtmpHelperJNI.sendVideoPacket(buf, len, pts);
    }

    public int sendAACSpec(byte[] spec, int len, long pts) {
        return RtmpHelperJNI.sendAACSpec(spec, len, pts);
    }

    public int sendAudioPacket(byte[] buf, int len, long pts) {
        return RtmpHelperJNI.sendAudioPacket(buf, len, pts);
    }

    public int sendRTMPPacket(byte[] buf, int len, long pts, int frame_type) {
        int ret = RtmpHelperJNI.sendRTMPPacket(buf, len, pts, frame_type);
        if (ret <= 0) {
            Log.i(TAG, "Streaming failed with result: "+ret);
        }
        return ret;
    }

    public int writeRTMPPacket(byte[] buf, int len) {
        return RtmpHelperJNI.writeRTMPPacket(buf, len);
    }

    public int setChunkSize(int size) {
        return RtmpHelperJNI.setChunkSize(size);
    }

}
