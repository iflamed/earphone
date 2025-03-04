package com.jieli.opus.data.model.result;

/**
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc 操作结果
 * @since 2024/1/23
 */
public class OpResult<T> {
    private int op;
    private int code = -1;
    private String message;
    private T data;

    public int getOp() {
        return op;
    }

    public OpResult<T> setOp(int op) {
        this.op = op;
        return this;
    }

    public int getCode() {
        return code;
    }

    public OpResult<T> setCode(int code) {
        this.code = code;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public OpResult<T> setMessage(String message) {
        this.message = message;
        return this;
    }

    public T getData() {
        return data;
    }

    public OpResult<T> setData(T data) {
        this.data = data;
        return this;
    }

    public boolean isSuccess() {
        return code == 0;
    }

    @Override
    public String toString() {
        return "OpResult{" +
                "op=" + op +
                ", code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
