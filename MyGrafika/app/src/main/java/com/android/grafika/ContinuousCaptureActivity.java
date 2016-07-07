/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.grafika;

import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.app.Activity;

import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
import static android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
import static android.hardware.Camera.open;

import com.android.grafika.baidu.recorder.hw.encoder.VideoEncoder;
import com.android.grafika.baidu.recorder.hw.graghic.*;
import com.android.grafika.baidu.recorder.hw.muxer.FlvMuxer;
import com.visionin.gpu.GPU;

/**
 * Demonstrates capturing video into a ring buffer.  When the "capture" button is clicked,
 * the buffered video is saved.
 * <p>
 * Capturing and storing raw frames would be slow and require lots of memory.  Instead, we
 * feed the frames into the video encoder and buffer the output.
 * <p>
 * Whenever we receive a new frame from the camera, our SurfaceTexture callback gets
 * notified.  That can happen on an arbitrary thread, so we use it to send a message
 * through our Handler.  That causes us to render the new frame to the display and to
 * our video encoder.
 */
public class ContinuousCaptureActivity extends Activity implements SurfaceHolder.Callback,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "Grafika";
    private static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 720;
    private static final int DESIRED_PREVIEW_FPS = 15;
    private final float[] mTmpMatrix = new float[16];
    private int cameraCurrentId;
    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private FullFrameRect mFullFrameBlit;
    private int mTextureId;
    private int mFrameNum;
    private LiveSession mLiveSession = null;
    private Camera mCamera;
    private int mCameraPreviewThousandFps;
    private File mOutputFile;
    private CircularEncoder mCircEncoder;
    private WindowSurface mEncoderSurface;
    private boolean mFileSaveInProgress;
    private SurfaceHolder surfaceHolde;
    private MainHandler mHandler;
    private int mCurrentCamera = -1;
    private float mSecondsOfVideo;
    private Button mCameraStateButton;//闪光灯按钮
    private Button mSwitchCamera;//摄像头转换按钮
    private boolean isFrontCamera=false;
    private  int numCameras;
    private int cameraId;
    private Camera displayOrientation;
    private Camera.Parameters pictureSize;
    private int cameraPosition = 1;
    private MediaRecorder mMediaRecorder;
    private boolean isRecording=true;
    private Camera.Size mCameraPreviewSize;
    private int targetVideoWidth = 0, targetVideoHeight = 0;
    private int previewHolderWidth = 0, previewHolderHeight = 0;
    private int mEncoderOutputSurfaceIndex = -1;
    private int mPreviewOutputSurfaceIndex = -1;
    private Surface mOutputNativeSurface;



    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_continuous_capture);
        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        surfaceHolde = sv.getHolder();
        surfaceHolde.addCallback(this);
        mCameraStateButton=(Button)findViewById(R.id.flash_ctl) ;
        mSwitchCamera=(Button)findViewById(R.id.camera_ctl);
        mHandler = new MainHandler(this);
        mHandler.sendEmptyMessageDelayed(MainHandler.MSG_BLINK_TEXT, 1500);


        mSwitchCamera.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {

        // 切换前后摄像头
            if(cameraPosition==1){
                    releaseCamera();
                    if (mCircEncoder != null) {
                        mCircEncoder.shutdown();
                        mCircEncoder = null;
                    }
                    if (mCameraTexture != null) {
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
                    if (mEglCore != null) {
                        mEglCore.release();
                        mEglCore = null;
                    }
                onPause();
                onDestroy();
                onCreate(savedInstanceState);
                openbackCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
                setStartPreview(mCamera,surfaceHolde);


                cameraPosition = 0;
                }
             else {
                    releaseCamera();
                    if (mCircEncoder != null) {
                        mCircEncoder.shutdown();
                        mCircEncoder = null;
                    }
                    if (mCameraTexture != null) {
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
                    if (mEglCore != null) {
                        mEglCore.release();
                        mEglCore = null;
                    }

                    onPause();
                    onDestroy();
                    onCreate(savedInstanceState);
                    openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
                    setStartPreview(mCamera,surfaceHolde);
                    cameraPosition = 1;
        }


    }
});
        CreateFile();


}


    void CreateFile(){
        File dir = new File(Environment.getExternalStorageDirectory(), "grafika");
        if(!dir.exists()) {
            dir.mkdirs();
        }
        mOutputFile = new File(dir, "VisioninDemo.mp4");
        mSecondsOfVideo = 0.0f;
    }




    /**
     * 设置camera显示取景画面,并预览
     * @param camera
     */
    private void setStartPreview(Camera camera,SurfaceHolder holder){
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG,"onResume");
        // Ideally, the frames from the camera are at the same resolution as the input to
        // the video encoder so we don't have to scale.
        openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG,"pause");

        releaseCamera();
        if(mMediaRecorder!=null)
        {
            mMediaRecorder.release();
            mMediaRecorder=null;
        }
        if (mCircEncoder != null) {
            mCircEncoder.shutdown();
            mCircEncoder = null;
        }
        if (mCameraTexture != null) {
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
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
        Log.d(TAG, "onPause() done");
    }

    @Override
    protected void onDestroy() {
        if(mCamera!=null){
            mCamera.lock();
            mCamera.release();
            mCamera=null;
        }
        if(mMediaRecorder!=null){
            mMediaRecorder.release();
            mMediaRecorder=null;
        }

        super.onDestroy();
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p>
     * Sets mCameraPreviewFps to the expected frame rate (which might actually be variable).
     */
    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == CAMERA_FACING_FRONT) {
                mCamera = open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();
        mCameraPreviewSize=CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
        mCamera.setDisplayOrientation(90);
        targetVideoWidth = desiredWidth;
        targetVideoHeight = desiredHeight;

        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);
        mCamera.setParameters(parms);

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.i(TAG, "Camera config: " + previewFacts);


    }

    private void openbackCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == CAMERA_FACING_BACK) {
                mCamera = open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();
        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
        mCamera.setDisplayOrientation(90);
        targetVideoWidth = desiredWidth;
        targetVideoHeight = desiredHeight;

        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);
        mCamera.setParameters(parms);

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.i(TAG, "Camera config: " + previewFacts);


    }


    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    /**
     * Updates the current state of the controls.
     */
    private void updateControls() {
        String str = getString(R.string.secondsOfVideo, mSecondsOfVideo);
//        TextView tv = (TextView) findViewById(R.id.capturedVideoDesc_text);
//        tv.setText(str);

        boolean wantEnabled = (mCircEncoder != null) && !mFileSaveInProgress;
        Button button = (Button) findViewById(R.id.capture_button);
        if (button.isEnabled() != wantEnabled) {
            Log.d(TAG, "setting enabled = " + wantEnabled);
            button.setEnabled(wantEnabled);
        }
    }

    /**
     * Handles onClick for "capture" button.
     */
    public void clickCapture(@SuppressWarnings("unused") View unused) {
        Log.d(TAG, "Save");
        if (mFileSaveInProgress) {
            Log.w(TAG, "HEY: file save is already in progress");
            return;
        }
        // The button is disabled in onCreate(), and not enabled until the encoder and output
        // surface is ready, so it shouldn't be possible to get here with a null mCircEncoder.
        mFileSaveInProgress = true;
        updateControls();
        mCircEncoder.saveVideo(mOutputFile);

    }

    /**
     * The file save has completed.  We can resume recording.
     */
    private void fileSaveComplete(int status) {
        Log.d(TAG, "fileSaveComplete " + status);
        if (!mFileSaveInProgress) {
            throw new RuntimeException("WEIRD: got fileSaveCmplete when not in progress");
        }
        mFileSaveInProgress = false;
        updateControls();
        String str = getString(R.string.nowRecording);
        if (status == 0) {
            str = getString(R.string.recordingSucceeded);
        } else {
            str = getString(R.string.recordingFailed, status);
        }
        Toast toast = Toast.makeText(this, str, Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * Updates the buffer status UI.
     */
    private void updateBufferStatus(long durationUsec) {
        mSecondsOfVideo = durationUsec / 1000000.0f;
        updateControls();
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



    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated holder=" + holder);

        // Set up everything that requires an EGL context.
        //
        // We had to wait until we had a surface because you can't make an EGL context current
        // without one, and creating a temporary 1x1 pbuffer is a waste of time.
        //
        // The display surface that we use for the SurfaceView, and the encoder surface we
//         use for video, use the same EGL context.
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();
        mFullFrameBlit = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

        VisioninHelper.start();
        mTextureId=VisioninHelper.createTexture();

//        mTextureId = mFullFrameBlit.createTextureObject();
        mCameraTexture = new SurfaceTexture(mTextureId);
        mCameraTexture.setOnFrameAvailableListener(this);


        Log.d(TAG, "starting camera preview");
        try {
            mCamera.setPreviewTexture(mCameraTexture);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCamera.startPreview();

        // TODO: adjust bit rate based on frame rate?
        // TODO: adjust video width/height based on what we're getting from the camera preview?
        //       (can we guarantee that camera preview size is compatible with AVC video encoder?)

        try {
            mCircEncoder = new CircularEncoder(VIDEO_WIDTH, VIDEO_HEIGHT, 6000000,
                    mCameraPreviewThousandFps / 1000, 7, mHandler);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mEncoderSurface = new WindowSurface(mEglCore, mCircEncoder.getInputSurface(), true);
        setOutputSurface(mCircEncoder.getInputSurface());

        updateControls();
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);
        previewHolderWidth = width;
        previewHolderHeight = height;
        if (mPreviewOutputSurfaceIndex == -1) {
                mPreviewOutputSurfaceIndex = VisioninHelper.createOutput(previewHolderWidth, previewHolderHeight);
        }
        if (mEncoderOutputSurfaceIndex == -1) {
                mEncoderOutputSurfaceIndex = VisioninHelper.createOutput(targetVideoHeight, targetVideoWidth);
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
    }

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        //Log.d(TAG, "frame available");
        mHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
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

    private int  processBeautyEffect(boolean isPreview) {
        Log.w(TAG,"process Effect");
        VisioninHelper.setInputSize(mCameraPreviewSize.width,
                mCameraPreviewSize.height,
                cameraPosition,
                1,
                isPreview);
        VisioninHelper.setBeautyEffect(8.0f, 1.0f, 1.0f);
        if (isPreview) {
            VisioninHelper.setOutput(mPreviewOutputSurfaceIndex);
        } else {
            VisioninHelper.setOutput(mEncoderOutputSurfaceIndex);
        }
        return VisioninHelper.process(mTextureId);
    }

    private void drawFrame() {
        //Log.d(TAG, "drawFrame");
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }

        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();
        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mTmpMatrix);


        processBeautyEffect(true);


        // Fill the SurfaceView with it.

//        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
//        int viewWidth = sv.getWidth();
//        int viewHeight = sv.getHeight();
//        GLES20.glViewport(0, 0, viewWidth, viewHeight);
//        mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);

        mDisplaySurface.swapBuffers();

        //mEncoderSurface=new WindowSurface(mEglCore,mOutputNativeSurface,true);


        // Send it to the video encoder.
        if (!mFileSaveInProgress) {
            mEncoderSurface.makeCurrent();
            GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
            mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);



            mCircEncoder.frameAvailableSoon();
            mEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp());
            mEncoderSurface.swapBuffers();
        }
    }

    /**
     * Custom message handler for main UI thread.
     * <p>
     * Used to handle camera preview "frame available" notifications, and implement the
     * blinking "recording" text.  Receives callback messages from the encoder thread.
     */
    private static class MainHandler extends Handler implements CircularEncoder.Callback {
        public static final int MSG_BLINK_TEXT = 0;
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_FILE_SAVE_COMPLETE = 2;
        public static final int MSG_BUFFER_STATUS = 3;

        private WeakReference<ContinuousCaptureActivity> mWeakActivity;

        public MainHandler(ContinuousCaptureActivity activity) {
            mWeakActivity = new WeakReference<ContinuousCaptureActivity>(activity);
        }

        // CircularEncoder.Callback, called on encoder thread
        @Override
        public void fileSaveComplete(int status) {
            sendMessage(obtainMessage(MSG_FILE_SAVE_COMPLETE, status, 0, null));
        }

        // CircularEncoder.Callback, called on encoder thread
        @Override
        public void bufferStatus(long totalTimeMsec) {
            sendMessage(obtainMessage(MSG_BUFFER_STATUS,
                    (int) (totalTimeMsec >> 32), (int) totalTimeMsec));
        }

        @Override
        public void handleMessage(Message msg) {
            ContinuousCaptureActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what) {
                case MSG_BLINK_TEXT: {
                   break;
                }
                case MSG_FRAME_AVAILABLE: {
                    activity.drawFrame();
                    break;
                }
                case MSG_FILE_SAVE_COMPLETE: {
                    activity.fileSaveComplete(msg.arg1);
                    break;
                }
                case MSG_BUFFER_STATUS: {
                    long duration = (((long) msg.arg1) << 32) |
                                    (((long) msg.arg2) & 0xffffffffL);
                    activity.updateBufferStatus(duration);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }
}
