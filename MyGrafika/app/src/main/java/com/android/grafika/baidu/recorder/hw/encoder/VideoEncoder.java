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

package com.android.grafika.baidu.recorder.hw.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.android.grafika.CircularEncoderBuffer;
import com.android.grafika.baidu.recorder.hw.muxer.FlvMuxer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 视频编码器
 *编码视频在一个固定大小的圆形缓冲区。
 * <p>
 *最明显的方法是将每个数据包存储在自己的缓冲区和钩
 *一个链表。这种方法的问题在于,它需要常数
 *配置,这意味着我们将驾驶分心的帧速率和GC
 *比特率增加。相反,我们为视频数据和元数据创建固定大小的线程池,
 *这需要更多的为我们工作,但避免分配的稳定状态。
 * <p>
 *视频必须开始同步帧(/ k /一个关键帧,/ k / I-帧)。当
 *循环缓冲包装,我们要么需要删除所有的数据帧之间
 *列表的头和第二帧同步,或者知道文件保存功能
 *需要向前扫描同步帧之前可以保存数据。
 * <p>
 *当我们告诉保存快照,我们创建一个MediaMuxer,写所有的帧,
 *然后回到我们在做什么。
 */
public class VideoEncoder {
    private static final String TAG = "VideoEncoder";
    private static final boolean VERBOSE = false;
    private EncoderThread mEncoderThread;
    private Surface mInputSurface;
    private MediaCodec mAVCEncoder;
    private MediaCodecInfo mCodecInfo;
    private MediaCodecInfo.CodecProfileLevel mMaxCodecProfileLevel;
    private int mPreferedCodecProfile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
    private int mPreferedCodecLevel = MediaCodecInfo.CodecProfileLevel.AVCLevel42;
    private static final String MEDIACODEC_AVC_CODEC_PROFILE_KEY = "profile";
    private static final String MEDIACODEC_AVC_CODEC_LEVEL_KEY = "level";
    private MediaFormat mFormat = null;

    private String mVideoCodecMimeType = null;
    private int mVideoTrack = 100;
    private FlvMuxer mFlvMuxer = null;
    private volatile boolean isEncoding = false;





    /**
     *配置编码器,准备输入表面。
     */
    public VideoEncoder(String mimeType, FlvMuxer muxer) throws IOException {
        // The goal is to size the buffer so that we can accumulate N seconds worth of video,
        // where N is passed in as "desiredSpanSec".  If the codec generates data at roughly
        // the requested bit rate, we can compute it as time * bitRate / bitsPerByte.
        //
        // Sync frames will appear every (frameRate * IFRAME_INTERVAL) frames.  If the frame
        // rate is higher or lower than expected, various calculations may not work out right.
        //
        // Since we have to start muxing from a sync frame, we want to ensure that there's
        // room for at least one full GOP in the buffer, preferrably two.

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.work
        mCodecInfo = selectCodec(mimeType);
        mAVCEncoder = MediaCodec.createByCodecName(mCodecInfo.getName());
        mMaxCodecProfileLevel = selectProfileAndLevel(mCodecInfo, mimeType);
        if (mMaxCodecProfileLevel.profile < mPreferedCodecProfile
                || (mMaxCodecProfileLevel.profile == mPreferedCodecProfile
                    && mMaxCodecProfileLevel.level < mPreferedCodecLevel)) {
            mPreferedCodecProfile = mMaxCodecProfileLevel.profile;
            mPreferedCodecLevel = mMaxCodecProfileLevel.level;
        }

        // Start the encoder thread last.  That way we're sure it can see all of the state
        // we've initialized.
        mFlvMuxer = muxer;
        mVideoCodecMimeType = mimeType;



    }


    public void setFlvMuxer(FlvMuxer muxer) {
        mFlvMuxer = muxer;
        if (mFlvMuxer != null) mVideoTrack = mFlvMuxer.addTrack(mFormat);
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
//            Log.d(TAG, codecInfo.getName()+" ["+codecInfo.getSupportedTypes()[0]+"]");
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    private MediaCodecInfo.CodecProfileLevel selectProfileAndLevel(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);

        MediaCodecInfo.CodecProfileLevel max = null;
        for (int i = 0; i < capabilities.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel pl = capabilities.profileLevels[i];
            Log.i(TAG, "Find a codec profile [" + pl.profile + "|" + pl.level + "]");
            if (max == null || max.level < pl.level || max.profile < pl.profile) {
                max = pl;
            }
        }
        return max;
    }

    /**
     *
     * @param width Width of encoded video, in pixels.  Should be a multiple of 16.
     * @param height Height of encoded video, in pixels.  Usually a multiple of 16 (1080 is ok).
     * @param bitRate Target bit rate, in bits.
     * @param frameRate Expected frame rate.
     * @param desiredSpanSec How many seconds of video we want to have in our buffer at any time.
     */
    public void setupEncoder(int width, int height, int bitRate, int frameRate, int desiredSpanSec) {
        try {
            MediaFormat format = createDefaultFormat(mVideoCodecMimeType,
                    width, height, bitRate, frameRate, desiredSpanSec);
            format.setInteger("profile", mPreferedCodecProfile);
            format.setInteger("level", mPreferedCodecLevel);
//            format.setInteger("profile", mMaxCodecProfileLevel.profile);
//            format.setInteger("level", mMaxCodecProfileLevel.level);
            mAVCEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mFormat = format;
            Log.d(TAG, String.format("Encoder params: [%dx%d]@%dfps, bitrate: %d, profile: %d, level: %d",
                    width, height, frameRate, bitRate*1000,
                    mFormat.getInteger(MEDIACODEC_AVC_CODEC_PROFILE_KEY),
                    mFormat.getInteger(MEDIACODEC_AVC_CODEC_LEVEL_KEY)));
        } catch (Throwable t) {
            MediaFormat format = createDefaultFormat(mVideoCodecMimeType,
                    width, height, bitRate, frameRate, desiredSpanSec);
            mAVCEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mFormat = format;
            Log.d(TAG, String.format("Encoder params: [%dx%d]@%dfps, bitrate: %d",
                    width, height, frameRate, bitRate*1000));
        }
        mInputSurface = mAVCEncoder.createInputSurface();

        if (mFlvMuxer != null) mVideoTrack = mFlvMuxer.addTrack(mFormat);
        if (mAVCEncoder != null) mAVCEncoder.start();
        mEncoderThread = new EncoderThread(this);
        mEncoderThread.start();
        mEncoderThread.waitUntilReady();
    }
    
    private MediaFormat createDefaultFormat(String mimeType,
            int width, int height, int bitRate, int frameRate, int desiredSpanSec) {
        MediaFormat format = MediaFormat.createVideoFormat(mVideoCodecMimeType, width, height);
        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate * 1000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, desiredSpanSec);
        return format;
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void start() {
        isEncoding = true;
    }

    public void stop() {
        isEncoding = false;
    }

    /**
     * Shuts down the encoder thread, and releases encoder resources.
     * <p>
     * Does not return until the encoder thread has stopped.
     */
    public void release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects");

        if (mEncoderThread != null) {
            Handler handler = mEncoderThread.getHandler();
            handler.sendMessage(handler.obtainMessage(EncoderThread.EncoderHandler.MSG_SHUTDOWN));
            try {
                mEncoderThread.join();
            } catch (InterruptedException ie) {
                Log.w(TAG, "Encoder thread join() was interrupted", ie);
            }
        }

        if (mAVCEncoder != null) {
            mAVCEncoder.stop();
            mAVCEncoder.release();
            mAVCEncoder = null;
        }
        Log.d(TAG, "The avc encoder was destroyed!");
    }

    /**
     * Notifies the encoder thread that a new frame will shortly be provided to the encoder.
     * <p>
     * There may or may not yet be data available from the encoder output.  The encoder
     * has a fair mount of latency due to processing, and it may want to accumulate a
     * few additional buffers before producing output.  We just need to drain it regularly
     * to avoid a situation where the producer gets wedged up because there's no room for
     * additional frames.
     * <p>
     * If the caller sends the frame and then notifies us, it could get wedged up.  If it
     * notifies us first and then sends the frame, we guarantee that the output buffers
     * were emptied, and it will be impossible for a single additional frame to block
     * indefinitely.
     */
    public void frameAvailableSoon() {
        Handler handler = mEncoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(
                EncoderThread.EncoderHandler.MSG_FRAME_AVAILABLE_SOON));
    }

    // when got encoded h264 es stream.
    private void onEncodedAnnexbFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        if (mFlvMuxer == null) return;
        try {
            mFlvMuxer.writeSampleData(mVideoTrack, es, bi);
        } catch (Exception e) {
            Log.e(TAG, "muxer write video sample failed.");
            e.printStackTrace();
        }
    }
    public void saveFile(){
        Log.d(TAG,"Handler sendMessager!");
        Handler handler=mEncoderThread.getHandler();
        handler.sendMessage((handler.obtainMessage(EncoderThread.EncoderHandler.MSG_SAVE)));
    }
    public void stopSave(){
        Log.d(TAG,"Handler sendMessager!");
        Handler handler=mEncoderThread.getHandler();
        handler.sendMessage((handler.obtainMessage(EncoderThread.EncoderHandler.MSG_STOP)));

    }

    /**
     * Object that encapsulates the encoder thread.
     * <p>
     * We want to sleep until there's work to do.  We don't actually know when a new frame
     * arrives at the encoder, because the other thread is sending frames directly to the
     * input surface.  We will see data appear at the decoder output, so we can either use
     * an infinite timeout on dequeueOutputBuffer() or wait() on an object and require the
     * calling app wake us.  It's very useful to have all of the buffer management local to
     * this thread -- avoids synchronization -- so we want to do the file muxing in here.
     * So, it's best to sleep on an object and do something appropriate when awakened.
     * <p>
     * This class does not manage the MediaCodec encoder startup/shutdown.  The encoder
     * should be fully started before the thread is created, and not shut down until this
     * thread has been joined.
     */
    private static class EncoderThread extends Thread {
        private VideoEncoder mEncoder;
        private MediaFormat mEncodedFormat;
        private MediaCodec.BufferInfo mBufferInfo;
        private CircularEncoderBuffer mEncBuffer;
        private EncoderHandler mHandler;
        private int mFrameNum;
        private File file;
        private FileChannel fc;
        private Boolean isRecording=false;

        private final Object mLock = new Object();
        private volatile boolean mReady = false;

        public EncoderThread(VideoEncoder mediaCodec) {
            mEncoder = mediaCodec;
            mEncBuffer = new CircularEncoderBuffer(1024000,15,
                    7);
            file=new File(Environment.getExternalStorageDirectory().getAbsolutePath(),"test.h264");
            try {
                fc=new FileOutputStream(file).getChannel();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            mBufferInfo = new MediaCodec.BufferInfo();
        }

        /**
         * Thread entry point.
         * <p>
         * Prepares the Looper, Handler, and signals anybody watching that we're ready to go.
         */
        @Override
        public void run() {
            Looper.prepare();
            mHandler = new EncoderHandler(this);    // must create on encoder thread
            Log.d(TAG, "encoder thread ready");
            synchronized (mLock) {
                mReady = true;
                mLock.notify();    // signal waitUntilReady()
            }

            Looper.loop();

            synchronized (mLock) {
                mReady = false;
                mHandler = null;
            }
            Log.d(TAG, "looper quit");
        }

        /**
         * Waits until the encoder thread is ready to receive messages.
         * <p>
         * Call from non-encoder thread.
         */
        public void waitUntilReady() {
            synchronized (mLock) {
                while (!mReady) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        /**
         * Returns the Handler used to send messages to the encoder thread.
         */
        public EncoderHandler getHandler() {
            synchronized (mLock) {
                // Confirm ready state.
                if (!mReady) {
                    throw new RuntimeException("not ready");
                }
            }
            return mHandler;
        }

        public void saveVideo(){
            isRecording=true;
        }
        public void stopRecord(){
            isRecording=false;
        }








        /**
         * Drains all pending output from the decoder, and adds it to the circular buffer.
         */
        public void drainEncoder() {
            if (!mEncoder.isEncoding) return;
            final int TIMEOUT_USEC = 0;     // no timeout -- check for buffers, bail if none

            ByteBuffer[] encoderOutputBuffers = mEncoder.mAVCEncoder.getOutputBuffers();
            while (mEncoder.isEncoding) {
                int encoderStatus = mEncoder.mAVCEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    break;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    if (mEncoder.isEncoding) encoderOutputBuffers = mEncoder.mAVCEncoder.getOutputBuffers();
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                    if (mEncoder.isEncoding) mEncodedFormat = mEncoder.mAVCEncoder.getOutputFormat();
                    Log.d(TAG, "encoder output format changed: " + mEncodedFormat);
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus);
                    // let's ignore it
                } else {
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null");
                    }



                    if (mBufferInfo.size != 0) {
                        // adjust the ByteBuffer values to match BufferInfo (not needed?)
                        encodedData.position(mBufferInfo.offset);
                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                        if(isRecording){
                            try {
                                fc.write(encodedData);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            Log.d(TAG,"Save!!!!!!");
                        }



                        if (mEncoder.isEncoding) mEncoder.onEncodedAnnexbFrame(encodedData, mBufferInfo);

                        if (VERBOSE) {
                            Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                                    mBufferInfo.presentationTimeUs);
                        }
                    }

                    if (mEncoder.isEncoding) mEncoder.mAVCEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                        break;      // out of while
                    }
                }
            }
        }

        /**
         * Tells the Looper to quit.
         */
        void shutdown() {
            if (VERBOSE) Log.d(TAG, "shutdown");
            Looper.myLooper().quit();
        }

        /**
         * Handler for EncoderThread.  Used for messages sent from the UI thread (or whatever
         * is driving the encoder) to the encoder thread.
         * <p>
         * The object is created on the encoder thread.
         */
        private static class EncoderHandler extends Handler {
            public static final int MSG_FRAME_AVAILABLE_SOON = 1;
            public static final int MSG_SHUTDOWN = 2;
            public static final int MSG_SAVE=3;
            public static final int MSG_STOP=4;


            // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
            // but no real harm in it.
            private WeakReference<EncoderThread> mWeakEncoderThread;

            /**
             * Constructor.  Instantiate object from encoder thread.
             */
            public EncoderHandler(EncoderThread et) {
                mWeakEncoderThread = new WeakReference<EncoderThread>(et);
            }

            @Override  // runs on encoder thread
            public void handleMessage(Message msg) {
                int what = msg.what;
                if (VERBOSE) {
                    Log.v(TAG, "EncoderHandler: what=" + what);
                }

                EncoderThread encoderThread = mWeakEncoderThread.get();
                if (encoderThread == null) {
                    Log.w(TAG, "EncoderHandler.handleMessage: weak ref is null");
                    return;
                }

                switch (what) {
                    case MSG_FRAME_AVAILABLE_SOON:
                        encoderThread.drainEncoder();
                        break;
                    case MSG_SHUTDOWN:
                        encoderThread.shutdown();
                        break;
                    case MSG_SAVE:
                        encoderThread.saveVideo();
                        break;
                    case MSG_STOP:
                        encoderThread.stopRecord();
                        break;
                    default:
                        throw new RuntimeException("unknown message " + what);
                }
            }
        }
    }

    public long getLastSentPacketPtsInMs() {
        return mFlvMuxer == null ? 0 : mFlvMuxer.getLastSentPacketPtsInMs();
    }

    public void clearSendingBuffer() {
        if (mFlvMuxer != null) {
            mFlvMuxer.clearSendingBuffer();
        }
    }


}
