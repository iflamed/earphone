package com.jieli.opus.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class BluetoothSppManager {
    private static final String TAG = "BluetoothSppManager";
    // SPP UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice connectedDevice;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isConnected = false;
    private ReadThread readThread;
    private final Handler mainHandler;

    // 回调接口
    public interface BluetoothCallback {
        void onConnectionStateChanged(boolean connected, String deviceName);
        void onDataReceived(byte[] data);
        void onError(String message);
    }

    private BluetoothCallback callback;

    public BluetoothSppManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setCallback(BluetoothCallback callback) {
        this.callback = callback;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public Set<BluetoothDevice> getPairedDevices() {
        if (bluetoothAdapter == null) return null;
        return bluetoothAdapter.getBondedDevices();
    }

    public boolean isConnected() {
        return isConnected;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        if (device == null) {
            notifyError("设备为空");
            return;
        }

        // 断开现有连接
        disconnect();

        new Thread(() -> {
            try {
                connectedDevice = device;
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();

                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                isConnected = true;

                // 通知连接状态
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onConnectionStateChanged(true, device.getName());
                    }
                });

                // 启动读取线程
                readThread = new ReadThread();
                readThread.start();

            } catch (IOException e) {
                disconnect();
                notifyError("连接失败: " + e.getMessage());
            }
        }).start();
    }

    public void disconnect() {
        isConnected = false;

        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }

        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }

            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }

            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "断开连接错误: " + e.getMessage());
        }

        // 通知断开连接
        if (connectedDevice != null) {
            @SuppressLint("MissingPermission") final String deviceName = connectedDevice.getName();
            connectedDevice = null;

            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onConnectionStateChanged(false, deviceName);
                }
            });
        }
    }

    public boolean sendData(byte[] data) {
        if (!isConnected || outputStream == null) {
            notifyError("未连接到设备");
            return false;
        }

        try {
            // 修改测试发送的内容
            // String inputString = "Hello World!\n";
            // data = inputString.getBytes(StandardCharsets.UTF_8);
            outputStream.write(data);
            outputStream.flush();
            return true;
        } catch (IOException e) {
            disconnect();
            notifyError("发送数据失败: " + e.getMessage());
            return false;
        }
    }

    private void notifyError(String message) {
        Log.e(TAG, message);
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onError(message);
            }
        });
    }

    // 读取数据线程
    private class ReadThread extends Thread {
        @Override
        public void run() {
            // 默认每次读取1024字节，适配蓝牙耳机SPP数据流改成200
            byte[] buffer = new byte[200];
            int bytes;

            while (isConnected) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        final byte[] data = new byte[bytes];
                        System.arraycopy(buffer, 0, data, 0, bytes);
                        // callback.onDataReceived(data);
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onDataReceived(data);
                            }
                        });
                    }
                } catch (IOException e) {
                    if (isConnected) {
                        disconnect();
                        notifyError("读取数据失败: " + e.getMessage());
                    }
                    Log.e(TAG, "ReadThread run: ", e);
                    break;
                }
            }
        }
    }
}