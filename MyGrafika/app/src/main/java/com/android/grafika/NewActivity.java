package com.android.grafika;

import android.app.Activity;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import android.widget.Toast;

import com.android.grafika.baidu.recorder.api.*;
import com.android.grafika.baidu.recorder.hw.device.VideoCaptureDevice;
import com.android.grafika.baidu.recorder.hw.encoder.VideoEncoder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by jiayuanmin on 16/6/28.
 */
public class NewActivity extends Activity  {

    private SurfaceView mCameraView = null;
    private Button mFlashStateButton = null;
    private Button mCameraStateButton = null;
    private SurfaceHolder mHolder = null;
    private int mCurrentCamera = -1;
    private boolean isFlashOn = false;
    private int mVideoWidth = 1280;
    private int mVideoHeight = 720;
    private int mFrameRate = 15;
    private int mBitrate = 2048000;
    private final String TAG="new Grafika";
    private com.android.grafika.baidu.recorder.api.LiveSession mLiveSession=null;
    private Button mRecorderButton = null;
    private VideoCaptureDevice mVideoCaptureDevice;
    private FileChannel ops;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        win.requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_continuous_capture);
        initUIElements();
        mCurrentCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        isFlashOn = false;
        initRTMPSession(mCameraView.getHolder());
        File file=new File(Environment.getExternalStorageDirectory().getAbsolutePath(),"test.h264");
        try {
            ops=new FileOutputStream(file).getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        mFlashStateButton.setOnClickListener(flashListener);
        mCameraStateButton.setOnClickListener(switchlistener);
        mRecorderButton.setOnClickListener(saveListener);


    }





    private void initUIElements() {
        mRecorderButton = (Button) findViewById(R.id.capture_button);
        mRecorderButton.setEnabled(true);
        mCameraView = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        mFlashStateButton = (Button) findViewById(R.id.flash_ctl);
        mCameraStateButton = (Button) findViewById(R.id.camera_ctl);
    }

    private void initRTMPSession(SurfaceHolder sh) {
        Log.d(TAG, "Calling initRTMPSession...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            mLiveSession = new LiveSessionHW(this, mVideoWidth, mVideoHeight, mFrameRate, mBitrate, mCurrentCamera);
        else
            mLiveSession = new LiveSessionSW(this, mVideoWidth, mVideoHeight, mFrameRate, mBitrate, mCurrentCamera);
        mLiveSession.bindPreviewDisplay(sh);
        mLiveSession.prepareSessionAsync();
        mVideoCaptureDevice=mLiveSession.getDevice();
    }

    View.OnClickListener saveListener=new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mRecorderButton.setEnabled(false);
            if(mVideoCaptureDevice.isRecording){
                mVideoCaptureDevice.stopRecording();
                mRecorderButton.setBackgroundResource(R.drawable.to_start);
            }else {
                mVideoCaptureDevice.save();
                mRecorderButton.setBackgroundResource(R.drawable.to_stop);
            }
            mRecorderButton.setEnabled(true);

        }
    };

    View.OnClickListener flashListener=new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mCameraStateButton.setEnabled(false);
            if (mCurrentCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mLiveSession.toggleFlash(!isFlashOn);
                isFlashOn = !isFlashOn;
                if (isFlashOn) {
                    mFlashStateButton.setBackgroundResource(R.drawable.flash_on);
                } else {
                    mFlashStateButton.setBackgroundResource(R.drawable.flash_off);
                }
            } else {
                Toast.makeText(NewActivity.this, "抱歉！前置摄像头不支持切换闪光灯！", Toast.LENGTH_SHORT).show();
            }
            mCameraStateButton.setEnabled(true);

        }
    };

    View.OnClickListener switchlistener=new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mCameraStateButton.setEnabled(false);
            if (mLiveSession.canSwitchCamera()) {
                if (mCurrentCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mCurrentCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
                    mLiveSession.switchCamera(mCurrentCamera);
                } else {
                    mCurrentCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
                    mLiveSession.switchCamera(mCurrentCamera);
                }
                isFlashOn = false;
                mFlashStateButton.setBackgroundResource(R.drawable.flash_off);
            } else {
                Toast.makeText(NewActivity.this, "抱歉！该分辨率下不支持切换摄像头！", Toast.LENGTH_SHORT).show();
            }
            mCameraStateButton.setEnabled(true);

        }
    };



}
