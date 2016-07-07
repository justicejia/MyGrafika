package com.android.grafika.baidu.recorder.jni;

import android.util.Log;

public final class BTransmitterJNI {

    private static final String TAG = "FFRecorder";

    public BTransmitterJNI(String target) {
        setup(target);
    }

    public void postNativeEvent(int err) {
        Log.d(TAG, "Received native error, errno is : " + err);
    }

    public native int open(String file);

    public native int close();

    public native void setup(String target);

    public native void release();
}