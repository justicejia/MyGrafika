package com.android.grafika.baidu.recorder.sw.controller;

import com.android.grafika.baidu.recorder.api.SessionStateListener;

public abstract class RecordDevices {
    
    protected SessionStateListener mStateListener;
    
    public abstract boolean open();
    
    public abstract void close();
    
    public abstract void reset();
    
    public abstract void release();
    
    public void setStateListener(SessionStateListener listener) {
        mStateListener = listener;
    }

}
