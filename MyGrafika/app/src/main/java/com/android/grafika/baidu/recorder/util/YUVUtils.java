package com.android.grafika.baidu.recorder.util;

import android.media.MediaCodecInfo;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by andy on 4/21/16.
 */
public class YUVUtils {

    // for the vbuffer for YV12(android YUV), @see below:
    // https://developer.android.com/reference/android/hardware/Camera.Parameters.html#setPreviewFormat(int)
    // https://developer.android.com/reference/android/graphics/ImageFormat.html#YV12
    public static int getYuvBuffer(int width, int height) {
        // stride = ALIGN(width, 16)
        int stride = (int) Math.ceil(width / 16.0) * 16;
        // y_size = stride * height
        int y_size = stride * height;
        // c_stride = ALIGN(stride/2, 16)
        int c_stride = (int) Math.ceil(width / 32.0) * 16;
        // c_size = c_stride * height/2
        int c_size = c_stride * height / 2;
        // size = y_size + c_size * 2
        return y_size + c_size * 2;
    }

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    public static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {
        /*
         * COLOR_TI_FormatYUV420PackedSemiPlanar is NV21
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2 + 1] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    public static byte[] YV12toYUV420SemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {
        /*
         * COLOR_FormatYUV420SemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    public static byte[] NV21toYUV420PackedPlanar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i + qFrameSize] = input[frameSize + i * 2 + 1]; // Cb (U)
            output[frameSize + i] = input[frameSize + i * 2]; // Cr (V)
        }

        return output;
    }

    // the color transform, @see http://stackoverflow.com/questions/15739684/mediacodec-and-camera-color-space-incorrect
    public static byte[] NV21toYUV420SemiPlanar(final byte[] input, final byte[] output, final int width, final int height) {
        /*
         * COLOR_FormatYUV420SemiPlanar is NV12
         * We convert by putting the corresponding U and V bytes together (interleaved).
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i * 2 + 1]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i * 2]; // Cr (V)
        }
        return output;
    }

    public static byte[] NV21toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i] = input[frameSize + i * 2 + 1]; // Cb (U)
            output[frameSize + i + qFrameSize] = input[frameSize + i * 2]; // Cr (V)
        }

        return output;
    }

    public static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {
        /*
         * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V reversed.
         * So we just have to reverse U and V.
         */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb (U)
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr (V)

        return output;
    }

    public void dumpYUVData(byte[] buffer, int len, String name) {
        File f = new File(Environment.getExternalStorageDirectory().getPath() + "/tmp/", name);
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(buffer);
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static byte[] CropYuv(int src_format, byte[] src_yuv, int src_width, int src_height, int dst_width,
            int dst_height) {
        byte[] dst_yuv = null;
        if (src_yuv == null)
            return dst_yuv;
        // simple implementation: copy the corner
        if (src_width == dst_width && src_height == dst_height) {
            dst_yuv = src_yuv;
        } else {
            dst_yuv = new byte[(int) (dst_width * dst_height * 1.5)];
            switch (src_format) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar: // I420
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar: // YV12
            {
                // copy Y
                int src_yoffset = 0;
                int dst_yoffset = 0;
                for (int i = 0; i < dst_height; i++) {
                    System.arraycopy(src_yuv, src_yoffset, dst_yuv, dst_yoffset, dst_width);
                    src_yoffset += src_width;
                    dst_yoffset += dst_width;
                }

                // copy u
                int src_uoffset = 0;
                int dst_uoffset = 0;
                src_yoffset = src_width * src_height;
                dst_yoffset = dst_width * dst_height;
                for (int i = 0; i < dst_height / 2; i++) {
                    System.arraycopy(src_yuv, src_yoffset + src_uoffset, dst_yuv, dst_yoffset + dst_uoffset,
                            dst_width / 2);
                    src_uoffset += src_width / 2;
                    dst_uoffset += dst_width / 2;
                }

                // copy v
                int src_voffset = 0;
                int dst_voffset = 0;
                src_uoffset = src_width * src_height + src_width * src_height / 4;
                dst_uoffset = dst_width * dst_height + dst_width * dst_height / 4;
                for (int i = 0; i < dst_height / 2; i++) {
                    System.arraycopy(src_yuv, src_uoffset + src_voffset, dst_yuv, dst_uoffset + dst_voffset,
                            dst_width / 2);
                    src_voffset += src_width / 2;
                    dst_voffset += dst_width / 2;
                }

            }
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar: // NV12
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar: // NV21
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar: {
                // copy Y
                int src_yoffset = 0;
                int dst_yoffset = 0;
                for (int i = 0; i < dst_height; i++) {
                    System.arraycopy(src_yuv, src_yoffset, dst_yuv, dst_yoffset, dst_width);
                    src_yoffset += src_width;
                    dst_yoffset += dst_width;
                }

                // copy u and v
                int src_uoffset = 0;
                int dst_uoffset = 0;
                src_yoffset = src_width * src_height;
                dst_yoffset = dst_width * dst_height;
                for (int i = 0; i < dst_height / 2; i++) {
                    System.arraycopy(src_yuv, src_yoffset + src_uoffset, dst_yuv, dst_yoffset + dst_uoffset, dst_width);
                    src_uoffset += src_width;
                    dst_uoffset += dst_width;
                }
            }
                break;

            default: {
                dst_yuv = null;
            }
                break;
            }
        }
        return dst_yuv;
    }
}
