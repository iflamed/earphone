package com.jieli.opus.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jieli.opus.R
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*

class BluetoothSppActivity : AppCompatActivity() {
    private var tag = "BluetoothSppActivity"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val handler = Handler(Looper.getMainLooper())
    private val devices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceAdapter

    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_spp)

        // 使用新的API获取BluetoothAdapter
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setupRecyclerView()
        setupButtons()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.rv_devices)
        deviceAdapter = DeviceAdapter(devices) { device ->
            connectToDevice(device)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_scan).setOnClickListener {
            if (checkPermissions()) {
                scanDevices()
            }
        }

        findViewById<Button>(R.id.btn_send_file).setOnClickListener {
            sendTestFile()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上版本需要的权限
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 12以下版本需要的权限
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
            return false
        }
        return true
    }

    private fun scanDevices() {
        val size = devices.size
        devices.clear()
        deviceAdapter.notifyItemRangeRemoved(0, size)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                performScan()
            }
        } else {
            performScan()
        }
    }

    private fun performScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1
            )
            Log.d(tag, "performScan: not granted")
            return
        }

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        bluetoothAdapter.startDiscovery()
        Log.d(tag, "performScan: start discovery")
        Log.d(tag, "performScan: length " + bluetoothAdapter.bondedDevices.size)
        bluetoothAdapter.bondedDevices.forEach { device ->
            Log.d(tag, "performScan: device " + device.name)
            devices.add(device)
            deviceAdapter.notifyItemInserted(0)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(tag, "connectToDevice: not granted")
            return
        }

        Thread {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(sppUUID)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                handler.post {
                    Toast.makeText(this, "已连接到设备: ${device.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Log.e(tag, "connectToDevice: ", e)
                handler.post {
                    Toast.makeText(this, "连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun sendTestFile() {
        if (outputStream == null) {
            Toast.makeText(this, "请先连接蓝牙设备", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val file = File(getExternalFilesDir(null), "test.opus")
                if (!file.exists()) {
                    handler.post {
                        Toast.makeText(this, "测试文件不存在", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                val inputStream = FileInputStream(file)
                val buffer = ByteArray(1024)
                var bytes: Int

                while (inputStream.read(buffer).also { bytes = it } != -1) {
                    outputStream?.write(buffer, 0, bytes)
                }

                inputStream.close()
                handler.post {
                    Toast.makeText(this, "文件发送完成", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                handler.post {
                    Toast.makeText(this, "发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}