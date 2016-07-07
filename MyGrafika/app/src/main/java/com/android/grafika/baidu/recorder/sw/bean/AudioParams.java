package com.android.grafika.baidu.recorder.sw.bean;

import java.io.Serializable;

import android.media.AudioFormat;

/**
 * 音频相关参数
 * 
 * @author Andy Young @ Baidu.com
 *
 */
public class AudioParams implements Serializable {

    private static final long serialVersionUID = 1L;
    private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int nChannels = 2;
    private int sampleRateInHz = 44100;

    /**
     * 获取音频配置，默认设置AudioFormat.CHANNEL_IN_STEREO
     * 
     * @return 返回音频配置
     */
    public int getChannelConfig() {
        return channelConfig;
    }

    /**
     * 设置音频配置，默认设置AudioFormat.CHANNEL_IN_STEREO
     * 
     * @param channelConfig
     */
    public void setChannelConfig(int channelConfig) {
        this.channelConfig = channelConfig;
    }

    /**
     * 获取音频格式，默认AudioFormat.ENCODING_PCM_16BIT
     * 
     * @return 返回AudioFormat
     */
    public int getAudioFormat() {
        return audioFormat;
    }

    /**
     * 设置音频格式，默认AudioFormat.ENCODING_PCM_16BIT
     * 
     * @param audioFormat
     *            音频格式，参考AudioFormat
     */
    public void setAudioFormat(int audioFormat) {
        this.audioFormat = audioFormat;
    }

    /**
     * 获取音频通道数,默认2
     * 
     * @return 返回通道数
     */
    public int getnChannels() {
        return nChannels;
    }

    /**
     * 设置音频通道数，默认2
     * 
     * @param nChannels
     *            通道数
     */
    public void setnChannels(int nChannels) {
        this.nChannels = nChannels;
    }

    /**
     * 获取音频采样率，默认44100
     * 
     * @return 返回音频采样率
     */
    public int getSampleRateInHz() {
        return sampleRateInHz;
    }

    /**
     * 设置音频采样率，会自动判断手机支持的采样率，默认44100
     * 
     * @param sampleRateInHz
     *            采样率
     */
    public void setSampleRateInHz(int sampleRateInHz) {
        this.sampleRateInHz = sampleRateInHz;
    }
}
