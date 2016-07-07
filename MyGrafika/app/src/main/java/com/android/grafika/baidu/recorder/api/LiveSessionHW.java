package com.android.grafika.baidu.recorder.api;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import com.android.grafika.baidu.recorder.hw.device.AudioCaptureDevice;
import com.android.grafika.baidu.recorder.hw.device.VideoCaptureDevice;
import com.android.grafika.baidu.recorder.hw.encoder.AudioEncoder;
import com.android.grafika.baidu.recorder.hw.encoder.VideoEncoder;
import com.android.grafika.baidu.recorder.hw.muxer.FlvMuxer;
import com.android.grafika.baidu.recorder.hw.rtmp.RtmpSocket;
import com.android.grafika.baidu.recorder.util.ScreenUtils;
import com.visionin.gpu.Visionin;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaFormat;
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
public class LiveSessionHW extends LiveSession {

    private static final String TAG = "LiveSession";
    private static final int MIN_VIDEO_BITRATE_BY_BITS_PER_SEC = 100000;
    private RtmpSocket mRtmpSocket = null;
    private FlvMuxer mFlvMuxer = null;
    private AudioEncoder mAudioEncoder = null;
    private VideoEncoder mVideoEncoder = null;
    private AudioCaptureDevice mAudioDevice = null;
    private VideoCaptureDevice mVideoDevice = null;
    private Context mContext = null;
    private SessionStateListener mStateListener = null;
    private boolean isSessionPrepared = false;
    private boolean isSessionStarted = false;
    private int mZoomFactor = 0;
    private static final String NAME_OF_LIB_RTMP = "librtmp_jni.so";
    private static boolean isLibRtmpLoaded = false;

    private int mVideoWidth = 1280;
    private int mVideoHeight = 720;
    private int mVideoBitrate = 1024000;
    private int mVideoFps = 25;
    private int mVideoGop = 2;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int mAudioBitrate = 64000;

    // http://developer.android.com/reference/android/media/MediaCodec.html#createByCodecName(java.lang.String)
    private static final String VCODEC = "video/avc";
    private static final String ACODEC = "audio/mp4a-latm";

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
            if (path.indexOf(NAME_OF_LIB_RTMP) >= 0) {
                isLibRtmpLoaded = true;
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
            if (!isLibRtmpLoaded) {
                System.loadLibrary("rtmp_jni");
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
    public LiveSessionHW(final Context cxt) {
        super(cxt);
        loadLibrariesByDefaultPath();
        mContext = cxt;
        isSessionPrepared = false;
        isSessionStarted = false;
        mZoomFactor = 0;
        mAudioDevice = new AudioCaptureDevice(null);
        mVideoDevice = new VideoCaptureDevice(null,mVideoWidth,mVideoHeight);
        Visionin.initialize(cxt, "293cd8f2fd5cdf0e403f535f2563b5b4", "44ce96297a8bcc10eaf095d216d045ec");
        mVideoDevice.openCamera(mVideoWidth,mVideoHeight,mVideoFps,0,true);


    }
    public VideoCaptureDevice getDevice(){
        return mVideoDevice;
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
    public LiveSessionHW(Context cxt, int width, int height, int fps, int bitrate) {
        this(cxt);
        try {
            checkInitParams(width, height, fps, bitrate, Camera.CameraInfo.CAMERA_FACING_BACK);
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
            return;
        }
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoFps = fps;
        mVideoBitrate = bitrate;
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
    public LiveSessionHW(Context cxt, int width, int height, int fps, int bitrate, int cameraId) {
        this(cxt);
        try {
            checkInitParams(width, height, fps, bitrate, cameraId);
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
            return;
        }
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoFps = fps;
        mVideoBitrate = bitrate;
        mCameraId = cameraId;
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
        surfaceHolder.addCallback(mVideoDevice);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    /**
     * 设置推流Session状态订阅接口
     * 
     * @param listener
     */
    public void setStateListener(SessionStateListener listener) {
        mStateListener = listener;
        if (mFlvMuxer != null) mFlvMuxer.setStateListener(listener);
    }
    
    /**
     * 判断是否支持动态切换摄像头
     * 
     * @return 支持返回true，否则返回false
     */
    public boolean canSwitchCamera() {
        return mVideoDevice.canSwitchCamera();
    }

    /**
     * 切换摄像头接口
     * 
     * @param cameraId
     */
    public void switchCamera(int cameraId) {
        mVideoDevice.switchCamera(cameraId);
        mZoomFactor = 0;
    }

    /**
     * 开关闪光灯接口
     * 
     * @param flag
     */
    public void toggleFlash(boolean flag) {
        mVideoDevice.toggleFlash(flag);
    }

    /**
     * 重新自动对焦并指定焦点
     * 
     * @param x
     * @param y
     */
    public void focusToPosition(int x, int y) {
        mVideoDevice.focusToPoint(x, y);
    }
    
    /**
     * 放大相机画面（每次将画面放大一级）
     * @return 操作是否成功
     */
    public boolean zoomInCamera() {
        boolean hasFactorSet = mVideoDevice.setZoomFactor(mZoomFactor + 1);
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
        boolean hasFactorSet = mVideoDevice.setZoomFactor(mZoomFactor - 1);
        if (hasFactorSet) {
            --mZoomFactor;
        }
        return hasFactorSet;
    }
    
    /**
     * 设置相机画面放大级别
     * @param zoomlevel 目标放大级别
     * @return 设置是否成功
     */
    public boolean setCameraZoomLevel(int zoomlevel) {
        boolean hasFactorSet = mVideoDevice.setZoomFactor(zoomlevel);
        if (hasFactorSet) {
            mZoomFactor = zoomlevel;
        }
        return hasFactorSet;
    }
    
    /**
     * 取消相机放大效果
     */
    public void cancelZoomCamera() {
        boolean hasFactorSet = mVideoDevice.setZoomFactor(0);
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
        return mVideoDevice.getMaxZoomFactor();
    }
    
    /**
     * 判断当前摄像头是否支持给定的分辨率
     * @param width 输入分辨率的宽
     * @param height 输入分辨率的高
     * @return
     */
    public boolean isPreviewSizeSupported(int width, int height) {
        return true;
    }
    
    /**
     * 获取实际采集到的视频的高
     * @return
     */
    public int getAdaptedVideoHeight() {
        return mVideoHeight;
    }

    /**
     * 获取实际采集到的视频的宽
     * @return
     */
    public int getAdaptedVideoWidth() {
        return mVideoWidth;
    }
    
    /**
     * 获取当前实时上传带宽，单位：KBps
     * @return
     */
    public double getCurrentUploadBandwidthKbps() {
        if (mFlvMuxer != null) {
            return mFlvMuxer.getUploadBindwidthInKBps();
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
                if (!mAudioDevice.openRecorder()
                        || !mVideoDevice.openCamera(mVideoWidth,
                            mVideoHeight, mVideoFps, mCameraId,
                            !ScreenUtils.screenIsLanscape(mContext))) {
                    if (mStateListener != null) {
                        mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_PREPARE_SESSION_FAILED);
                    }
                    return;
                }

                mVideoWidth = mVideoDevice.getAdaptedVideoWidth();
                mVideoHeight = mVideoDevice.getAdaptedVideoHeight();
                isSessionPrepared = true;
                if (mStateListener != null) {
                    mStateListener.onSessionPrepared(SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED);
                }
            }
        }).start();
        setupEncoders();
        mVideoDevice.setOutputSurface(mVideoEncoder.getInputSurface());
    }
    
    private boolean setupEncoders() {
        // Setup encoders
        try {
            mAudioEncoder = new AudioEncoder(ACODEC, null);
            mVideoEncoder = new VideoEncoder(VCODEC, null);
//            mVideoEncoder.setVideoDataListener(new VideoEncoder.MyVideoDataListener() {
//                @Override
//                public void writeData(ByteBuffer es, MediaCodec.BufferInfo bi) {
//
//                }
//            });

            mAudioEncoder.setupEncoder(mAudioDevice.getSampleRate(), mAudioDevice.getChannelCount(), mAudioBitrate/1000);
            if (ScreenUtils.screenIsLanscape(mContext)) {
                mVideoEncoder.setupEncoder(mVideoWidth, mVideoHeight, mVideoBitrate / 1000, mVideoFps, mVideoGop);
            } else {
                mVideoEncoder.setupEncoder(mVideoHeight, mVideoWidth, mVideoBitrate / 1000, mVideoFps, mVideoGop);
            }
            mAudioEncoder.start();
            mVideoEncoder.start();
            mAudioDevice.setEncoder(mAudioEncoder);
            mVideoDevice.setEncoder(mVideoEncoder);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void destroyEncoders() {
        mAudioDevice.setEncoder(null);
        mVideoDevice.setEncoder(null);
        if (mAudioEncoder != null) {
            Log.i(TAG, "stop audio encoder");
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mVideoEncoder != null) {
            Log.i(TAG, "stop video encoder");
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
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
                boolean ret = setupEncoders() && setupStreamer(url);
                if (!ret) {
                    if (mStateListener != null) {
                        mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_CONNECT_TO_SERVER_FAILED);
                    }
                } else {
                    isSessionStarted = true;
                    // the pts for video and audio encoder.
                    long presentationTimeUs = System.nanoTime();
                    mAudioDevice.setEpoch(presentationTimeUs);
                    mVideoDevice.setEpoch(presentationTimeUs);
                    mVideoDevice.setOutputSurface(mVideoEncoder.getInputSurface());
                    if (mStateListener != null) {
                        mStateListener.onSessionStarted(SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED);
                    }
                }
            }
        }).start();
        return true;
    }

    private boolean setupStreamer(String url) {
        mRtmpSocket = new RtmpSocket();
        if (!mRtmpSocket.isConnected()) {
            int ret = mRtmpSocket.connect(url);
            if (ret < 0) return false;
        }
        mFlvMuxer = new FlvMuxer(url, FlvMuxer.OutputFormat.MUXER_OUTPUT_RTMP);
        mFlvMuxer.setRtmpSocket(mRtmpSocket);
        if (ScreenUtils.screenIsLanscape(mContext)) {
            mFlvMuxer.sendMetaData(mVideoWidth, mVideoHeight, mVideoFps, mVideoBitrate / 1000,
                                   mAudioDevice.getSampleRate(), mAudioBitrate / 1000);
        } else {
            mFlvMuxer.sendMetaData(mVideoHeight, mVideoWidth, mVideoFps, mVideoBitrate / 1000,
                                   mAudioDevice.getSampleRate(), mAudioBitrate / 1000);
        }
        Log.i(TAG, String.format("start muxer to SRS over rtmp, url=%s", url));
        try {
            mFlvMuxer.start();
        } catch (IOException e) {
            Log.e(TAG, "start muxer failed.");
            e.printStackTrace();
            return false;
        }

        mAudioEncoder.setFlvMuxer(mFlvMuxer);
        mVideoEncoder.setFlvMuxer(mFlvMuxer);

        return true;
    }

    private void destroyStreamer() {
        mAudioEncoder.setFlvMuxer(null);
        mVideoEncoder.setFlvMuxer(null);
        if (mFlvMuxer != null) {
            Log.i(TAG, "stop muxer to SRS over HTTP FLV");
            mFlvMuxer.stop();
            mFlvMuxer.release();
            mFlvMuxer = null;
        }
        if (mRtmpSocket != null) {
            mRtmpSocket.release();
            mRtmpSocket = null;
        }
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
                Log.d(TAG, "Stopping rtmp socket...");
                mVideoDevice.setOutputSurface(null);
                destroyStreamer();
                destroyEncoders();
                Log.d(TAG, "The rtmp socket was stopped...");
                isSessionStarted = false;
                if (mStateListener != null) {
                    mStateListener.onSessionStopped(SessionStateListener.RESULT_CODE_OF_OPERATION_SUCCEEDED);
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
        if (mVideoDevice != null) {
            mVideoDevice.closeCamera();
            mVideoDevice.release();
            mVideoDevice = null;
        }
        if (mAudioDevice != null) {
            mAudioDevice.closeRecorder();
            mAudioDevice.release();
            mVideoDevice = null;
        }
    }

    /**
     * 开启或关闭音频推流
     * @param isAudioEnabled 是否开启音频推流
     */
    public void setAudioEnabled(boolean isAudioEnabled) {
        if (mAudioDevice != null) {
            mAudioDevice.setAudioEnabled(isAudioEnabled);
        }
    }

    /**
     * 开启或关闭视频推流
     * @param isVideoEnabled 是否开启视频推流
     */
    public void setVideoEnabled(boolean isVideoEnabled) {
        if (mVideoDevice != null) {
            mVideoDevice.setVideoEnabled(isVideoEnabled);
        }
    }
}
