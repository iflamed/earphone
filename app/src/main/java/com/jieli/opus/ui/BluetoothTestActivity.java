package com.jieli.opus.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.jieli.jl_audio_decode.callback.OnDecodeStreamCallback;
import com.jieli.jl_audio_decode.exceptions.OpusException;
import com.jieli.jl_audio_decode.opus.OpusManager;
import com.jieli.jl_audio_decode.opus.model.OpusOption;
import com.jieli.logcat.JL_Log;
import com.jieli.opus.R;
import com.jieli.opus.data.constant.Constants;
import com.jieli.opus.data.model.opus.OpusConfiguration;
import com.jieli.opus.data.model.opus.OpusParam;
import com.jieli.opus.tool.AppUtil;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothTestActivity extends AppCompatActivity implements BluetoothSppManager.BluetoothCallback {
    private static final String TAG = "BluetoothTestActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private BluetoothSppManager bluetoothManager;
    private TextView statusTextView;
    private TextView dataReceivedTextView;
    private Button connectButton;
    private Button sendTestFileButton;
    private ListView deviceListView;
    private List<BluetoothDevice> pairedDevicesList;
    private OpusManager mOpusManager;
    /**
     * 音频播放器
     */
    private AudioTrack mAudioTrack;
    private FileOutputStream foutStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_test);

        statusTextView = findViewById(R.id.statusTextView);
        dataReceivedTextView = findViewById(R.id.dataReceivedTextView);
        connectButton = findViewById(R.id.connectButton);
        sendTestFileButton = findViewById(R.id.sendTestFileButton);
        deviceListView = findViewById(R.id.deviceListView);

        bluetoothManager = new BluetoothSppManager();
        bluetoothManager.setCallback(this);

        connectButton.setOnClickListener(v -> checkBluetoothAndPermissions());
        sendTestFileButton.setOnClickListener(v -> sendTestFile());

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = pairedDevicesList.get(position);
            connectToDevice(device);
        });
        try {
            mOpusManager = new OpusManager();
        } catch (OpusException e) {
            JL_Log.w(TAG, "init", "Failed to init OpusManager. " + e);
            mOpusManager = null;
        }
        setupOpusManager();
        updateUI();
    }

    private void checkBluetoothAndPermissions() {
        if (!bluetoothManager.isBluetoothSupported()) {
            Toast.makeText(this, "此设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothManager.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                return;
            }
        }

        listPairedDevices();
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    REQUEST_PERMISSIONS);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_PERMISSIONS);
        }
    }

    @SuppressLint("MissingPermission")
    private void listPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevices();

        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(this, "没有已配对的设备", Toast.LENGTH_SHORT).show();
            return;
        }

        pairedDevicesList = new ArrayList<>(pairedDevices);
        List<String> deviceNames = new ArrayList<>();

        for (BluetoothDevice device : pairedDevicesList) {
            deviceNames.add(device.getName() + "\n" + device.getAddress());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, deviceNames);
        deviceListView.setAdapter(adapter);
        deviceListView.setVisibility(View.VISIBLE);
    }

    private void connectToDevice(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        statusTextView.setText("正在连接到: " + device.getName());
        bluetoothManager.connect(device);
    }

    private void sendTestFile() {
        if (!bluetoothManager.isConnected()) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // 从资源中读取测试文件
                InputStream is = getAssets().open("opus/test.opus");
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();

                // 发送文件
                boolean success = bluetoothManager.sendData(buffer);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "测试文件发送成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "测试文件发送失败", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "发送测试文件失败: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this,
                        "发送测试文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void updateUI() {
        if (bluetoothManager.isConnected()) {
            connectButton.setText("断开连接");
            connectButton.setOnClickListener(v -> bluetoothManager.disconnect());
            deviceListView.setVisibility(View.GONE);
            sendTestFileButton.setEnabled(true);
        } else {
            connectButton.setText("连接设备");
            connectButton.setOnClickListener(v -> checkBluetoothAndPermissions());
            sendTestFileButton.setEnabled(false);
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected, String deviceName) {
        if (connected) {
            statusTextView.setText("已连接到: " + deviceName);
        } else {
            statusTextView.setText("已断开连接");
            dataReceivedTextView.setText("");
        }
        updateUI();
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
     * 停止播放音频
     */
    public void stopAudioPlay() {
        if (mAudioTrack == null) return;
        if (isPlayAudio()) {
            mAudioTrack.stop();
        }
        Log.d(TAG, "stopAudioPlay: stopped");
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

    private void playAudioPrepare(@NonNull OpusConfiguration option) {
        releaseAudioPlayer();
        int sampleRate = option.getSampleRate();
        int channelConfig = option.getChannel() == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
            JL_Log.w(TAG, "AudioTrack initialization failed.");
            stopAudioPlay();
            return;
        }
        mAudioTrack.play();
    }

    private void writeAudioData(byte[] data) {
        if (mAudioTrack == null) return;
        mAudioTrack.write(data, 0, data.length);
    }

    private void setupOpusManager() {
        String dt = "" + System.currentTimeMillis();
        String outFilePath = AppUtil.getOpusFileDirPath(this) + File.separator + "test_"+dt+".opus";
        createFileStream(outFilePath);
        OpusConfiguration opsCfg = new OpusConfiguration().setHasHead(false).setPacketSize(40)
                .setSampleRate(OpusConfiguration.SAMPLE_RATE_16k).setChannel(OpusConfiguration.CHANNEL_MONO);
        OpusParam param = new OpusParam(opsCfg)
                .setWay(OpusParam.WAY_STREAM).setPlayAudio(false);

        final OnDecodeStreamCallback callback = new OnDecodeStreamCallback() {
            @Override
            public void onDecodeStream(byte[] data) {
                JL_Log.d(TAG,"decodeOpusStream", "onDecodeStream ---> " + data.length);
                writeAudioData(data);
            }

            @Override
            public void onStart() {
                JL_Log.i(TAG,"decodeOpusStream", "onStart");
                playAudioPrepare(param.getOption());
            }

            @Override
            public void onComplete(String outFilePath) {
                JL_Log.d(TAG, "decodeOpusStream", "onComplete : " + outFilePath);
                stopAudioPlay();
            }

            @Override
            public void onError(int code, String message) {
                JL_Log.w(TAG, "decodeOpusStream", "onError : " + code + ", " + message);
                stopAudioPlay();
            }
        };
        mOpusManager.startDecodeStream(new OpusOption().setHasHead(param.getOption().isHasHead())
                .setChannel(param.getOption().getChannel())
                .setSampleRate(param.getOption().getSampleRate())
                .setPacketSize(param.getOption().getPacketSize()), callback);
    }

    @Override
    public void onDataReceived(byte[] data) {
        // 发送音频程序
        writeFileData(data);
        mOpusManager.writeAudioStream(data);
        Log.d(TAG, "onDataReceived: " + encodeHexString(data) + "\n");

        // String receivedText = new String(data);
        // String currentText = dataReceivedTextView.getText().toString();
        //
        // // 限制显示的数据量以避免UI卡顿
        // if (currentText.length() > 1000) {
        //     currentText = currentText.substring(currentText.length() - 500);
        // }
        //
        // dataReceivedTextView.setText(currentText + receivedText);
    }

    public String encodeHexString(byte[] byteArray) {
        StringBuffer hexStringBuffer = new StringBuffer();
        for (int i = 0; i < byteArray.length; i++) {
            hexStringBuffer.append(byteToHex(byteArray[i]));
        }
        return hexStringBuffer.toString();
    }

    public String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
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
            Log.d(TAG, "closeFileStream: close file stream");
            foutStream.close();
        } catch (IOException e) {
            Log.e(TAG, "closeFileStream: ", e);
        } finally {
            foutStream = null;
        }
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, "错误: " + message, Toast.LENGTH_SHORT).show();
        statusTextView.setText("错误: " + message);
        updateUI();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                listPairedDevices();
            } else {
                Toast.makeText(this, "需要蓝牙权限才能使用此功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                listPairedDevices();
            } else {
                Toast.makeText(this, "需要启用蓝牙才能使用此功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeFileStream();
        bluetoothManager.disconnect();
    }
}