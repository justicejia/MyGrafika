package com.android.grafika.baidu.recorder.sw.controller;

import java.util.List;

import com.android.grafika.baidu.recorder.api.SessionStateListener;
import com.android.grafika.baidu.recorder.sw.bean.VideoParams;
import com.android.grafika.baidu.recorder.util.ScreenUtils;
import com.android.grafika.baidu.recorder.util.VideoUtil;
import com.android.grafika.baidu.recorder.util.YUVUtils;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * 视频录制相关类
 * 
 * @author Andy Young @ Baidu.com
 *
 */
public class VideoRecordDevice extends RecordDevices implements AutoFocusCallback, SurfaceHolder.Callback {

    private static final String TAG = "VideoRecordDevice";
    private Camera mCamera = null;
    private SurfaceHolder mSurfaceHolder = null;
    private VideoParams cameraParams;
    private Context context;
    private IRecorder mRecorder;
    private List<Size> previewSizesOfFrontCamera = null;
    private List<Size> previewSizesOfBackCamera = null;
    private static final int NUM_CAMERA_PREVIEW_BUFFERS = 1;
    private byte[][] mPreviewBuffer = null;
    private VideoUtil mVideoUtil = null;
    private boolean isSendingVideo = true;

    public VideoRecordDevice(Context context, VideoParams cameraParams) {
        this.context = context;
        this.cameraParams = cameraParams;
        testPreviewSizesOfCameras();
    }

    public void setBRecorder(IRecorder recorder) {
        mRecorder = recorder;
    }
    
    public boolean isPreviewSizeSupported(int width, int height) {
        List<Size> sizes = getPreviewSizesOfCamera(cameraParams.getCameraId());
        return sizes != null && isInSizes(sizes, width, height);
    }

    /**
     * 绑定一个sufaceView作为预览view
     * 
     * @param mSurfaceHolder
     */
    public void bindingSurface(SurfaceHolder mSurfaceHolder) {
        mSurfaceHolder.addCallback(this);
    }
    
    private List<Size> getPreviewSizesOfCamera(int camId) {
        if (camId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            return previewSizesOfBackCamera;
        } else {
            return previewSizesOfFrontCamera;
        }
    }
    
    private void testPreviewSizesOfCamera(int camId) {
        try {
            Camera camera = Camera.open(camId);
            Parameters params = camera.getParameters();
            if (camId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                previewSizesOfBackCamera = params.getSupportedPreviewSizes();
            } else {
                previewSizesOfFrontCamera = params.getSupportedPreviewSizes();
            }
            camera.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void testPreviewSizesOfCameras() {
        int cntCamera = Camera.getNumberOfCameras();
        if (cntCamera > 0) {
            testPreviewSizesOfCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
        if (cntCamera > 1) {
            testPreviewSizesOfCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
        }
    }

    private boolean startCameraPreview() {
        Log.d(TAG, "Starting preview.");
        if (null != mCamera && mSurfaceHolder != null) {
            try {
                mCamera.setPreviewCallback(cb);
                for (int i = 0; i < NUM_CAMERA_PREVIEW_BUFFERS; i++) {
                    mCamera.addCallbackBuffer(mPreviewBuffer[i]);
                }
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview(); // 打开预览画面
            } catch (Throwable e) {
                e.printStackTrace();
                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    private void stopCameraPreview() {
        if (null != mCamera) {
            mCamera.setPreviewCallback(null); // ！！这个必须在前，不然退出出错
            mCamera.stopPreview();
        }
    }

    /**
     * 设置摄像头默认参数
     * 
     * @param parameters
     */
    private void buildDefaultParams(Parameters parameters) {
        setColorFormat(parameters);
        setFlashMode(parameters);
        setWhiteBalance(parameters);
        setSceneMode(parameters);
        setFocusMode(parameters); // 对焦模式
        setPreviewSize(parameters); // 分辨率
        setFps(parameters); // fps
        setDisplayOrientation(parameters); // 预览方向
    }
    
    /**
     * 设置相机预览颜色格式
     * @param parameters
     */
    private void setColorFormat(Parameters parameters) {
        parameters.setPreviewFormat(ImageFormat.NV21);
    }

    /**
     * 情景模式
     * 
     * @param parameters
     */
    private void setSceneMode(Parameters parameters) {
        List<String> supportedSceneModes = parameters.getSupportedSceneModes();
        
        if (supportedSceneModes != null) {
            if (supportedSceneModes.contains(cameraParams.getSceneMode())) {
                parameters.setSceneMode(cameraParams.getSceneMode());
            }
        }
    }

    /**
     * 设置白平衡
     */
    private void setWhiteBalance(Parameters parameters) {
        List<String> supportedWhiteBalance = parameters.getSupportedWhiteBalance();

        if (supportedWhiteBalance != null) {
            if (supportedWhiteBalance.contains(cameraParams.getWhiteBalance())) {
                parameters.setWhiteBalance(cameraParams.getWhiteBalance());
            }
        }
    }

    /**
     * 设置闪光灯
     * 
     * @param parameters
     */
    private void setFlashMode(Parameters parameters) {
        List<String> supportedFlashModes = parameters.getSupportedFlashModes();

        if (supportedFlashModes != null) {
            if (supportedFlashModes.contains(cameraParams.getFlashMode())) {
                parameters.setFlashMode(cameraParams.getFlashMode());
            }
        }
    }

    /**
     * 设置相机预览画面方向
     * 
     * @param parameters
     */
    private void setDisplayOrientation(Parameters parameters) {
        // 横竖屏镜头自动调整
        if (!ScreenUtils.screenIsLanscape(context)) {
            parameters.set("orientation", "portrait"); //
            cameraParams.setPreviewOrientation(90);
        } else { // 如果是横屏
            parameters.set("orientation", "landscape"); //
            cameraParams.setPreviewOrientation(0);
        }

        mCamera.setDisplayOrientation(cameraParams.getPreviewOrientation()); // 在2.2以上可以使用
    }

    /**
     * 设置相机对焦模式
     * 
     * @param parameters
     */
    private void setFocusMode(Parameters parameters) {
        List<String> supportedFocusModes = parameters.getSupportedFocusModes();

        if (supportedFocusModes != null) {
            if (supportedFocusModes.contains(cameraParams.getFocusMode())) {
                parameters.setFocusMode(cameraParams.getFocusMode());
            }
        }
    }

    /**
     * 设置相机预览分辨率
     * 
     * @param parameters
     */
    private void setPreviewSize(Parameters parameters) {
        List<Size> previewSizes = parameters.getSupportedPreviewSizes();
        List<Integer> previewFormats = parameters.getSupportedPreviewFormats();

        if (previewSizes != null) {
            Size psize = (Size) previewSizes.get(0);
            int min_delt = Math.abs(psize.height * psize.width - cameraParams.getHeight() * cameraParams.getWidth());
            previewSizes.remove(0);
            for (Size size : previewSizes) {
                int delt = Math.abs(size.height * size.width - cameraParams.getHeight() * cameraParams.getWidth());
                if (delt < min_delt) {
                    min_delt = delt;
                    psize = size;
                }
            }
            cameraParams.setWidth(psize.width);
            cameraParams.setHeight(psize.height);
            parameters.setPreviewSize(psize.width, psize.height);
        }

        if (previewFormats != null) {
            if (previewFormats.contains(cameraParams.getPreviewFormat())) {
                parameters.setPreviewFormat(cameraParams.getPreviewFormat());
            }
        }
    }

    /**
     * 设置fps
     * 
     * @param parameters
     */
    private void setFps(Parameters parameters) {
        List<int[]> range = parameters.getSupportedPreviewFpsRange();

        if (range != null) {
            int minRange = range.get(0)[0];
            int maxRange = range.get(range.size() - 1)[1];
    
            Log.i(TAG, "Count of fps:" + range.size() + ", minRange:" + minRange + ", maxRange:" + maxRange);
    
            if (cameraParams.getDefaultFps() <= maxRange && cameraParams.getDefaultFps() >= minRange) {
                cameraParams.setFps(new int[] { minRange, cameraParams.getDefaultFps() });
            } else {
                cameraParams.setFps(new int[] { minRange, maxRange });
            }
    
            // parameters.setPreviewFpsRange(cameraParams.getFps()[0], cameraParams.getFps()[1]);
        }
    }

    /**
     * 获取相机每一帧画面
     */
    byte[] tmpBuffer = null;
    PreviewCallback cb = new PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            tmpBuffer = data;
            if (mVideoUtil != null) {
                tmpBuffer = mVideoUtil.dealVideoFrame(data);
            }
            feedingFrame(tmpBuffer);
            camera.addCallbackBuffer(data);
        }
    };

    /**
     * 
     * @param data
     */
    private void feedingFrame(byte[] data) {
        if (mRecorder != null && isSendingVideo) {
            long timestamp = System.nanoTime() / 1000;
            mRecorder.feedingVideoFrame(data, data.length, timestamp);
        }
    }

    // //////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////

    /**
     * 打开摄像头，获取预览数据
     */
    @Override
    public boolean open() {
        if (mPreviewBuffer == null) {
            mPreviewBuffer = new byte[NUM_CAMERA_PREVIEW_BUFFERS][];
            for (int i = 0; i < NUM_CAMERA_PREVIEW_BUFFERS; i++) {
                mPreviewBuffer[i] = new byte[YUVUtils.getYuvBuffer(cameraParams.getWidth(), cameraParams.getHeight())];
            }
        }
        if (mCamera == null) {
            int cameraId = cameraParams.getCameraId();
            Log.i(TAG, String.format("going to open Camera [%d]", cameraId));
            int numCameras = Camera.getNumberOfCameras();
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == cameraId) {
                    mCamera = Camera.open(i);
                    break;
                }
            }
            if (mCamera == null) {
                return false;
            }

            // Build camera parameters
            Parameters parameters = mCamera.getParameters();
            buildDefaultParams(parameters); // 设置摄像头参数
            // 设定配置参数并开启预览
            try {
                mCamera.setParameters(parameters); // 将Camera.Parameters设定予Camera
            } catch (Throwable e) {
                e.printStackTrace();
                if (mStateListener != null) {
                    mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_OPEN_CAMERA_FAILED);
                }
                return false;
            }
            if (mSurfaceHolder != null) {
                if (!startCameraPreview() && mStateListener != null) {
                    mStateListener.onSessionError(SessionStateListener.ERROR_CODE_OF_OPEN_CAMERA_FAILED);
                }
            }
        }
        return true;
    }

    /**
     * 停止并释放摄像头
     */
    @Override
    public void close() {
        if (null != mCamera) {
            stopCameraPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "Closing camera, done!");
        }
    }

    /**
     * 重置摄像头参数
     */
    @Override
    public void reset() {

    }

    /**
     * 闪光灯开关
     * 
     * @param flag
     */
    public void setFlashFlag(boolean flag) {
        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            List<String> supportedFlashModes = params.getSupportedFlashModes();
            
            String flashMode = flag ? Parameters.FLASH_MODE_TORCH
                                   : Parameters.FLASH_MODE_OFF;
            if (supportedFlashModes != null
                && supportedFlashModes.contains(flashMode)) {
                try {
                    cameraParams.setFlashMode(flashMode);
                    params.setFlashMode(cameraParams.getFlashMode());
                    mCamera.setParameters(params);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }
    
    private Rect calculateTapArea(float x, float y, float coefficient) {
        float focusAreaSize = 200;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();
        int centerX = (int) ((x / cameraParams.getWidth()) * 2000 - 1000);
        int centerY = (int) ((y / cameraParams.getHeight()) * 2000 - 1000);
        int left = clamp(centerX - (areaSize / 2), -1000, 1000);
        int top = clamp(centerY - (areaSize / 2), -1000, 1000);
        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        return new Rect(Math.round(rectF.left), Math.round(rectF.top),
                Math.round(rectF.right), Math.round(rectF.bottom));
    }

    /**
     * 设置对焦焦点
     * 
     * @param x
     * @param y
     */
    public void focusToPoint(int x, int y) {
        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            List<String> supportedModes = params.getSupportedFlashModes();
            if (supportedModes!=null && supportedModes.contains(cameraParams.getFocusMode())) {
                params.setFocusMode(cameraParams.getFocusMode());
                
//              Rect focusRect = calculateTapArea(x, y, 1f);
//              Rect meteringRect = calculateTapArea(x, y, 1.5f);
//              if (params.getMaxNumFocusAreas() > 0) {
//                  List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
//                  focusAreas.add(new Camera.Area(focusRect, 600));
//                  params.setFocusAreas(focusAreas);
//              }
//              if (params.getMaxNumMeteringAreas() > 0) {
//                  List<Camera.Area> meteringAreas = new ArrayList<Camera.Area>();
//                  meteringAreas.add(new Camera.Area(meteringRect, 600));
//                  params.setMeteringAreas(meteringAreas);
//              }
                
                try { // In case that some device have bugs
                    mCamera.cancelAutoFocus();
                    mCamera.setParameters(params);
                    mCamera.autoFocus(null);
                } catch (Exception e) {
                      e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取相机最大的放大因子
     */
    public int getMaxZoomFactor() {
        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            if (!params.isZoomSupported()) {
                return 0;
            }
            return params.getMaxZoom();
        }
        return -1;
    }

    /**
     * 设置相机放大因子
     * @param factor
     */
    public boolean setZoomFactor(int factor) {
        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            if (!params.isZoomSupported()) {
                return false;
            }
            int maxZoom = params.getMaxZoom();
            if (factor > maxZoom || factor < 0) {
                return false;
            }
            try {
                params.setZoom(factor);
                mCamera.setParameters(params);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
    
    private boolean isInSizes(List<Size> sizes, int width, int height) {
        for (Size s : sizes) {
            if (s.width == width && s.height == height) {
                return true;
            }
        }
        return false;
    }
    
    public boolean canSwitchCamera() {
        // Check the number of cameras
        if (Camera.getNumberOfCameras() <= 1) {
            return false;
        }
        
        int cameraId = cameraParams.getCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT ?
                       Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
        List<Size> sizes = getPreviewSizesOfCamera(cameraId);
        return sizes != null && isInSizes(sizes, cameraParams.getWidth(), cameraParams.getHeight());
    }

    /**
     * 转换摄像头
     * 
     * @param cameraId
     *            参考Camera.CameraInfo.CAMERA_FACING_BACK 和
     *            Camera.CameraInfo.CAMERA_FACING_FRONT
     */
    public void switchCamera(int cameraId) {
        close();
        cameraParams.setFlashMode(Parameters.FLASH_MODE_OFF);
        cameraParams.setCameraId(cameraId);
        open();
    }

    // ///////////////////////////////////////////////////////////////////
    // ///////////////////////////////////////////////////////////////////

    /**
     * 释放资源
     */
    @Override
    public void release() {

    }

    public void setVideoUtil(VideoUtil videoKit) {
        mVideoUtil = videoKit;
    }

    @Override
    public void onAutoFocus(boolean arg0, Camera arg1) {
        if (arg0) {
            Log.d(TAG, "Auto-Focus succeeded!");
        } else {
            Log.d(TAG, "Auto-Focus failed!");
        }
    }

    public void setVideoEnabled(boolean isEnableVideo) {
        isSendingVideo = isEnableVideo;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        if (mVideoUtil != null) {
            fitPreviewToParentByResolution(holder, mVideoUtil.getTargetVideoWidth(), mVideoUtil.getTargetVideoHeight());
        }
    }
    
    private void fitPreviewToParentByResolution(SurfaceHolder holder, int width, int height) {
        // Adjust the size of SurfaceView dynamically
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        int adjustedVideoHeight = screenHeight;
        int adjustedVideoWidth = screenWidth;
        if (width * screenHeight > height * screenWidth) { // means width/height > screenWidth/screenHeight
            // Fit width
            adjustedVideoHeight = height * screenWidth / width;
            adjustedVideoWidth = screenWidth;
        } else {
            // Fit height
            adjustedVideoHeight = screenHeight;
            adjustedVideoWidth = width * screenHeight / height;
        }
        holder.setFixedSize(adjustedVideoWidth, adjustedVideoHeight); 
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        startCameraPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCameraPreview();
    }

}
