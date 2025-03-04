package com.jieli.opus.data.model.opus;

/**
 * OpusConfiguration
 *
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc OPUS编解码选项
 * @since 2024/10/23
 */
public class OpusConfiguration {

    /**
     * 单声道
     */
    public static final int CHANNEL_MONO = 0x01;
    /**
     * 双声道
     */
    public static final int CHANNEL_DUAL = 0x02;

    /**
     * 采样率 -- 8000Hz
     */
    public static final int SAMPLE_RATE_8K = 8000;
    /**
     * 采样率 -- 16000Hz
     */
    public static final int SAMPLE_RATE_16k = 16000;
    /**
     * 采样率 -- 32000Hz
     */
    public static final int SAMPLE_RATE_32K = 32000;
    /**
     * 采样率 -- 48000Hz
     */
    public static final int SAMPLE_RATE_48K = 48000;

    /**
     * 支持采样率数组
     */
    public static final int[] SAMPLE_RATE_ARRAY = new int[]{
            SAMPLE_RATE_8K, SAMPLE_RATE_16k, SAMPLE_RATE_32K, SAMPLE_RATE_48K
    };

    /**
     * 是否具有协议头
     */
    private boolean hasHead;
    /**
     * 通道数。取值范围:[1, 2]
     */
    private int channel = CHANNEL_MONO;
    /**
     * 采样率。参考值:{8000, 16000, 32000, 48000}
     */
    private int sampleRate = SAMPLE_RATE_16k;
    /**
     * 数据包长度。仅hasHead = false时生效。
     */
    private int packetSize = 40;

    public boolean isHasHead() {
        return hasHead;
    }

    public OpusConfiguration setHasHead(boolean hasHead) {
        this.hasHead = hasHead;
        return this;
    }

    public int getChannel() {
        return channel;
    }

    public OpusConfiguration setChannel(int channel) {
        if (channel == CHANNEL_MONO || channel == CHANNEL_DUAL) {
            this.channel = channel;
        }
        return this;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public OpusConfiguration setSampleRate(int sampleRate) {
        if (isValidSampleRate(sampleRate)) {
            this.sampleRate = sampleRate;
        }
        return this;
    }

    public int getPacketSize() {
        return packetSize;
    }

    public OpusConfiguration setPacketSize(int packetSize) {
        if (packetSize > 0) {
            this.packetSize = packetSize;
        }
        return this;
    }

    private boolean isValidSampleRate(int sampleRate) {
        for (int rate : SAMPLE_RATE_ARRAY) {
            if (rate == sampleRate) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "OpusDecodeOption{" +
                "hasHead=" + hasHead +
                ", channel=" + channel +
                ", sampleRate=" + sampleRate +
                ", packetSize=" + packetSize +
                '}';
    }
}
