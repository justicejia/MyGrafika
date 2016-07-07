package com.android.grafika.baidu.recorder.api;

import com.android.grafika.baidu.recorder.hw.device.VideoCaptureDevice;
import com.android.grafika.baidu.recorder.jni.BRecorderJNI.AudioSampleFormat;
import com.android.grafika.baidu.recorder.jni.BRecorderJNI.VideoFrameFormat;
import com.android.grafika.baidu.recorder.sw.bean.AudioParams;
import com.android.grafika.baidu.recorder.sw.bean.VideoParams;
import com.android.grafika.baidu.recorder.sw.controller.AudioRecordDevice;
import com.android.grafika.baidu.recorder.sw.controller.BRecorderJNIWrapper;
import com.android.grafika.baidu.recorder.sw.controller.IRecorder;
import com.android.grafika.baidu.recorder.sw.controller.VideoRecordDevice;
import com.android.grafika.baidu.recorder.util.ScreenUtils;
import com.android.grafika.baidu.recorder.util.VideoUtil;

import android.content.Context;
import android.hardware.Camera;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * LiveSession 为推流SDK的接口类， 该SDK包含音视频采集、编码、推流等功能模块， 并为用户提供友好的编程接口。
 * 
 * 默认情况下，该SDK提供的视频编码参数为720p@24fps，码率为1024kbps 音频编码参数为双声道、采样率为44.1khz，码率为64kbps
 * 
 * 需要注意的是，本SDK不负责对预览窗口进行按比例适配，画面将全屏填充预览SurfaceView，
 * 用户需保证预览SurfaceView的高宽与推流视频的高宽比例保持一致，以免出现预览时变形的问题
 * 
 * @author Andy Young
 *
 */
public class LiveSessionSW extends LiveSession {

    private static final String TAG = "LiveSession";
    private static final int MIN_VIDEO_BITRATE_BY_BITS_PER_SEC = 100000;
    private IRecorder mRecorder = null;
    private AudioRecordDevice mAudioRecordDevice = null;
    private VideoRecordDevice mVideoRecordDevice = null;
    private VideoUtil mVideoKit = null;
    private AudioParams mAudioParams = null;
    private VideoParams mVideoParams = null;
    private Context mContext = null;
    private SessionStateListener mStateListener = null;
    private boolean isSessionPrepared = false;
    private boolean isSessionStarted = false;
    private int mZoomFactor = 0;
    private static final String NAME_OF_LIB_FFMPEG = "libffmpeg.so";
    private static final String NAME_OF_LIB_RECORDER = "librecorder.so";
    private static boolean isLibFFmpegLoaded = false;
    private static boolean isLibRecorderLoaded = false;

    /**
     * 手动加载库文件接口，满足客户动态加载库文件需求，以减小apk体积
     * @param path 库文件的绝对路径，
     *             请使用context.getDir("libs", Context.MODE_PRIVATE)方法获取库文件所在文件夹路径
     * @return
     */
    private static boolean loadNativeLibraryByPath(String path) {
        try {
            Log.d(TAG, "Loading libraries from " + path);
            System.load(path);
            if (path.indexOf(NAME_OF_LIB_FFMPEG) >= 0) {
                isLibFFmpegLoaded = true;
            }
            if (path.indexOf(NAME_OF_LIB_RECORDER) >= 0) {
                isLibRecorderLoaded = true;
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private void loadLibrariesByDefaultPath() {
        try {
            Log.d(TAG, "Loading libraries for BRecorder...");
            if (!isLibFFmpegLoaded) {
                System.loadLibrary("ffmpeg");
            }
            if (!isLibRecorderLoaded) {
                System.loadLibrary("recorder");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 初始化函数 使用该初始化函数，将使用默认音视频编码参数
     * 同时默认使用后置摄像头采集数据
     * @param cxt
     */
    public LiveSessionSW(Context cxt) {
        super(cxt);
        loadLibrariesByDefaultPath();
        mContext = cxt;
        isSessionPrepared = false;
        isSessionStarted = false;
        mZoomFactor = 0;
        mAudioParams = new AudioParams();
        mAudioRecordDevice = new AudioRecordDevice(mAudioParams);
        mVideoParams = new VideoParams();
        mVideoRecordDevice = new VideoRecordDevice(cxt, mVideoParams);
    }

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
    public LiveSessionSW(Context cxt, int width, int height, int fps, int bitrate) {
        this(cxt);
        try {
            checkInitParams(width, height, fps, bitrate, Camera.CameraInfo.CAMERA_FACING_BACK);
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
            return;
        }
        mVideoParams.setWidth(width);
        mVideoParams.setHeight(height);
        mVideoParams.setDefaultFps(fps);
        mVideoParams.setBitrate(bitrate);
    }

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
    public LiveSessionSW(Context cxt, int width, int height, int fps, int bitrate, int cameraId) {
        this(cxt);
        try {
            checkInitParams(width, height, fps, bitrate, cameraId);
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
            return;
        }
        mVideoParams.setWidth(width);
        mVideoParams.setHeight(height);
        mVideoParams.setDefaultFps(fps);
        mVideoParams.setBitrate(bitrate);
        mVideoParams.setCameraId(cameraId);
    }

    public VideoCaptureDevice getDevice(){
        return null;
    }

    /**
     * 检测初始化参数，遇到不合法的参数将抛异常
     * 
     * @param width
     *            value > 0
     * @param height
     *            value > 0
     * @param fps
     *            value in [1, 30]
     * @param bitrate
     *            value >= 100000
     * @param cameraId
     *            value = Camera.CameraInfo.CAMERA_FACING_FRONT or
     *            Camera.CameraInfo.CAMERA_FACING_BACK
     * @throws Throwable
     */
    private void checkInitParams(int width, int height, int fps, int bitrate, int cameraId) throws Throwable {
        if (width < 0 || height < 0 || (fps < 0 || fps > 30)
                || bitrate < MIN_VIDEO_BITRATE_BY_BITS_PER_SEC
                || (cameraId != Camera.CameraInfo.CAMERA_FACING_FRONT
                && cameraId != Camera.CameraInfo.CAMERA_FACING_BACK)) {
            throw (new Throwable("Illigal parameters error!"));
        }
    }

    /**
     * 绑定预览Surface接口 该接口必须在prepareSessionAsync前被调用，否则无法启动相机
     * 
     * @param surfaceHolder
     */
    public void bindPreviewDisplay(SurfaceHolder surfaceHolder) {
        mVideoRecordDevice.bindingSurface(surfaceHolder);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    /**
     * 设置推流Session状态订阅接口
     * 
     * @param listener
     */
    public void setStateListener(SessionStateListener listener) {
        mStateListener = listener;
        mAudioRecordDevice.setStateListener(listener);
        mVideoRecordDevice.setStateListener(listener);
    }
    
    /**
     * 判断是否支持动态切换摄像头
     * 
     * @return 支持返回true，否则返回false
     */
    public boolean canSwitchCamera() {
        return mVideoRecordDevice.canSwitchCamera();
    }

    /**
     * 切换摄像头接口
     * 
     * @param cameraId
     */
    public void switchCamera(int cameraId) {
        mVideoRecordDevice.switchCamera(cameraId);
        if (mVideoKit != null) {
            mVideoKit.setIsCameraFront(cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT);
        }
        mZoomFactor = 0;
    }

    /**
     * 开关闪光灯接口
     * 
     * @param flag
     */
    public void toggleFlash(boolean flag) {
        mVideoRecordDevice.setFlashFlag(flag);
    }

    /**
     * 重新自动对焦并指定焦点
     * 
     * @param x
     * @param y
     */
    public void focusToPosition(int x, int y) {
        mVideoRecordDevice.focusToPoint(x, y);
    }
    
    /**
     * 放大相机画面（每次将画面放大一级）
     * @return 操作是否成功
     */
    public boolean zoomInCamera() {
        boolean hasFactorSet = mVideoRecordDevice.setZoomFactor(mZoomFactor + 1);
        if (hasFactorSet) {
            ++mZoomFactor;
        }
        return hasFactorSet;
    }
    
    /**
     * 缩小相机画面（每次将画面缩小一级）
     * @return 操作是否成功
     */
    public boolean zoomOutCamera() {
        boolean hasFactorSet = mVideoRecordDevice.setZoomFactor(mZoomFactor - 1);
        if (hasFactorSet) {
            --mZoomFactor;
        }
        return hasFactorSet;
    }
    
    /**
     * 设置相机画面放大级别
     * @param 目标放大级别
     * @return 设置是否成功
     */
    public boolean setCameraZoomLevel(int zoomlevel) {
        boolean hasFactorSet = mVideoRecordDevice.setZoomFactor(zoomlevel);
        if (hasFactorSet) {
            mZoomFactor = zoomlevel;
        }
        return hasFactorSet;
    }
    
    /**
     * 取消相机放大效果
     */
    public void cancelZoomCamera() {
        boolean hasFactorSet = mVideoRecordDevice.setZoomFactor(0);
        if (hasFactorSet) {
            mZoomFactor = 0;
        }
    }
    
    /**
     * 获取当前的相机画面的放大级别
     * @return
     */
    public int getCurrentZoomFactor() {
        return mZoomFactor;
    }
    
    /**
     * 获取相机所支持的最大放大级别
     * @return
     */
    public int getMaxZoomFactor() {
        return mVideoRecordDevice.getMaxZoomFactor();
    }
    
    /**
     * 判断当前摄像头是否支持给定的分辨率
     * @param width 输入分辨率的宽
     * @param height 输入分辨率的高
     * @return
     */
    public boolean isPreviewSizeSupported(int width, int height) {
        return mVideoRecordDevice.isPreviewSizeSupported(width, height);
    }
    
    /**
     * 获取实际采集到的视频的高
     * @return
     */
    public int getAdaptedVideoHeight() {
        return mVideoParams.getHeight();
    }

    /**
     * 获取实际采集到的视频的宽
     * @return
     */
    public int getAdaptedVideoWidth() {
        return mVideoParams.getWidth();
    }
    
    /**
     * 获取当前实时上传带宽，单位：KBps
     * @return
     */
    public double getCurrentUploadBandwidthKbps() {
        if (this.mRecorder != null) {
            if (mRecorder instanceof BRecorderJNIWrapper) {
                return ((BRecorderJNIWrapper)mRecorder).getCurrentUploadBandwidthKbps();
            }
        }
        return 0;
    }

    /**
     * 该接口完成音视频采集设备的初始化，调用该接口后，用户可对拍摄画面进行预览
     */
    public void prepareSessionAsync() {
        if (isSessionPrepared) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!mAudioRecordDevice.open()
                        || !mVideoRecordDevice.open()) {
                    if (mStateListener != null) {
                        mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_PREPARE_SESSION_FAILED);
                    }
                    return;
                }
                initVideoUtil();
                mVideoRecordDevice.setVideoUtil(mVideoKit);
                initRecorder();

                isSessionPrepared = true;
                if (mStateListener != null) {
                    mStateListener.onSessionPrepared(SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED);
                }
            }
        }).start();
    }
    
    private void initVideoUtil() {
        mVideoKit = new VideoUtil();
        mVideoKit.setIsCameraFront(mVideoParams.getCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT);
        boolean isPortrait = !ScreenUtils.screenIsLanscape(mContext);
        mVideoKit.setIsOrientationPortrait(isPortrait);
        mVideoKit.setVideoSize(mVideoParams.getWidth(), mVideoParams.getHeight());
    }
    
    private void initRecorder() {
        mRecorder = new BRecorderJNIWrapper();
        mRecorder.setStateListener(mStateListener);
        mRecorder.setAudioParams(AudioSampleFormat.AudioSampleFormatS16.ordinal(),
                                 mAudioParams.getnChannels(),
                                 mAudioParams.getSampleRateInHz(),
                                 64 * 1000);
        mRecorder.setVideoParams(VideoFrameFormat.VideoFrameFormatNV21.ordinal(),
                                 mVideoKit.getTargetVideoWidth(),
                                 mVideoKit.getTargetVideoHeight(),
                                 mVideoParams.getBitrate());
    }

    /**
     * 该接口将建立与服务器连接并开始推流，该接口会使用到网络，在子线程中执行，注意回调参数值，小于0表示失败，否则表示成功
     * 
     * @param url
     *            推流地址
     * @return
     */
    public boolean startRtmpSession(final String url) {
        if (!isSessionPrepared || isSessionStarted || TextUtils.isEmpty(url)) {
            return false;
        }
        Log.d(TAG, "Starting RtmpSession...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ret = mRecorder.open(url);
                if (ret < 0) {
                    if (mStateListener != null) {
                        mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_CONNECT_TO_SERVER_FAILED);
                    }
                } else {
                    mAudioRecordDevice.setBRecorder(mRecorder);
                    mVideoRecordDevice.setBRecorder(mRecorder);
                    isSessionStarted = true;
                    if (mStateListener != null) {
                        mStateListener.onSessionStarted(SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED);
                    }
                }
            }
        }).start();
        return true;
    }

    /**
     * 该接口将断开推流连接，停止向服务器发送数据，在子线程中执行，注意回调参数值，小于0表示失败，否则表示成功
     * 
     * @return
     */
    public boolean stopRtmpSession() {
        if (!isSessionPrepared || !isSessionStarted) {
            return false;
        }
        Log.d(TAG, "Stopping RtmpSession...");
        Thread tmpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Stopping mRecorder...");
                int ret = mRecorder.close();
                Log.d(TAG, "mRecorder was stopped...");
                isSessionStarted = false;
                if (mAudioRecordDevice != null) {
                    mAudioRecordDevice.setBRecorder(null);
                }
                if (mVideoRecordDevice != null) {
                    mVideoRecordDevice.setBRecorder(null);
                }
                if (mStateListener != null) {
                    if (ret < 0) {
                        mStateListener
                                .onSessionError(SessionStateListener.ERROR_CODE_OF_DISCONNECT_FROM_SERVER_FAILED);
                    } else {
                        mStateListener.onSessionStopped(SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED);
                    }
                }
            }
        });
        tmpThread.start();
        return true;
    }

    /**
     * 停止音视频采集模块
     */
    public void destroyRtmpSession() {
        if (!isSessionPrepared) {
            return;
        }
        Log.d(TAG, "Destroying RtmpSession...");
        isSessionPrepared = false;
        if (mVideoRecordDevice != null) {
            mVideoRecordDevice.close();
            mVideoRecordDevice.release();
            mVideoRecordDevice = null;
        }
        if (mAudioRecordDevice != null) {
            mAudioRecordDevice.close();
            mAudioRecordDevice.release();
            mVideoRecordDevice = null;
        }
        mRecorder.release();
        mRecorder = null;
    }

    public void setAudioEnabled(boolean isEnableAudio) {
        if (mAudioRecordDevice != null) {
            mAudioRecordDevice.setAudioEnabled(isEnableAudio);
        }
    }

    public void setVideoEnabled(boolean isEnableVideo) {
        if (mVideoRecordDevice != null) {
            mVideoRecordDevice.setVideoEnabled(isEnableVideo);
        }
    }
}
