package com.jieli.opus.ui.opus;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.jieli.jl_audio_decode.callback.OnDecodeStreamCallback;
import com.jieli.jl_audio_decode.callback.OnEncodeStreamCallback;
import com.jieli.jl_audio_decode.callback.OnStateCallback;
import com.jieli.jl_audio_decode.constant.ErrorCode;
import com.jieli.jl_audio_decode.exceptions.OpusException;
import com.jieli.jl_audio_decode.opus.OpusManager;
import com.jieli.jl_audio_decode.opus.model.OpusOption;
import com.jieli.logcat.JL_Log;
import com.jieli.opus.data.constant.Constants;
import com.jieli.opus.data.model.opus.OpusConfiguration;
import com.jieli.opus.data.model.opus.OpusParam;
import com.jieli.opus.data.model.opus.OpusResult;
import com.jieli.opus.data.model.result.StateResult;
import com.jieli.opus.tool.AppUtil;
import com.jieli.opus.ui.base.ResourceViewModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * OpusDecodeViewModel
 *
 * @author zqjasonZhong
 * @email zhongzhuocheng@zh-jieli.com
 * @desc OPUS解码逻辑实现
 * @since 2024/5/10
 */
public class OpusViewModel extends ResourceViewModel {

    private static final int OP_DECODE = 0x01;
    private static final int OP_ENCODE = 0x02;

    private static final long WAITING_TIMEOUT = 1000L;

    private static final int MSG_WAITING_DATA_TIMEOUT = 0x1234;

    /**
     * 解码状态回调
     */
    public final MutableLiveData<StateResult<OpusResult>> decodeStateMLD = new MutableLiveData<>();
    /**
     * 播放状态回调
     */
    public final MutableLiveData<Boolean> playStateMLD = new MutableLiveData<>();

    /**
     * 编码状态回调
     */
    public final MutableLiveData<StateResult<OpusResult>> encodeStateMLD = new MutableLiveData<>();

    /**
     * OPUS编解码器
     */
    private OpusManager mOpusManager;
    /**
     * 音频播放器
     */
    private AudioTrack mAudioTrack;
    /**
     * OPUS编码参数
     */
    private OpusParam opusParam;
    /**
     * 文件输出流
     */
    private FileOutputStream foutStream;

    private final Handler uiHandler = new Handler(Looper.getMainLooper(), msg -> {
        if (MSG_WAITING_DATA_TIMEOUT == msg.what) {
            int way = msg.arg1;
            if (way == OP_DECODE) {
                stopDecode();
            } else {
                stopEncode();
            }
        }
        return true;
    });

    public OpusViewModel() {
        try {
            mOpusManager = new OpusManager();
        } catch (OpusException e) {
            JL_Log.w(tag, "init", "Failed to init OpusManager. " + e);
            mOpusManager = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        uiHandler.removeCallbacksAndMessages(null);
        releaseAudioPlayer();
        if (null != mOpusManager) {
            mOpusManager.release();
            mOpusManager = null;
        }
    }

    /**
     * 是否正在解码中
     *
     * @return boolean 结果
     */
    public boolean isDecoding() {
        if (mOpusManager == null) return false;
        if (mOpusManager.isDecodeStream()) return true;
        return getDecodeState().getState() == StateResult.STATE_WORKING;
    }

    /**
     * 开始解码
     *
     * @param context    上下文
     * @param inFilePath 输入文件路径
     * @param param      解码参数
     */
    public void startDecode(@NonNull Context context, @NonNull String inFilePath, @NonNull OpusParam param) {
        if (isDecoding()) {
            JL_Log.i(tag, "startDecode", "It is decoding.");
            return;
        }
        String inFileName = AppUtil.getFileNameByPath(inFilePath);
        if (inFileName == null || (!inFileName.endsWith(".opus") && !inFileName.endsWith(".OPUS"))) {
            callbackDecodeFinish(ErrorCode.ERR_BAD_ARGS, "Bad File. " + inFileName);
            return;
        }
        String outFilePath = AppUtil.getOutputFileDirPath(context) + File.separator + AppUtil.getNameNoSuffix(inFileName);
        if (param.getWay() == OpusParam.WAY_STREAM) {
            outFilePath += "_steam.pcm";
            callbackDecodeWorking(inFilePath, outFilePath, param);
            decodeOpusStream(inFilePath, outFilePath, param);
        } else {
            outFilePath += "_file.pcm";
            callbackDecodeWorking(inFilePath, outFilePath, param);
            decodeOpusFile(inFilePath, outFilePath, param);
        }
    }

    /**
     * 停止解码
     *
     * @return boolean 结果
     */
    public boolean stopDecode() {
        if (null == mOpusManager || !isDecoding()) return false;
        if (null != opusParam && opusParam.getWay() == OpusParam.WAY_STREAM) {
            if (mOpusManager.isDecodeStream()) {
                mOpusManager.stopDecodeStream();
            }
            return true;
        }
        return false;
    }

    /**
     * 是否正在播放
     *
     * @return boolean 结果
     */
    public boolean isPlayAudio() {
        return mAudioTrack != null && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    /**
     * 恢复播放
     */
    public void play() {
        if (mAudioTrack == null || isPlayAudio()) return;
        mAudioTrack.play();
        playStateMLD.postValue(true);
    }

    /**
     * 暂停播放
     */
    public void pause() {
        if (!isPlayAudio()) return;
        mAudioTrack.pause();
        playStateMLD.postValue(false);
    }

    /**
     * 播放音频
     *
     * @param option OPUS选项
     * @param path   文件路径
     */
    public void playAudio(@NonNull OpusConfiguration option, String path) {
        if (TextUtils.isEmpty(path)) {
            play();
            return;
        }
        if (playAudioPrepare(option)) {
            JL_Log.d(tag, "playAudio", "Ready to play audio.");
            readFileData(path);
            return;
        }
        JL_Log.i(tag, "playAudio", "Failed to play audio.");
    }

    /**
     * 停止播放音频
     */
    public void stopAudioPlay() {
        if (mAudioTrack == null) return;
        if (isPlayAudio()) {
            mAudioTrack.stop();
        }
        playStateMLD.postValue(false);
    }

    /**
     * 释放音频播放器
     */
    private void releaseAudioPlayer() {
        if (mAudioTrack == null) return;
        stopAudioPlay();
        mAudioTrack.release();
        mAudioTrack = null;
    }

    public boolean isEncoding() {
        if (mOpusManager == null) return false;
        if (mOpusManager.isEncodeStream()) return true;
        return getEncodeState().getState() == StateResult.STATE_WORKING;
    }

    public void startEncode(@NonNull Context context, @NonNull String inFilePath, @NonNull OpusParam param) {
        if (isEncoding()) {
            JL_Log.i(tag, "startEncode", "It is Encoding.");
            return;
        }
        String inFileName = AppUtil.getFileNameByPath(inFilePath);
        if (inFileName == null || (!inFileName.endsWith(".pcm") && !inFileName.endsWith(".PCM"))) {
            JL_Log.i(tag, "startEncode", "Bad file. " + inFileName);
            return;
        }
        String outFilePath = AppUtil.getOpusFileDirPath(context) + File.separator + AppUtil.getNameNoSuffix(inFileName);
        if (param.getWay() == OpusParam.WAY_STREAM) {
            outFilePath += "_steam.opus";
            callbackEncodeWorking(inFilePath, outFilePath, param);
            encodePcmStream(inFilePath, outFilePath, param);
        } else {
            outFilePath += "_file.opus";
            callbackEncodeWorking(inFilePath, outFilePath, param);
            encodePcmFile(inFilePath, outFilePath, param);
        }
    }

    public boolean stopEncode() {
        if (null == mOpusManager || !isEncoding()) return false;
        if (null != opusParam && opusParam.getWay() == OpusParam.WAY_STREAM) {
            if (mOpusManager.isEncodeStream()) {
                mOpusManager.stopEncodeStream();
            }
            return true;
        }
        return false;
    }

    @NonNull
    private StateResult<OpusResult> getDecodeState() {
        StateResult<OpusResult> result = decodeStateMLD.getValue();
        if (null == result) {
            result = new StateResult<>();
        }
        return result;
    }

    @NonNull
    private StateResult<OpusResult> getEncodeState() {
        StateResult<OpusResult> stateResult = encodeStateMLD.getValue();
        if (null == stateResult) return new StateResult<>();
        return stateResult;
    }

    private void callbackDecodeWorking(@NonNull String inFilePath, @NonNull String outFilePath,
                                       @NonNull OpusParam param) {
        opusParam = param;
        StateResult<OpusResult> stateResult = getDecodeState();
        stateResult.setState(StateResult.STATE_WORKING)
                .setCode(0)
                .setData(new OpusResult(inFilePath, outFilePath, param.getWay()));
        decodeStateMLD.postValue(stateResult);
    }

    private void callbackDecodeFinish(int code, String message) {
        StateResult<OpusResult> result = getDecodeState();
        result.setState(StateResult.STATE_FINISH).setCode(code).setMessage(message);
        decodeStateMLD.postValue(result);
        if (code != 0) {
            OpusResult resultData = result.getData();
            if (null != resultData) {
                AppUtil.deleteFile(new File(resultData.getOutFilePath()));
            }
        }
        opusParam = null;
    }

    private void callbackEncodeWorking(@NonNull String inFilePath, @NonNull String outFilePath,
                                       @NonNull OpusParam param) {
        opusParam = param;
        StateResult<OpusResult> stateResult = getDecodeState();
        stateResult.setState(StateResult.STATE_WORKING)
                .setCode(0)
                .setData(new OpusResult(inFilePath, outFilePath, param.getWay()));
        encodeStateMLD.postValue(stateResult);
    }

    private void callbackEncodeFinish(int code, String message) {
        StateResult<OpusResult> result = getEncodeState();
        result.setState(StateResult.STATE_FINISH).setCode(code).setMessage(message);
        encodeStateMLD.postValue(result);
        if (!result.isSuccess()) {
            OpusResult resultData = result.getData();
            if (null != resultData) {
                AppUtil.deleteFile(new File(resultData.getOutFilePath()));
            }
        }
        opusParam = null;
    }

    private void decodeOpusStream(@NonNull String inFilePath, @NonNull String outFilePath, @NonNull OpusParam param) {
        JL_Log.d(tag,"decodeOpusStream", "mOpusManager ---> " + mOpusManager);
        if (null == mOpusManager) return;
        createFileStream(outFilePath);
        final OnDecodeStreamCallback callback = new OnDecodeStreamCallback() {
            @Override
            public void onDecodeStream(byte[] data) {
                JL_Log.d(tag,"decodeOpusStream", "onDecodeStream ---> " + data.length);
                uiHandler.removeMessages(MSG_WAITING_DATA_TIMEOUT);
                uiHandler.sendMessageDelayed(uiHandler.obtainMessage(MSG_WAITING_DATA_TIMEOUT, OP_DECODE, 0), WAITING_TIMEOUT);
                writeFileData(data);
                if (param.isPlayAudio()) {
                    writeAudioData(data);
                }
            }

            @Override
            public void onStart() {
                JL_Log.i(tag,"decodeOpusStream", "onStart");
                if (param.isPlayAudio()) {
                    playAudioPrepare(param.getOption());
                }
                writeOpusDataHandle(inFilePath, 30);
            }

            @Override
            public void onComplete(String outFilePath) {
                JL_Log.d(tag, "decodeOpusStream", "onComplete : " + outFilePath);
                closeFileStream();
                callbackDecodeFinish(0, "Success");
                if (param.isPlayAudio()) {
                    stopAudioPlay();
                }
            }

            @Override
            public void onError(int code, String message) {
                JL_Log.w(tag, "decodeOpusStream", "onError : " + code + ", " + message);
                closeFileStream();
                callbackDecodeFinish(code, message);
                if (param.isPlayAudio()) {
                    stopAudioPlay();
                }
            }
        };
        if (Constants.IS_SUPPORT_DUAL_CHANNEL) {
            mOpusManager.startDecodeStream(new OpusOption().setHasHead(param.getOption().isHasHead())
                    .setChannel(param.getOption().getChannel())
                    .setSampleRate(param.getOption().getSampleRate())
                    .setPacketSize(param.getOption().getPacketSize()), callback);
        } else {
            mOpusManager.startDecodeStream(callback);
        }
    }

    private void decodeOpusFile(@NonNull String inFilePath, @NonNull String outFilePath, @NonNull OpusParam param) {
        if (null == mOpusManager) return;
        final OnStateCallback callback = new OnStateCallback() {
            @Override
            public void onStart() {
                JL_Log.i(tag, "decodeOpusFile", "onStart");
            }

            @Override
            public void onComplete(String outFilePath) {
                JL_Log.i(tag, "decodeOpusFile", "onComplete ---> outFilePath : " + outFilePath);
                callbackDecodeFinish(0, "Success");
                if (param.isPlayAudio()) {
                    playAudio(param.getOption(), outFilePath);
                }
            }

            @Override
            public void onError(int code, String message) {
                JL_Log.w(tag, "decodeOpusFile", "onError ---> code : " + code + ", " + message);
                callbackDecodeFinish(code, message);
                if (param.isPlayAudio()) {
                    stopAudioPlay();
                }
            }
        };
        if (Constants.IS_SUPPORT_DUAL_CHANNEL) {
            mOpusManager.decodeFile(inFilePath, outFilePath, new OpusOption().setHasHead(param.getOption().isHasHead())
                    .setChannel(param.getOption().getChannel())
                    .setSampleRate(param.getOption().getSampleRate())
                    .setPacketSize(param.getOption().getPacketSize()), callback);
        } else {
            mOpusManager.decodeFile(inFilePath, outFilePath, callback);
        }
    }

    private void encodePcmStream(@NonNull String inFilePath, @NonNull String outFilePath, @NonNull OpusParam param) {
        if (null == mOpusManager) return;
        JL_Log.d(tag, "encodePcmStream", "inFilePath : " + inFilePath + ", \n outFilePath : " + outFilePath);
        createFileStream(outFilePath);
        mOpusManager.startEncodeStream(new OnEncodeStreamCallback() {
            @Override
            public void onEncodeStream(byte[] bytes) {
                uiHandler.removeMessages(MSG_WAITING_DATA_TIMEOUT);
                uiHandler.sendMessageDelayed(uiHandler.obtainMessage(MSG_WAITING_DATA_TIMEOUT, OP_ENCODE, 0), WAITING_TIMEOUT);
                writeFileData(bytes);
            }

            @Override
            public void onStart() {
                JL_Log.d(tag, "encodePcmStream", "onStart");
                writePcmDataHandle(inFilePath, 30);
            }

            @Override
            public void onComplete(String s) {
                closeFileStream();
                callbackEncodeFinish(0, "Success");
            }

            @Override
            public void onError(int code, String message) {
                closeFileStream();
                callbackEncodeFinish(code, message);
            }
        });
    }

    private void encodePcmFile(@NonNull String pcmFilePath, @NonNull String opusFilePath, @NonNull OpusParam param) {
        if (null == mOpusManager) return;
        final OnStateCallback callback = new OnStateCallback() {
            @Override
            public void onStart() {

            }

            @Override
            public void onComplete(String outFilePath) {
                callbackEncodeFinish(0, "Success");
            }

            @Override
            public void onError(int code, String message) {
                callbackEncodeFinish(code, message);
            }
        };
        mOpusManager.encodeFile(pcmFilePath, opusFilePath, callback);
    }

    private boolean playAudioPrepare(@NonNull OpusConfiguration option) {
        releaseAudioPlayer();
        int sampleRate = option.getSampleRate();
        int channelConfig = option.getChannel() == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
                new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(channelConfig)
                        .build(),
                minBufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        mAudioTrack.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {

            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {

            }
        });
        if (mAudioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
            JL_Log.w(tag, "AudioTrack initialization failed.");
            stopAudioPlay();
            return false;
        }
        mAudioTrack.play();
        playStateMLD.postValue(true);
        return true;
    }

    private void writeAudioData(byte[] data) {
        if (mAudioTrack == null) return;
        mAudioTrack.write(data, 0, data.length);
    }

    private void createFileStream(String filePath) {
        closeFileStream();
        try {
            foutStream = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean writeFileData(byte[] data) {
        if (null == foutStream) return false;
        try {
            foutStream.write(data, 0, data.length);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void closeFileStream() {
        if (null == foutStream) return;
        try {
            foutStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            foutStream = null;
        }
    }

    private void writeOpusDataHandle(String filePath, int interval) {
        if (null == mOpusManager) return;
        threadPool.submit(() -> {
            try {
                FileInputStream fin = new FileInputStream(filePath);
                int size;
                byte[] buf = new byte[120];
                while ((size = fin.read(buf)) != -1) {
                    if (size == 0) continue;
                    if (!isDecoding()) break;
                    byte[] data = new byte[size];
                    System.arraycopy(buf, 0, data, 0, size);
                    mOpusManager.writeAudioStream(data);
                    try {
                        Thread.sleep(interval); //模拟小机发送数据间隔
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                fin.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void writePcmDataHandle(String filePath, int interval) {
        threadPool.submit(() -> {
            if (null == mOpusManager) return;
            try {
                FileInputStream fin = new FileInputStream(filePath);
                int size;
                byte[] buf = new byte[120];
                while ((size = fin.read(buf)) != -1) {
                    if (size == 0) continue;
                    if (!isEncoding()) break;
                    byte[] data = new byte[size];
                    System.arraycopy(buf, 0, data, 0, size);
                    mOpusManager.writeEncodeStream(data);
                    try {
                        Thread.sleep(interval); //模拟小机发送数据间隔
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                fin.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void readFileData(String inFilePath) {
        threadPool.submit(() -> {
            try {
                FileInputStream fin = new FileInputStream(inFilePath);
                int readSize;
                byte[] buf = new byte[4 * 1024];
                while ((readSize = fin.read(buf)) != -1) {
                    if (readSize <= 0) continue;
                    byte[] data = new byte[readSize];
                    System.arraycopy(buf, 0, data, 0, data.length);
                    writeAudioData(data);
                }
                fin.close();
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                stopAudioPlay();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
