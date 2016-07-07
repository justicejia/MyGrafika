package com.android.grafika;

import android.content.Context;
import android.view.SurfaceHolder;

import com.android.grafika.baidu.recorder.hw.device.VideoCaptureDevice;

public abstract class LiveSession {


    
    /**
     * 初始化函数 使用该初始化函数，将使用默认音视频编码参数
     * 同时默认使用后置摄像头采集数据
     * @param cxt
     */
    public LiveSession(Context ctx) {}

    /**
     * 初始化函数 使用该初始化函数，可设置推流视频的基本信息 注意：如果设备不支持用户设置的分辨率，SDK将从设备支持的分辨率中选择一个最接近的分辨率值
     * 强烈建议用户使用常用分辨率值进行设置，以免出现异常情况
     * 
     * @param cxt
     * @param width
     *            视频宽度
     * @param height
     *            视频高度
     * @param fps
     *            视频帧率
     * @param bitrate
     *            视频编码码率
     */
    public LiveSession(Context ctx, int width, int height, int fps, int bitrate) {}

    /**
     * 初始化函数 使用该初始化函数，用户不仅可以设置推流视频的基本信息，还可选定视频采集所使用的摄像头
     * 
     * @param cxt
     * @param width
     *            视频宽度
     * @param height
     *            视频高度
     * @param fps
     *            视频帧率
     * @param bitrate
     *            视频编码码率
     * @param cameraId
     *            摄像头ID
     */
    public LiveSession(Context ctx, int width, int height, int fps, int bitrate, int cameraId) {}

    /**
     * 绑定预览Surface接口 该接口必须在prepareSessionAsync前被调用，否则无法启动相机
     * 
     * @param surfaceHolder
     */


    public abstract void bindPreviewDisplay(SurfaceHolder surfaceHolder);

  
    
    /**
     * 判断是否支持动态切换摄像头
     * 
     * @return 支持返回true，否则返回false
     */
    public abstract boolean canSwitchCamera();

    /**
     * 切换摄像头接口
     * 
     * @param cameraId
     */
    public abstract void switchCamera(int cameraId);

    /**
     * 开关闪光灯接口
     * 
     * @param flag
     */
    public abstract void toggleFlash(boolean isFlashOn);

    /**
     * 重新自动对焦并指定焦点
     * 
     * @param x
     * @param y
     */
    public abstract void focusToPosition(int x, int y);
    
    /**
     * 放大相机画面（每次将画面放大一级）
     * @return 操作是否成功
     */
    public abstract boolean zoomInCamera();
    
    /**
     * 缩小相机画面（每次将画面缩小一级）
     * @return 操作是否成功
     */
    public abstract boolean zoomOutCamera();
    
    /**
     * 设置相机画面放大级别
     * @param zoom_level 目标放大级别
     * @return 设置是否成功
     */
    public abstract boolean setCameraZoomLevel(int level);
    
    /**
     * 取消相机放大效果
     */
    public abstract void cancelZoomCamera();
    
    /**
     * 获取当前的相机画面的放大级别
     * @return
     */
    public abstract int getCurrentZoomFactor();
    
    /**
     * 获取相机所支持的最大放大级别
     * @return
     */
    public abstract int getMaxZoomFactor();
    
    /**
     * 判断当前摄像头是否支持给定的分辨率
     * @param width 输入分辨率的宽
     * @param height 输入分辨率的高
     * @return
     */
    public abstract boolean isPreviewSizeSupported(int width, int height);
    
    /**
     * 获取实际采集到的视频的高
     * @return
     */
    public abstract int getAdaptedVideoHeight();

    /**
     * 获取实际采集到的视频的宽
     * @return
     */
    public abstract int getAdaptedVideoWidth();
    
    /**
     * 获取当前实时上传带宽，单位：KBps
     * @return
     */
    public abstract double getCurrentUploadBandwidthKbps();

    /**
     * 该接口完成音视频采集设备的初始化
     */
    public abstract void prepareSessionAsync();

    /**
     * 该接口将建立与服务器连接并开始推流，该接口会使用到网络，在子线程中执行，注意回调参数值，小于0表示失败，否则表示成功
     * 
     * @param url
     *            推流地址
     * @return
     */
    public abstract boolean startRtmpSession(String url);

    /**
     * 该接口将断开推流连接，停止向服务器发送数据，在子线程中执行，注意回调参数值，小于0表示失败，否则表示成功
     * 
     * @return
     */
    public abstract boolean stopRtmpSession();

    /**
     * 停止音视频采集模块
     */
    public abstract void destroyRtmpSession();
    
    /**
     * 开启或关闭音频推流接口
     * @param isEnableAudio
     */
    public abstract void setAudioEnabled(boolean isEnableAudio);
    
    /**
     * 开启或关闭视频推流接口
     * @param isEnableVideo
     */
    public abstract void setVideoEnabled(boolean isEnableVideo);

    public abstract VideoCaptureDevice getDevice();
}
