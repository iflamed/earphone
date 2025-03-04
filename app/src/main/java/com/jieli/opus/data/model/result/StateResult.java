package com.jieli.opus.data.model.result;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 状态结果
 * @since 2024/1/23
 */
public class StateResult<T> extends OpResult<T> {
    public static final int STATE_IDLE = 0;
    public static final int STATE_WORKING = 1;
    public static final int STATE_FINISH = 2;

    private int state = STATE_IDLE;

    public int getState() {
        return state;
    }

    public StateResult<T> setState(int state) {
        this.state = state;
        return this;
    }
}
