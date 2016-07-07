package com.android.grafika.baidu.recorder.sw.bean;

import java.io.Serializable;

import android.graphics.ImageFormat;
import android.hardware.Camera;

/**
 * 设置摄像头参数，必须在摄像头开启设置好
 * 
 * @author Andy Young @ Baidu.com
 *
 */
public class VideoParams implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 默认最小fps(实际fps会根据手机不同会不一样)
     */
    private int minFps = 15000;
    /**
     * 默认最大fps(实际fps会根据手机不同会不一样)
     */
    private int defautFps = 24000;
    private int width = 1280;
    private int height = 720;

    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private String flashMode = Camera.Parameters.FLASH_MODE_OFF;
    private String whiteBalance = Camera.Parameters.WHITE_BALANCE_AUTO;
    private String sceneMode = Camera.Parameters.SCENE_MODE_AUTO;
    private String focusMode = Camera.Parameters.FOCUS_MODE_AUTO;
    private int[] fps = { minFps, defautFps };

    private int previewFormat = ImageFormat.NV21;
    private int pictureFormat = ImageFormat.JPEG;
    private int previewOrientation = 0;
    private int bitrate = 1024000;

    /**
     * 获取当前摄像头 默认Camera.CameraInfo.CAMERA_FACING_BACK
     * 
     * @return int Camera.CameraInfo.CAMERA_FACING_BACK
     * @return int Camera.CameraInfo.CAMERA_FACING_FRONT
     */
    public int getCameraId() {
        return cameraId;
    }

    /**
     * 设置摄像头
     * 
     * @param cameraId
     *            摄像头id，参考Camera.CameraInfo
     */
    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

    /**
     * 获取闪光灯模式 默认Camera.Parameters.FLASH_MODE_OFF
     * 
     * @return
     */
    public String getFlashMode() {
        return flashMode;
    }

    /**
     * 设置闪光灯模式
     * 
     * @param flashMode，设置闪光灯模式
     */
    public void setFlashMode(String flashMode) {
        this.flashMode = flashMode;
    }

    /**
     * 获取白平衡 默认Camera.Parameters.WHITE_BALANCE_AUTO
     * 
     * @return 返回白平衡参数
     */
    public String getWhiteBalance() {
        return whiteBalance;
    }

    /**
     * 设置白平衡
     * 
     * @param whiteBalance
     *            参考Camera.Parameters
     */
    public void setWhiteBalance(String whiteBalance) {
        this.whiteBalance = whiteBalance;
    }

    /**
     * 获取情景模式 默认Camera.Parameters.SCENE_MODE_AUTO
     * 
     * @return 获取情景模式
     */
    public String getSceneMode() {
        return sceneMode;
    }

    /**
     * 设置情景模式
     * 
     * @param sceneMode
     *            参考Camera.Parameters
     */
    public void setSceneMode(String sceneMode) {
        this.sceneMode = sceneMode;
    }

    /**
     * 获取摄像头返回的数据格式，默认是ImageFormat.NV21
     * 
     * @return
     */
    public int getPreviewFormat() {
        return previewFormat;
    }

    /**
     * 设置摄像头返回的数据格式
     */
    public void setPreviewFormat(int pf) {
        previewFormat = pf;
    }

    /**
     * 获取摄像头拍照的数据格式，默认是ImageFormat.JPEG
     * 
     * @return
     */
    public int getPictureFormat() {
        return pictureFormat;
    }

    /**
     * 返回对焦模式 默认Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
     * 
     * @return
     */
    public String getFocusMode() {
        return focusMode;
    }

    /**
     * 设置对焦模式
     * 
     * @param focusMode
     *            参考Camera.Parameters
     */
    public void setFocusMode(String focusMode) {
        this.focusMode = focusMode;
    }

    /**
     * 返回摄像头旋转角度，默认横屏，则返回0
     * 
     * @return 返回摄像头旋转角度
     */
    public int getPreviewOrientation() {
        return previewOrientation;
    }

    /**
     * 设置摄像头旋转角度
     * 
     * @param previewOrientation
     */
    public void setPreviewOrientation(int previewOrientation) {
        this.previewOrientation = previewOrientation;
    }

    /**
     * 获取当前摄像头预览的分辨率
     * 
     * @return 返回分辨率宽
     */
    public int getWidth() {
        return width;
    }

    /**
     * 设置摄像头预览分辨率
     * 
     * @param width
     *            设置宽
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * 获取当前摄像头预览的分辨率
     * 
     * @return 返回分辨率高
     */
    public int getHeight() {
        return height;
    }

    /**
     * 设置摄像头预览分辨率
     * 
     * @param height
     *            设置高
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * 获取摄像头当前设置的fps
     * 
     * @return
     */
    public int[] getFps() {
        return fps;
    }

    /**
     * 设置摄像头fps
     * 
     * @param fps
     */
    public void setDefaultFps(int fps) {
        this.defautFps = fps;
    }

    /**
     * 获取摄像头当前设置的fps
     * 
     * @return
     */
    public int getDefaultFps() {
        return defautFps;
    }

    /**
     * 设置摄像头fps
     * 
     * @param fps
     */
    public void setFps(int[] fps) {
        this.fps = fps;
    }

    /**
     * 获取当前编码码率
     * 
     * @return 返回编码码率
     */
    public int getBitrate() {
        return bitrate;
    }

    /**
     * 设置当前编码码率
     */
    public void setBitrate(int br) {
        bitrate = br;
    }

}
