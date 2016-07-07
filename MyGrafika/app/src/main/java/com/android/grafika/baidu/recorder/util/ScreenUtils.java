package com.android.grafika.baidu.recorder.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

public class ScreenUtils {

    public static int getWight(Context mContext) {
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;
        return screenWidth;
    }

    public static int getHeight(Context mContext) {
        DisplayMetrics dm = new DisplayMetrics();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenHeight = dm.heightPixels;
        return screenHeight;
    }

    public static boolean isInRight(Context mContext, int xWeight) {
        return (xWeight > getHeight(mContext) * 3 / 4);
    }

    public static boolean isInLeft(Context mContext, int xWeight) {
        return (xWeight < getWight(mContext) * 1 / 4);
    }

    /**
     * 是否横屏
     * 
     * @param mContext
     * @return
     */
    public static boolean screenIsLanscape(Context mContext) {
        return mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * 获取当前屏幕状态
     * 
     * @param mContext
     * @return
     */
    public static int getOrientation(Context mContext) {
        return mContext.getResources().getConfiguration().orientation;
    }

    /**
     * 根据屏幕宽度获取16-10的屏幕高度
     */
    public static int getImageWidth16to10(int heightPx) {
        return (int) (heightPx * 1.6);
    }

    public static int getImageHeight16to10(int widthPx) {
        return (int) (widthPx / 1.6);
    }

    public static int getImageHeight16to9(int widthPx) {
        return (int) ((widthPx * 9) / 16);
    }

    public static int getImageHeight7to2(int widthPx) {
        return (int) ((widthPx * 2) / 7);
    }
}
