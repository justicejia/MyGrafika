package com.android.grafika.baidu.recorder.hw.device;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import com.android.grafika.baidu.recorder.hw.encoder.VideoEncoder;
import com.android.grafika.baidu.recorder.hw.graghic.*;
import com.android.grafika.baidu.recorder.hw.muxer.FlvMuxer;

import java.io.IOException;

/**
 * Created by andy on 4/29/16.
 */
public class VideoCaptureDevice implements AutoFocusCallback, SurfaceHolder.Callback,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "VideoCaptureDevice";
    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private FullFrameRect mFullFrameBlit;
    private final float[] mTmpMatrix = new float[16];
    private int mTextureId = -1;

    private Camera mCamera;
    private int mCurrentCameraId = CameraInfo.CAMERA_FACING_BACK;
    private int mCameraPreviewThousandFps ;
    public VideoEncoder mEncoder;
    private WindowSurface mEncoderSurface;
    private Surface mOutputNativeSurface;

    private long mPresentationTimeNs = 0;
    private volatile boolean isSendingVideo = true;

    private int targetVideoWidth = 0, targetVideoHeight = 0;
    private int previewHolderWidth = 0, previewHolderHeight = 0;
    private Camera.Size mCameraPreviewSize = null;
    private int previewFps = 15;
    private boolean hasPreviewContextInited = false;
    private volatile boolean hasCameraPreviewStarted = false;
    private boolean isOrientationPortrait = false;
    private int mEncoderOutputSurfaceIndex = -1;
    private int mPreviewOutputSurfaceIndex = -1;

    private String currentFocusMode = Camera.Parameters.FOCUS_MODE_AUTO;
    public boolean isRecording=false;

    public VideoCaptureDevice(VideoEncoder encoder,int mVideoWidth, int mVideoHeight) {
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mEncoder = encoder;
        isSendingVideo = true;
        hasPreviewContextInited = false;
        targetVideoWidth = mVideoWidth;
        targetVideoHeight = mVideoHeight;
        previewHolderWidth = 0;
        previewHolderHeight = 0;
        previewFps = 0;
    }

    public VideoCaptureDevice(int mVideoWidth, int mVideoHeight) {

    }

    public void setEncoder(VideoEncoder encoder) {
        mEncoder = encoder;
    }

    public void save(){
        mEncoder.saveFile();
        isRecording=true;

    }

    public void stopRecording(){
        mEncoder.stopSave();
        isRecording=false;
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p>
     * Sets mCameraPreviewFps to the expected frame rate (which might actually be variable).
     */
    public boolean openCamera(int desiredWidth, int desiredHeight, int desiredFps, int cameraId, boolean isPortrait) {
        if (mCamera != null) {
            return true;
        }
        previewFps = desiredFps;
        isOrientationPortrait = isPortrait;
        CameraInfo info = new CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == cameraId) {
                Log.d(TAG,"open Camera");
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            return false;
        }

        mCurrentCameraId = cameraId;
        Log.d(TAG, "Current camera Id was set to "+cameraId);
        Camera.Parameters parms = mCamera.getParameters();

        Camera.Size realSize = CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
        targetVideoWidth = desiredWidth;
        targetVideoHeight = desiredHeight;
        // VisionIn.inc is gonna do it for us
        if (isPortrait) {
            parms.set("orientation", "portrait"); //
            mCamera.setDisplayOrientation(90);
        } else {
            parms.set("orientation", "landscape"); //
            mCamera.setDisplayOrientation(0);
        }
        mCameraPreviewSize = realSize;
        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);

        mCamera.setParameters(parms);

        if (mCameraTexture != null) {
            startCameraPreview();
        }

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.i(TAG, "Camera config: " + previewFacts);
//        if (mEncoderOutputSurfaceIndex == -1) {
//            if (isOrientationPortrait) {
//                mEncoderOutputSurfaceIndex = VisioninHelper.createOutput(targetVideoHeight, targetVideoWidth);
//            } else {
//                mEncoderOutputSurfaceIndex = VisioninHelper.createOutput(targetVideoWidth, targetVideoHeight);
//            }
//        }
        return true;
    }
    
    public int getAdaptedVideoWidth() {
        return targetVideoWidth; // mCameraPreviewSize.width;
    }
    
    public int getAdaptedVideoHeight() {
        return targetVideoHeight; // mCameraPreviewSize.height;
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    public void closeCamera() {
        if (mCamera != null) {
            stopCameraPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    public void release() {
        destroyPreviewContext();
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
        mEncoderSurface = null;
    }

    public void toggleFlash(boolean flag) {
        if (mCamera != null && mCurrentCameraId != CameraInfo.CAMERA_FACING_FRONT) {
            String flashMode = flag ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF;
            Camera.Parameters parms = mCamera.getParameters();
            CameraUtils.chooseFlashMode(parms, flashMode);
            mCamera.setParameters(parms);
        }
    }

    public boolean canSwitchCamera() {
        // Check the number of cameras
        return Camera.getNumberOfCameras() > 1;
    }

    public void switchCamera(int cameraId) {
        if (mCurrentCameraId == cameraId) {
            return;
        }
        closeCamera();
        if (isOrientationPortrait) {
            openCamera(targetVideoHeight, targetVideoWidth, previewFps, cameraId, isOrientationPortrait);
        } else {
            openCamera(targetVideoWidth, targetVideoHeight, previewFps, cameraId, isOrientationPortrait);
        }
    }

    /**
     * 设置对焦焦点
     *
     * @param x
     * @param y
     */
    public void focusToPoint(int x, int y) {
        if (mCamera != null && hasCameraPreviewStarted) {
            Camera.Parameters params = mCamera.getParameters();
            CameraUtils.chooseFocusPoint(params, currentFocusMode, x, y, previewHolderWidth, previewHolderHeight);
            mCamera.cancelAutoFocus();
            mCamera.setParameters(params);
            try {
                mCamera.autoFocus(this);
            } catch (Throwable t) {
                Log.e(TAG, "Touch to auto focus failed!");
            }
        }
    }

    /**
     * 获取相机最大的放大因子
     */
    public int getMaxZoomFactor() {
        if (mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
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
            Camera.Parameters params = mCamera.getParameters();
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

    public void setOutputSurface(Surface sf) {
        if (sf != null) {
//            mEncoderSurface = new WindowSurface(mEglCore, sf, true);
        } else {
//            Log.e(TAG, "The output surface is null.");
            mEncoderSurface = null;
        }
        mOutputNativeSurface = sf;
    }

    public void setEpoch(long pts) {
        mPresentationTimeNs = pts;
    }

    private void initPreviewContext(SurfaceHolder holder) {
        // Set up everything that requires an EGL context.
        //
        // We had to wait until we had a surface because you can't make an EGL context current
        // without one, and creating a temporary 1x1 pbuffer is a waste of time.
        //
        // The display surface that we use for the SurfaceView, and the encoder surface we
        // use for video, use the same EGL context.
        if (hasPreviewContextInited) {
            return;
        }
        hasPreviewContextInited = true;
        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();
        mFullFrameBlit = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D));
        // For surface texture only
//        mFullFrameBlit = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
//        mTextureId = mFullFrameBlit.createTextureObject();
        VisioninHelper.start();
        mTextureId = VisioninHelper.createTexture();
        mCameraTexture = new SurfaceTexture(mTextureId);
        mCameraTexture.setOnFrameAvailableListener(this);
    }

    private void destroyPreviewContext() {
        if (!hasPreviewContextInited) {
            return;
        }

        Log.d(TAG, "Destroying gl context...");
        hasPreviewContextInited = false;
        if (mCameraTexture != null) {
            mCameraTexture.setOnFrameAvailableListener(null);
            mCameraTexture.release();
            mCameraTexture = null;
        }
        if (mDisplaySurface != null) {
            mDisplaySurface.release();
            mDisplaySurface = null;
        }
        if (mFullFrameBlit != null) {
            mFullFrameBlit.release(false);
            mFullFrameBlit = null;
        }
        VisioninHelper.stop();
        mEncoderOutputSurfaceIndex = -1;
        mPreviewOutputSurfaceIndex = -1;
    }

    private void startCameraPreview() {
        if (hasCameraPreviewStarted) {
            return;
        }

        try {
            if (mCamera != null) {
                Log.d(TAG, "starting camera preview");
                mCamera.setPreviewTexture(mCameraTexture);
                mCamera.startPreview();
                hasCameraPreviewStarted = true;
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private void stopCameraPreview() {
        if (!hasCameraPreviewStarted) {
            return;
        }

        if (mCamera != null) {
            Log.d(TAG, "stop camera preview");
            hasCameraPreviewStarted = false;
            mCamera.stopPreview();
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "In surfaceCreated() holder=" + holder);
        initPreviewContext(holder);
        if (targetVideoHeight == 0 || targetVideoWidth == 0) {
            return;
        }
        Log.d(TAG, "targetVideoWidth=" + targetVideoWidth + ",targetVideoHeight=" + targetVideoHeight);
        //openCamera(targetVideoWidth, targetVideoHeight, previewFps, mCurrentCameraId, isOrientationPortrait);
        startCameraPreview();
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "In surfaceChanged() holder=" + holder);
        previewHolderWidth = width;
        previewHolderHeight = height;
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);
        if (mPreviewOutputSurfaceIndex == -1) {
            if (isOrientationPortrait && previewHolderWidth < previewHolderHeight ||
               !isOrientationPortrait && previewHolderWidth > previewHolderHeight)
                mPreviewOutputSurfaceIndex = VisioninHelper.createOutput(previewHolderWidth, previewHolderHeight);
            else
                mPreviewOutputSurfaceIndex = VisioninHelper.createOutput(previewHolderHeight, previewHolderWidth);
        }
        if (mEncoderOutputSurfaceIndex == -1) {
            if (isOrientationPortrait) {
                Log.e("output", "targetVideoHeight:"+targetVideoWidth+"/width:"+targetVideoWidth);
                mEncoderOutputSurfaceIndex = VisioninHelper.createOutput(targetVideoHeight, targetVideoWidth);
            } else {
                Log.e("output", "targetVideoWidth:"+targetVideoWidth+"/height:"+targetVideoHeight);
                mEncoderOutputSurfaceIndex = VisioninHelper.createOutput(targetVideoWidth, targetVideoHeight);
            }
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "In surfaceDestroyed() holder=" + holder);
        stopCameraPreview();
        closeCamera();
        destroyPreviewContext();
    }

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        //Log.d(TAG, "frame available");
        this.drawFrame(previewHolderWidth, previewHolderHeight);
    }

    @Override
    public void onAutoFocus(boolean arg0, Camera arg1) {
        if (arg0) {
            Log.d(TAG, "Auto-Focus succeeded!");
        } else {
            Log.d(TAG, "Auto-Focus failed!");
        }
    }

    public void setVideoEnabled(boolean flag) {
        isSendingVideo = flag;
    }
    
    private int  processBeautyEffect(boolean isPreview) {
        VisioninHelper.setInputSize(mCameraPreviewSize.width,
                                    mCameraPreviewSize.height,
                                    mCurrentCameraId,
                                    isOrientationPortrait ? 0 : 1,
                                    isPreview);
        VisioninHelper.setBeautyEffect(1.0f, 1.0f, 0.5f);
        if (isPreview) {
            VisioninHelper.setOutput(mPreviewOutputSurfaceIndex);
        } else {
            VisioninHelper.setOutput(mEncoderOutputSurfaceIndex);
        }
        return VisioninHelper.process(mTextureId);
    }

    /**
     * Draws a frame onto the SurfaceView and the encoder surface.
     * <p>
     * This will be called whenever we get a new preview frame from the camera.  This runs
     * on the UI thread, which ordinarily isn't a great idea -- you really want heavy work
     * to be on a different thread -- but we're really just throwing a few things at the GPU.
     * The upside is that we don't have to worry about managing state changes between threads.
     * <p>
     * If there was a pending frame available notification when we shut down, we might get
     * here after onPause().
     */



    private void drawFrame(int width, int height) {


        if (!hasPreviewContextInited) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }


        // Latch the next frame from the camera.
        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mTmpMatrix);
        mDisplaySurface.makeCurrent();

        int testureId = mTextureId;
        testureId = processBeautyEffect(true);
//        GLES20.glViewport(0, 0, previewHolderWidth, previewHolderHeight);
//        mFullFrameBlit.drawFrame(testureId, mTmpMatrix);
        mDisplaySurface.swapBuffers();

//        if (mOutputNativeSurface != null && mEncoderSurface == null) {
//            mEncoderSurface = new WindowSurface(mEglCore, mOutputNativeSurface, true);
//        }
                if (mEncoderSurface == null) {
            mEncoderSurface = new WindowSurface(mEglCore, mOutputNativeSurface, true);
        }

//        if (mEncoderSurface != null && isSendingVideo) {
        if (mEncoderSurface == null) {
            Log.d(TAG, "mEncoderSurface is null!!!");
        }
        Log.d(TAG,"Draw Frame");

            long lastPtsInMs = mEncoder.getLastSentPacketPtsInMs();
            long ptsInNs = System.nanoTime() - mPresentationTimeNs;
            if (lastPtsInMs > 0 && ptsInNs / 1000000 - lastPtsInMs
                    > FlvMuxer.THRESHOLD_OF_LATENCY_IN_MS_TO_DROP_PACKET) {
                mEncoder.clearSendingBuffer();
            }
            
            mEncoderSurface.makeCurrent();
            processBeautyEffect(false);
//            if (isOrientationPortrait) {
//                GLES20.glViewport(0, 0, targetVideoHeight, targetVideoWidth);
//            } else {
//                GLES20.glViewport(0, 0, targetVideoWidth, targetVideoHeight);          
//            }
//            mFullFrameBlit.drawFrame(testureId, mTmpMatrix);

            mEncoder.frameAvailableSoon();
            mEncoderSurface.setPresentationTime(ptsInNs);
            mEncoderSurface.swapBuffers();

    }
}
