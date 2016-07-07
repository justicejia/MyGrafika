package com.android.grafika.baidu.recorder.hw.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import com.android.grafika.baidu.recorder.hw.muxer.FlvMuxer;

import java.nio.ByteBuffer;

/**
 * Created by andy on 4/19/16.
 */
public class AudioEncoder {
    private final static String TAG = AudioEncoder.class.getName();
    private String mAudioCodecMimeType = null;
    private MediaCodec mAACEncoder = null;
    private MediaCodec.BufferInfo mCodecBufferInfo = null;
    private FlvMuxer mFlvMuxer = null;
    private int mAudioTrack = -1;
    private volatile boolean isEncoding = false;
    private MediaFormat mFormat = null;

    public AudioEncoder(String mime_type, FlvMuxer muxer) {
        mAudioCodecMimeType = mime_type;
        MediaCodecInfo codecInfo = selectCodec(mime_type);
        try {
            mAACEncoder = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        mCodecBufferInfo = new MediaCodec.BufferInfo();
        mFlvMuxer = muxer;
    }

    public void setFlvMuxer(FlvMuxer muxer) {
        mFlvMuxer = muxer;
        if (mFlvMuxer != null) mAudioTrack = mFlvMuxer.addTrack(mFormat);
    }

    public boolean setupEncoder(int sample_rate, int channel, int bitrate) {
        // setup the aencoder.
        // @see https://developer.android.com/reference/android/media/MediaCodec.html
        MediaFormat format = MediaFormat.createAudioFormat(mAudioCodecMimeType, sample_rate, channel);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * bitrate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAACEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (mAACEncoder != null) mAACEncoder.start();

        mFormat = format;
        if (mFlvMuxer != null) {
            mAudioTrack = mFlvMuxer.addTrack(format);
            Log.i(TAG, String.format("muxer add audio track index=%d", mAudioTrack));
        }
        return true;
    }

    public void start() {
        isEncoding = true;
    }

    public void stop() {
        isEncoding = false;
    }

    public void push(byte[] audioSample, long ptsInUs) {
        // feed the aencoder with yuv frame, got the encoded 264 es stream.
        ByteBuffer[] inBuffers = null;
        if (!isEncoding) return;
        if (mFlvMuxer == null) return;
        long lastPtsInMs = mFlvMuxer.getLastSentPacketPtsInMs();
        if (lastPtsInMs > 0 && ptsInUs / 1000 - lastPtsInMs
                > FlvMuxer.THRESHOLD_OF_LATENCY_IN_MS_TO_DROP_PACKET) {
            mFlvMuxer.clearSendingBuffer();
        }
        inBuffers = mAACEncoder.getInputBuffers();
        
        ByteBuffer[] outBuffers = null;
        if (!isEncoding) return;
        outBuffers = mAACEncoder.getOutputBuffers();     //这里获取了音频数据

        if (isEncoding) {
            int inBufferIndex = mAACEncoder.dequeueInputBuffer(-1);
            //Log.i(TAG, String.format("try to dequeue input vbuffer, ii=%d", inBufferIndex));
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                bb.put(audioSample, 0, audioSample.length);
                //Log.i(TAG, String.format("feed PCM to encode %dB, pts=%d", data.length, pts / 1000));
                //FlvMuxer.srs_print_bytes(TAG, data, data.length);
                if (isEncoding) mAACEncoder.queueInputBuffer(inBufferIndex, 0, audioSample.length, ptsInUs, 0);
            }
        }

        while (isEncoding) {
            int outBufferIndex = mAACEncoder.dequeueOutputBuffer(mCodecBufferInfo, 0);
            //Log.i(TAG, String.format("try to dequeue output vbuffer, ii=%d, oi=%d", inBufferIndex, outBufferIndex));
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                //Log.i(TAG, String.format("encoded aac %dB, pts=%d", aebi.size, aebi.presentationTimeUs / 1000));
                //FlvMuxer.srs_print_bytes(TAG, bb, aebi.size);
                if (isEncoding) onEncodedAacFrame(bb, mCodecBufferInfo);
                if (isEncoding) mAACEncoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    // when got encoded aac raw stream.
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) {
        if (mFlvMuxer == null) return;
        try {
            mFlvMuxer.writeSampleData(mAudioTrack, es, bi);
        } catch (Exception e) {
            Log.e(TAG, "muxer write audio sample failed.");
            e.printStackTrace();
        }
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
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

    public void release() {
        if (mAACEncoder != null) {
            mAACEncoder.stop();
            mAACEncoder.release();
            mAACEncoder = null;
        }
        Log.d(TAG, "The aac encoder was destroyed!");
    }
}
