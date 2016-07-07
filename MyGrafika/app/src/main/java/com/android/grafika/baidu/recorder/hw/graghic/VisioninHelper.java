package com.android.grafika.baidu.recorder.hw.graghic;

import com.visionin.gpu.GPU;

import android.util.Log;

public class VisioninHelper {

    private static final String TAG = "VisioninHelper";

    private static final int kGPUImageNoRotation = 0;
    private static final int kGPUImageRotateLeft = 1;
    private static final int kGPUImageRotateRight = 2;
    private static final int kGPUImageFlipVertical = 3;
    private static final int kGPUImageFlipHorizonal = 4;
    private static final int kGPUImageRotateRightFlipVertical = 5;
    private static final int kGPUImageRotateRightFlipHorizontal = 6;
    private static final int kGPUImageRotate180 = 7; 

    /**
     * get rotation parameter for GPU processor
     * @param cameraid: 0 for back camera, 1 for front camera
     * @param orientation: 0 for portrait, 1 for landscape
     * @param needMirror: whether to mirror the image or not
     * @return
     */
    private static int getRotate(int cameraid, int orientation, boolean needMirror) {
        int rotate = kGPUImageNoRotation;
        if (orientation == 0 && cameraid == 0) {
            rotate = kGPUImageRotateRightFlipHorizontal;
        } else if (orientation == 0 && cameraid == 1) {
            rotate = kGPUImageRotateRight;
            if (!needMirror) {
                rotate = kGPUImageRotateRightFlipVertical;
            }
        } else if (orientation == 1 && cameraid == 0) {
            rotate = kGPUImageFlipVertical;
        } else if (orientation == 1 && cameraid == 1) {
            rotate = kGPUImageRotate180;
            if (!needMirror) {
                rotate = kGPUImageFlipVertical;
            }
        }
        return rotate;
    }
    
    public static void setInputSize(int frameWidth, int frameHeight, int cameraId, int orientation, boolean needMirror) {
        int rotate = getRotate(cameraId, orientation, needMirror);
        if (orientation == 0) {
//            Log.d(TAG, String.format("Current input zise is [%dx%d, %d]", frameHeight, frameWidth, rotate));
            GPU.setRotate(frameHeight, frameWidth, rotate);
        } else {
//            Log.d(TAG, String.format("Current input zise is [%dx%d, %d]", frameWidth, frameHeight, rotate));
            GPU.setRotate(frameWidth, frameHeight, rotate);
        }
    }
    
    public static int createTexture() {
        return GPU.createTexture();
    }
    
    public static void start() {
        GPU.start();
    }
    
    public static void stop() {
        GPU.stop();
    }
    
    public static int process(int textureId) {
        return GPU.process(textureId);
    }
    
    public static int createOutput(int width, int height) {
        Log.d(TAG, String.format("Current output size is %dx%d", width, height));
        return GPU.createOutput(width, height);
    }
    
    public static void setOutput(int outputIdex) {
        GPU.setOutput(outputIdex);
    }
    
    public static void setBeautyEffect(float bright, float smooth, float pink) {
        GPU.setBrightenLevel(bright);
        GPU.setSmoothLevel(smooth);
        GPU.setToningLevel(pink);
    }
    
}
