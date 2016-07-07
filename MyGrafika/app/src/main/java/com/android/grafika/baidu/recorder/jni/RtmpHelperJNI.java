package com.android.grafika.baidu.recorder.jni;

/**
 * JNI interface for librtmp
 * @author Andy Young 07-07-15
 *
 */
public class RtmpHelperJNI {
    public static native void setup();
    public static native void release();
    public static native int connect(String target_url);
    public static native int sendAVCSPSnPPS(byte[] sps, int sps_len, byte[] pps, int pps_len, long pts);
    public static native int sendVideoPacket(byte[] buf, int len, long pts);
    public static native int sendAACSpec(byte[] spec, int len, long pts);
    public static native int sendAudioPacket(byte[] buf, int len, long pts);
    public static native int sendRTMPPacket(byte[] buf, int len, long pts, int frame_type);
    public static native int writeRTMPPacket(byte[] buf, int len);
    public static native int setChunkSize(int size);
}
