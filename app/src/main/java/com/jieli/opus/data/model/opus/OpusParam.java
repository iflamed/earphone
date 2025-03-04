package com.jieli.opus.data.model.opus;

import androidx.annotation.NonNull;

/**
 * OpusParam
 *
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc OPUS编解码参数
 * @since 2024/5/10
 */
public class OpusParam {
    /**
     * 数据流方式
     */
    public static final int WAY_STREAM = 0;
    /**
     * 文件方式
     */
    public static final int WAY_FILE = 1;

    /**
     * OPUS选项
     */
    @NonNull
    private final OpusConfiguration option;
    /**
     * 操作方式
     */
    private int way = WAY_STREAM;
    /**
     * 是否播放音频
     */
    private boolean isPlayAudio;

    public OpusParam(@NonNull OpusConfiguration option) {
        this.option = option;
    }

    @NonNull
    public OpusConfiguration getOption() {
        return option;
    }

    public int getWay() {
        return way;
    }

    public boolean isPlayAudio() {
        return isPlayAudio;
    }

    public OpusParam setWay(int way) {
        this.way = way;
        return this;
    }

    public OpusParam setPlayAudio(boolean playAudio) {
        isPlayAudio = playAudio;
        return this;
    }

    @Override
    public String toString() {
        return "OpusDecodeParam{" +
                "option=" + option +
                ", way=" + way +
                ", isPlayAudio=" + isPlayAudio +
                '}';
    }
}
