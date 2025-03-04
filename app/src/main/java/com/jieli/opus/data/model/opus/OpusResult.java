package com.jieli.opus.data.model.opus;

/**
 * OpusDecodeResult
 *
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc OPUS解码结果
 * @since 2024/5/10
 */
public class OpusResult {

    /**
     * 输入文件路径
     */
    private final String inFilePath;
    /**
     * 输出文件路径
     */
    private String outFilePath;
    /**
     * 操作方式
     */
    private final int way;

    public OpusResult(String inFilePath, String outFilePath, int way) {
        this.inFilePath = inFilePath;
        this.outFilePath = outFilePath;
        this.way = way;
    }

    public String getInFilePath() {
        return inFilePath;
    }

    public String getOutFilePath() {
        return outFilePath;
    }

    public int getWay() {
        return way;
    }

    public void setOutFilePath(String outFilePath) {
        this.outFilePath = outFilePath;
    }

    @Override
    public String toString() {
        return "OpusDecodeResult{" +
                "inFilePath='" + inFilePath + '\'' +
                ", outFilePath='" + outFilePath + '\'' +
                ", way=" + way +
                '}';
    }
}
