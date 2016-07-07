package com.android.grafika.baidu.recorder.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

public class VideoUtil {

    private static final String TAG = "VideoUtil";
    private Size srcImageSize = null;
    // size of processed image
    private Size tagImageSize = null;
    private byte[] mOutputBuffer = null;
    private byte[] mCroppedBuffer = null;
    private byte[] mRotatedBuffer = null;
    private boolean isNeedCrop = false;
    private boolean mCropSwitchFlag = false;

    private class Size {
        public int width;
        public int height;

        public Size(int w, int h) {
            width = w;
            height = h;
        }
    }

    public void setVideoSize(int width, int height) {
        srcImageSize = new Size(width, height);
        calTargetImageSize();

        mCroppedBuffer = new byte[tagImageSize.height * tagImageSize.width * 3 / 2];
        mRotatedBuffer = new byte[tagImageSize.height * tagImageSize.width * 3 / 2];
    }

    private void calTargetImageSize() {
        if (srcImageSize == null) {
            return;
        }
        isNeedCrop = true;
        if (mCropSwitchFlag && srcImageSize.width * 9 < srcImageSize.height * 16) {
            tagImageSize = new Size(srcImageSize.width, srcImageSize.width * 9 / 16);
        } else {
            tagImageSize = new Size(srcImageSize.width, srcImageSize.height);
            isNeedCrop = false;
        }
    }

    public int getTargetVideoWidth() {
        return isOrientationPortrait ? tagImageSize.height : tagImageSize.width;
    }

    public int getTargetVideoHeight() {
        return isOrientationPortrait ? tagImageSize.width : tagImageSize.height;
    }

    private boolean isOrientationPortrait = false;

    public void setIsOrientationPortrait(boolean r) {
        isOrientationPortrait = r;
    }

    private boolean isCameraFront = false;

    public void setIsCameraFront(boolean f) {
        isCameraFront = f;
    }

    public byte[] dealVideoFrame(byte[] data) {
        if (!isNeedCrop) {
            mCroppedBuffer = data;
            // saveRawData(data, data.length, "Test_"+System.nanoTime()+".raw");
        } else {
            cropYUVImage(data);
        }
        mOutputBuffer = mCroppedBuffer;

        if (isOrientationPortrait || isCameraFront) {
            rotateNV21(mCroppedBuffer, mRotatedBuffer, tagImageSize.width, tagImageSize.height);
            mOutputBuffer = mRotatedBuffer;
        }
        return mOutputBuffer;
    }

    private void cropYUVImage(byte[] src) {
        if (tagImageSize.height != srcImageSize.height) {
            int startRow = (srcImageSize.height - tagImageSize.height) / 4 * 2;
            // Copying the Y-color
            System.arraycopy(src, startRow * srcImageSize.width, mCroppedBuffer, 0,
                    tagImageSize.width * tagImageSize.height);
            // Copying the UV-color
            System.arraycopy(src, (srcImageSize.height + startRow / 2) * srcImageSize.width, mCroppedBuffer,
                    tagImageSize.width * tagImageSize.height, tagImageSize.height * tagImageSize.width / 2);
        }
    }

    private byte[] rotateNV21(final byte[] yuv, byte[] output, final int width, final int height) {
        final int frameSize = width * height;
        final boolean swap = isOrientationPortrait;
        final boolean xflip = !isCameraFront;
        final boolean yflip = isCameraFront && isOrientationPortrait;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                final int yIn = j * width + i;
                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                final int vIn = uIn + 1;

                final int wOut = swap ? height : width;
                final int hOut = swap ? width : height;
                final int iSwapped = swap ? j : i;
                final int jSwapped = swap ? i : j;
                final int iOut = xflip ? wOut - iSwapped - 1 : iSwapped;
                final int jOut = yflip ? hOut - jSwapped - 1 : jSwapped;

                final int yOut = jOut * wOut + iOut;
                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                final int vOut = uOut + 1;

                output[yOut] = (byte) (0xff & yuv[yIn]);
                output[uOut] = (byte) (0xff & yuv[uIn]);
                output[vOut] = (byte) (0xff & yuv[vIn]);
            }
        }
        return output;
    }

    private void decodeNV21(int[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;

        int yp = 0;
        for (int j = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width;
            int u = 0;
            int v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) {
                    y = 0;
                }
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                if (r < 0) {
                    r = 0;
                } else if (r > 262143) {
                    r = 262143;
                }
                if (g < 0) {
                    g = 0;
                } else if (g > 262143) {
                    g = 262143;
                }
                if (b < 0) {
                    b = 0;
                } else if (b > 262143) {
                    b = 262143;
                }

                rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

    private void saveBitmap(Bitmap bm, String name) {
        File f = new File(Environment.getExternalStorageDirectory().getPath() + "/tmp/", name);
        if (f.exists()) {
            f.delete();
        }
        try {
            Log.d(TAG, "Saving bitmap " + name);
            FileOutputStream out = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();
            Log.d(TAG, "Bitmap " + name + " saved.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveRawData(byte[] buffer, int len, String name) {
        File f = new File(Environment.getExternalStorageDirectory().getPath() + "/tmp/", name);
        if (f.exists()) {
            f.delete();
        }
        try {
            Log.d(TAG, "Saving raw data to " + name);
            FileOutputStream out = new FileOutputStream(f);
            out.write(buffer);
            out.flush();
            out.close();
            Log.d(TAG, "File " + name + " saved.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
