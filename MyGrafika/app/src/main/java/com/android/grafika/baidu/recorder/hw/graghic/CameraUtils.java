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

package com.android.grafika.baidu.recorder.hw.graghic;

import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.util.Log;

import java.util.List;

/**
 * Camera-related utility functions.
 */
public class CameraUtils {
    private static final String TAG = "CameraUtils";

    /**
     * Attempts to find a preview size that matches the provided width and height (which
     * specify the dimensions of the encoded video).  If it fails to find a match it just
     * uses the default preview size for video.
     * <p>
     * TODO: should do a best-fit match, e.g.
     * https://github.com/commonsguy/cwac-camera/blob/master/camera/src/com/commonsware/cwac/camera/CameraUtils.java
     */
    public static Camera.Size choosePreviewSize(Camera.Parameters parms, int width, int height) {
        // We should make sure that the requested MPEG size is less than the preferred
        // size, and has the same aspect ratio.
        Camera.Size ppsfv = parms.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            Log.d(TAG, "Camera preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
        }

        //for (Camera.Size size : parms.getSupportedPreviewSizes()) {
        //    Log.d(TAG, "supported: " + size.width + "x" + size.height);
        //}

        for (Camera.Size size : parms.getSupportedPreviewSizes()) {
            if (size.width == width && size.height == height) {
                parms.setPreviewSize(width, height);
                return size;
            }
        }

        Log.w(TAG, "Unable to set preview size to " + width + "x" + height);
        if (ppsfv != null) {
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
        }
        // else use whatever the default size is
        return ppsfv;
    }

    /**
     * Attempts to find a fixed preview frame rate that matches the desired frame rate.
     * <p>
     * It doesn't seem like there's a great deal of flexibility here.
     * <p>
     * TODO: follow the recipe from http://stackoverflow.com/questions/22639336/#22645327
     *
     * @return The expected frame rate, in thousands of frames per second.
     */
    public static int chooseFixedPreviewFps(Camera.Parameters parms, int desiredThousandFps) {
        List<int[]> supported = parms.getSupportedPreviewFpsRange();

        for (int[] entry : supported) {
            //Log.d(TAG, "entry: " + entry[0] + " - " + entry[1]);
            if ((entry[0] == entry[1]) && (entry[0] == desiredThousandFps)) {
                parms.setPreviewFpsRange(entry[0], entry[1]);
                return entry[0];
            }
        }

        int[] tmp = new int[2];
        parms.getPreviewFpsRange(tmp);
        int guess;
        if (tmp[0] == tmp[1]) {
            guess = tmp[0];
        } else {
            guess = tmp[1] / 2;     // shrug
        }

        Log.d(TAG, "Couldn't find match for " + desiredThousandFps + ", using " + guess);
        return guess;
    }

    public static void chooseFlashMode(Camera.Parameters parameters, String flashMode) {
        List<String> supportedFlashModes = parameters.getSupportedFlashModes();

        if (supportedFlashModes != null) {
            if (supportedFlashModes.contains(flashMode)) {
                parameters.setFlashMode(flashMode);
            }
        }
    }

    public static void chooseFocusMode(Camera.Parameters parameters, String focusMode) {
        List<String> supportedFocusModes = parameters.getSupportedFocusModes();

        if (supportedFocusModes != null) {
            if (supportedFocusModes.contains(focusMode)) {
                parameters.setFocusMode(focusMode);
            }
        }
    }

    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    private static Rect calculateTapArea(float x, float y, int width, int height, float coefficient) {
        float focusAreaSize = 200;
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();
        int centerX = (int) ((x / width) * 2000 - 1000);
        int centerY = (int) ((y / height) * 2000 - 1000);
        int left = clamp(centerX - (areaSize / 2), -1000, 1000);
        int top = clamp(centerY - (areaSize / 2), -1000, 1000);
        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        return new Rect(Math.round(rectF.left), Math.round(rectF.top),
                Math.round(rectF.right), Math.round(rectF.bottom));
    }

    public static void chooseFocusPoint(Camera.Parameters parameters, String focusMode,  int x, int y, int width, int height) {
        List<String> supportedModes = parameters.getSupportedFocusModes();
        if (supportedModes!=null && supportedModes.contains(focusMode)) {
            parameters.setFocusMode(focusMode);

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
        }
    }

    public static void chooseSceneMode(Camera.Parameters parameters, String sceneMode) {
        List<String> supportedSceneModes = parameters.getSupportedSceneModes();

        if (supportedSceneModes != null) {
            if (supportedSceneModes.contains(sceneMode)) {
                parameters.setSceneMode(sceneMode);
            }
        }
    }

    public static void chooseWhiteBalance(Camera.Parameters parameters, String whiteBalance) {
        List<String> supportedWhiteBalance = parameters.getSupportedWhiteBalance();

        if (supportedWhiteBalance != null) {
            if (supportedWhiteBalance.contains(whiteBalance)) {
                parameters.setWhiteBalance(whiteBalance);
            }
        }
    }

}
