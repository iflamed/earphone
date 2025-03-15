package com.jieli.opus.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jieli.jl_audio_decode.callback.OnDecodeStreamCallback
import com.jieli.jl_audio_decode.exceptions.OpusException
import com.jieli.jl_audio_decode.opus.OpusManager
import com.jieli.jl_audio_decode.opus.model.OpusOption
import com.jieli.logcat.JL_Log
import com.jieli.opus.R
import com.jieli.opus.data.model.opus.OpusConfiguration
import com.jieli.opus.data.model.opus.OpusParam
import com.jieli.opus.tool.AppUtil
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BleCommunicationActivity : AppCompatActivity() {
    private var tag = "BleCommunicationActivity"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private val devices = ArrayList<BluetoothDevice>()
    private var deviceNamesWithAddress = ArrayList<String>()
    private var selectedDevicePosition = -1
    private var selectedFile: Uri? = null
    private var currentMtu = 23
    private var isFileTransferring = false
    private var isScanning = false
    private var isNotificationsEnabled = false
    private var isAudioStreamingEnabled = false

    // 音频相关
    private var mOpusManager: OpusManager? = null
    private var mAudioTrack: AudioTrack? = null
    private var foutStream: FileOutputStream? = null

    // 用于通知的UUID
    private val CLIENT_CHARACTERISTIC_CONFIG =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val services = mutableListOf<BluetoothGattService>()
    private val characteristics = mutableListOf<BluetoothGattCharacteristic>()
    private var selectedService: BluetoothGattService? = null
    private var selectedCharacteristic: BluetoothGattCharacteristic? = null
    private val threadPool: ExecutorService = Executors.newSingleThreadExecutor()

    private val requestFilePick = 1001
    private val requestEnableBt = 1002

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device

            // 检查设备名称是否为null并且设备是否已经在列表中
            if (!devices.contains(device) && device.name != null) {
                // 使用UI线程更新列表，避免潜在的并发问题
                runOnUiThread {
                    try {
                        val deviceName = try {
                            if (ActivityCompat.checkSelfPermission(
                                    this@BleCommunicationActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                device.name ?: "未知设备"
                            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                device.name ?: "未知设备"
                            } else {
                                "未知设备 (需要权限)"
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "获取设备名称出错: ${e.message}")
                            "获取名称错误"
                        }

                        val position = devices.size
                        devices.add(device)
                        deviceNamesWithAddress.add("$deviceName (${device.address})")
                        updateDeviceSpinner()
                        Log.d(tag, "添加设备到位置: $position, 现在列表大小: ${devices.size}")
                    } catch (e: Exception) {
                        Log.e(tag, "添加设备到列表异常: ${e.message}")
                    }
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.d(tag, "批量扫描结果: ${results.size}")
            // results.forEach { result ->
            //     onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            // }
        }

        override fun onScanFailed(code: Int) {
            isScanning = false
            Log.e(tag, "扫描失败: 错误码 $code")
            runOnUiThread {
                Toast.makeText(
                    this@BleCommunicationActivity,
                    "扫描失败: 错误码 $code",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handler.post {
                        Toast.makeText(
                            this@BleCommunicationActivity,
                            "已连接到设备",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                                this@BleCommunicationActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.d(tag, "onConnectionStateChange: no bluetooth_connect permission")
                            return@post
                        }
                        gatt.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.post {
                        Toast.makeText(
                            this@BleCommunicationActivity,
                            "设备已断开连接",
                            Toast.LENGTH_SHORT
                        ).show()
                        clearServices()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                services.clear()
                services.addAll(gatt.services)
                updateServiceSpinner()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtu = mtu
                handler.post {
                    findViewById<TextView>(R.id.tv_mtu).text = "当前MTU: $mtu"
                    Toast.makeText(
                        this@BleCommunicationActivity,
                        "MTU已更新为: $mtu",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        fun onCharacteristicChange(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handler.post {
                val value = characteristic.value
                val result = String(value)
                findViewById<TextView>(R.id.tv_command_result).text = "指令结果: $result"
            }
        }

        // API 33 及以上的新回调方法
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value

            if (isAudioStreamingEnabled && characteristic == selectedCharacteristic) {
                // 处理音频数据
                processAudioData(value)
            } else {
                // 处理普通命令响应
                handler.post {
                    val result = String(value)
                    findViewById<TextView>(R.id.tv_command_result).text = "指令结果: $result"
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                val result = String(value)
                handler.post {
                    findViewById<TextView>(R.id.tv_command_result).text = "读取结果: $result"
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Toast.makeText(
                        this@BleCommunicationActivity,
                        "写入成功",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@BleCommunicationActivity,
                        "写入失败: $status",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val isEnabled =
                        descriptor.value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                                descriptor.value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)

                    isNotificationsEnabled = isEnabled

                    handler.post {
                        Toast.makeText(
                            this@BleCommunicationActivity,
                            if (isEnabled) "通知已启用" else "通知已禁用",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_communication)

        setupBluetooth()
        setupDeviceSpinner()
        setupSpinners()
        setupButtons()
        setupOpusManager()
        checkPermissions()
    }

    /**
     * 初始化OpusManager
     */
    private fun setupOpusManager() {
        try {
            mOpusManager = OpusManager()
        } catch (e: OpusException) {
            JL_Log.w(tag, "初始化OpusManager失败: $e")
            mOpusManager = null
        }
    }

    /**
     * 处理接收到的音频数据
     */
    private fun processAudioData(data: ByteArray) {
        // 写入文件
        writeFileData(data)
        // 发送到OpusManager解码
        mOpusManager?.writeAudioStream(data)
        // Log.d(tag, "接收音频数据: ${data.size} 字节")
    }

    /**
     * 开始音频流解码
     */
    private fun startAudioStreamDecode(outfile: Boolean) {
        val dt = "${System.currentTimeMillis()}"
        if (outfile) {
            val outFilePath =
                AppUtil.getOpusFileDirPath(this) + File.separator + "ble_audio_$dt.opus"
            createFileStream(outFilePath)
        }

        // 配置Opus解码参数
        val opusCfg = OpusConfiguration().setHasHead(false).setPacketSize(40)
            .setSampleRate(OpusConfiguration.SAMPLE_RATE_16k)
            .setChannel(OpusConfiguration.CHANNEL_MONO)
        val param = OpusParam(opusCfg)
            .setWay(OpusParam.WAY_STREAM).setPlayAudio(false)

        val callback = object : OnDecodeStreamCallback {
            override fun onDecodeStream(data: ByteArray) {
                JL_Log.d(tag, "decodeOpusStream", "onDecodeStream ---> ${data.size}")
                writeAudioData(data)
            }

            override fun onStart() {
                JL_Log.i(tag, "decodeOpusStream", "onStart")
                playAudioPrepare(param.option)
            }

            override fun onComplete(outFilePath: String) {
                JL_Log.d(tag, "decodeOpusStream", "onComplete : $outFilePath")
                stopAudioPlay()
            }

            override fun onError(code: Int, message: String) {
                JL_Log.w(tag, "decodeOpusStream", "onError : $code, $message")
                stopAudioPlay()
            }
        }

        mOpusManager?.startDecodeStream(
            OpusOption().setHasHead(param.option.isHasHead)
                .setChannel(param.option.channel)
                .setSampleRate(param.option.sampleRate)
                .setPacketSize(param.option.packetSize), callback
        )
    }

    /**
     * 准备音频播放器
     */
    private fun playAudioPrepare(@NonNull option: OpusConfiguration) {
        releaseAudioPlayer()
        val sampleRate = option.sampleRate
        val channelConfig =
            if (option.channel == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBufferSize =
            AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)

        mAudioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channelConfig)
                .build(),
            minBufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        if (mAudioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
            JL_Log.w(tag, "AudioTrack初始化失败")
            stopAudioPlay()
            return
        }
        Log.d(tag, "playAudioPrepare: start play audio")
        mAudioTrack?.play()
    }

    /**
     * 写入音频数据到播放器
     */
    private fun writeAudioData(data: ByteArray) {
        mAudioTrack?.write(data, 0, data.size)
    }

    private fun playAudioFile() {
        val inFilePath = AppUtil.getOpusFileDirPath(this) + File.separator + "test.opus"
        startAudioStreamDecode(false)
        writeOpusDataHandle(inFilePath, 50)
    }

    private fun writeOpusDataHandle(filePath: String, interval: Int) {
        if (null == mOpusManager) return
        threadPool.submit(Runnable {
            try {
                val fin = FileInputStream(filePath)
                var size: Int
                val buf = ByteArray(120)
                Log.d(tag, "writeOpusDataHandle: path $filePath")
                while ((fin.read(buf).also { size = it }) != -1) {
                    if (size == 0) continue
                    val data = ByteArray(size)
                    System.arraycopy(buf, 0, data, 0, size)
                    mOpusManager!!.writeAudioStream(data)
                    try {
                        Thread.sleep(interval.toLong()) //模拟小机发送数据间隔
                    } catch (e: InterruptedException) {
                        Log.e(tag, "writeOpusDataHandle: ", e)
                        break
                    }
                }
                fin.close()
            } catch (e: IOException) {
                Log.e(tag, "writeOpusDataHandle: ", e)
            }
        })
    }

    /**
     * 是否正在播放音频
     */
    private fun isPlayAudio(): Boolean {
        return mAudioTrack != null && mAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    /**
     * 停止音频播放
     */
    private fun stopAudioPlay() {
        if (mAudioTrack == null) return
        if (isPlayAudio()) {
            mAudioTrack?.stop()
        }
        Log.d(tag, "stopAudioPlay: 已停止")
    }

    /**
     * 释放音频播放器
     */
    private fun releaseAudioPlayer() {
        if (mAudioTrack == null) return
        stopAudioPlay()
        mAudioTrack?.release()
        mAudioTrack = null
    }

    /**
     * 创建文件流
     */
    private fun createFileStream(filePath: String) {
        closeFileStream()
        try {
            foutStream = FileOutputStream(filePath)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }

    /**
     * 写入文件数据
     */
    private fun writeFileData(data: ByteArray): Boolean {
        if (foutStream == null) return false
        try {
            foutStream?.write(data, 0, data.size)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 关闭文件流
     */
    private fun closeFileStream() {
        if (foutStream == null) return
        try {
            Log.d(tag, "closeFileStream: 关闭文件流")
            foutStream?.close()
        } catch (e: IOException) {
            Log.e(tag, "closeFileStream: ", e)
        } finally {
            foutStream = null
        }
    }

    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun setupDeviceSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinner_devices)
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNamesWithAddress)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position >= 0 && position < devices.size) {
                    selectedDevicePosition = position
                    Log.d(tag, "选择设备: ${devices[position].address}")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedDevicePosition = -1
            }
        }
    }

    private fun updateDeviceSpinner() {
        try {
            val spinner = findViewById<Spinner>(R.id.spinner_devices)
            val adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNamesWithAddress)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        } catch (e: Exception) {
            Log.e(tag, "更新设备下拉菜单失败: ${e.message}")
        }
    }

    private fun setupSpinners() {
        val serviceSpinner = findViewById<Spinner>(R.id.spinner_services)
        val characteristicSpinner = findViewById<Spinner>(R.id.spinner_characteristics)

        serviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedService = services.getOrNull(position)
                updateCharacteristicSpinner()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        characteristicSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedCharacteristic = characteristics.getOrNull(position)

                // 更新UI上的特征能力信息
                val propertiesText =
                    selectedCharacteristic?.let { getCharacteristicPropertiesText(it) } ?: ""
                findViewById<TextView>(R.id.tv_characteristic_properties).text = propertiesText

                // 添加额外按钮
                updateOperationButtons()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_scan).setOnClickListener {
            if (checkPermissions()) {
                Log.d(tag, "开始扫描BLE设备")
                scanDevices()
            }
        }
        findViewById<Button>(R.id.btn_play).setOnClickListener {
            playAudioFile()
        }

        // 添加连接按钮点击事件
        findViewById<Button>(R.id.btn_connect).setOnClickListener {
            if (selectedDevicePosition >= 0 && selectedDevicePosition < devices.size) {
                connectToDevice(devices[selectedDevicePosition])
            } else {
                Toast.makeText(this, "请先选择设备", Toast.LENGTH_SHORT).show()
            }
        }

        // 添加断开连接按钮点击事件
        findViewById<Button>(R.id.btn_disconnect).setOnClickListener {
            disconnectFromDevice()
        }

        findViewById<Button>(R.id.btn_request_mtu).setOnClickListener {
            requestMtu()
        }

        findViewById<Button>(R.id.btn_send_command).setOnClickListener {
            sendCommand()
        }

        findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            openFilePicker()
        }

        findViewById<Button>(R.id.btn_send_file).setOnClickListener {
            sendFile()
        }

        // 添加读取按钮
        findViewById<Button>(R.id.btn_read).setOnClickListener {
            readCharacteristic()
        }

        // 添加开启/关闭通知按钮
        findViewById<Button>(R.id.btn_toggle_notification).setOnClickListener {
            toggleNotification()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        try {
            startActivityForResult(Intent.createChooser(intent, "选择文件"), requestFilePick)
        } catch (ex: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
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

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, requestEnableBt)
            return false
        }

        return true
    }

    private fun scanDevices() {
        // 如果正在扫描，先停止扫描
        if (isScanning) {
            stopScan()
            // 添加一点延迟再重新开始扫描
            handler.postDelayed({ startScan() }, 200)
            return
        }

        startScan()
    }

    private fun startScan() {
        // 安全地清空设备列表
        runOnUiThread {
            try {
                devices.clear()
                deviceNamesWithAddress.clear()
                updateDeviceSpinner()
                Log.d(tag, "已清空设备列表")
            } catch (e: Exception) {
                Log.e(tag, "清空设备列表异常: ${e.message}")
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 处理旧版权限
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    performScan()
                } else {
                    Toast.makeText(this, "缺少位置权限，无法扫描设备", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "缺少蓝牙扫描权限", Toast.LENGTH_SHORT).show()
            }
            return
        }

        performScan()
    }

    private fun stopScan() {
        if (!isScanning) {
            Log.d(tag, "stopScan: not scanning")
            return
        }

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothLeScanner.stopScan(scanCallback)
                isScanning = false
                Log.d(tag, "停止BLE扫描")
            } else {
                bluetoothLeScanner.stopScan(scanCallback)
                isScanning = false
                Log.d(tag, "stopScan: no permission to stop")
            }
        } catch (e: Exception) {
            Log.e(tag, "停止扫描出错: ${e.message}")
        }
    }

    private fun performScan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.d(tag, "开始BLE扫描")
            isScanning = true
            // 10秒后自动停止扫描
            handler.postDelayed({
                stopScan()
                runOnUiThread {
                    Toast.makeText(this, "扫描完成，发现${devices.size}个设备", Toast.LENGTH_SHORT)
                        .show()
                }
            }, 20000)
            bluetoothLeScanner.startScan(scanCallback)
        } catch (e: Exception) {
            Log.e(tag, "启动扫描失败: ${e.message}")
            Toast.makeText(this, "启动扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 如果正在扫描，先停止扫描
            stopScan()

            // 连接前先断开可能存在的连接
            bluetoothGatt?.close()

            Log.d(tag, "连接设备: ${device.address}")
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
            Toast.makeText(this, "正在连接设备...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(tag, "连接设备失败: ${e.message}")
            Toast.makeText(this, "连接设备失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestMtu() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (bluetoothGatt == null) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.d(tag, "请求MTU: 512")
            bluetoothGatt?.requestMtu(512)
        } catch (e: Exception) {
            Log.e(tag, "请求MTU失败: ${e.message}")
            Toast.makeText(this, "请求MTU失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendCommand() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        val command = findViewById<EditText>(R.id.et_command).text.toString()
        if (command.isEmpty()) {
            Toast.makeText(this, "请输入指令", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCharacteristic == null) {
            Toast.makeText(this, "请先选择特征", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.d(tag, "发送指令: $command")
            selectedCharacteristic?.value = command.toByteArray()
            bluetoothGatt?.writeCharacteristic(selectedCharacteristic)
        } catch (e: Exception) {
            Log.e(tag, "发送指令失败: ${e.message}")
            Toast.makeText(this, "发送指令失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readCharacteristic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCharacteristic == null) {
            Toast.makeText(this, "请先选择特征", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
            Toast.makeText(this, "该特征不支持读取", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            Log.d(tag, "读取特征值: ${selectedCharacteristic!!.uuid}")
            bluetoothGatt?.readCharacteristic(selectedCharacteristic)
        } catch (e: Exception) {
            Log.e(tag, "读取特征值失败: ${e.message}")
            Toast.makeText(this, "读取特征值失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCharacteristic == null) {
            Toast.makeText(this, "请先选择特征", Toast.LENGTH_SHORT).show()
            return
        }

        val hasNotify =
            selectedCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val hasIndicate =
            selectedCharacteristic!!.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

        if (!hasNotify && !hasIndicate) {
            Toast.makeText(this, "该特征不支持通知或指示", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 首先设置通知状态
            bluetoothGatt?.setCharacteristicNotification(
                selectedCharacteristic,
                !isNotificationsEnabled
            )

            // 然后写入描述符
            val descriptor = selectedCharacteristic!!.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor != null) {
                descriptor.value = if (!isNotificationsEnabled) {
                    if (hasNotify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }

                bluetoothGatt?.writeDescriptor(descriptor)
                Log.d(tag, "设置特征通知状态: ${!isNotificationsEnabled}")

                // 更新通知状态
                val newNotificationState = !isNotificationsEnabled
                isNotificationsEnabled = newNotificationState

                // 如果开启通知，并且是音频流模式，则启动音频解码
                if (newNotificationState) {
                    // 询问用户是否为音频流
                    if (askUserIfAudioStream()) {
                        isAudioStreamingEnabled = true
                        startAudioStreamDecode(true)
                        Toast.makeText(this, "已开启音频流接收模式", Toast.LENGTH_SHORT).show()
                        findViewById<Button>(R.id.btn_toggle_notification).text = "关闭音频流"
                    } else {
                        isAudioStreamingEnabled = false
                        findViewById<Button>(R.id.btn_toggle_notification).text = "关闭通知"
                    }
                } else {
                    // 关闭音频流
                    if (isAudioStreamingEnabled) {
                        isAudioStreamingEnabled = false
                        stopAudioPlay()
                        closeFileStream()
                        Toast.makeText(this, "已关闭音频流接收", Toast.LENGTH_SHORT).show()
                    }
                    findViewById<Button>(R.id.btn_toggle_notification).text = "开启通知"
                }
            } else {
                Toast.makeText(this, "无法找到通知描述符", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "设置通知状态失败: ${e.message}")
            Toast.makeText(this, "设置通知状态失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 询问用户是否为音频流
     */
    private fun askUserIfAudioStream(): Boolean {
        // 这里简化处理，实际应用中可以使用对话框询问用户
        return true
    }


    private fun sendFile() {
        if (selectedFile == null) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show()
            return
        }

        if (isFileTransferring) {
            Toast.makeText(this, "文件正在传输中", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCharacteristic == null) {
            Toast.makeText(this, "请先选择特征", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                isFileTransferring = true
                val inputStream = contentResolver.openInputStream(selectedFile!!)
                val fileSize =
                    contentResolver.openFileDescriptor(selectedFile!!, "r")?.statSize ?: 0
                var bytesSent = 0L
                val buffer = ByteArray(currentMtu - 3) // 预留3字节用于ATT头部

                handler.post {
                    findViewById<ProgressBar>(R.id.progress_bar).apply {
                        visibility = View.VISIBLE
                        max = 100
                    }
                }

                while (true) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead == -1) break

                    selectedCharacteristic?.value = buffer.copyOf(bytesRead)
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.d(tag, "sendFile: no BLUETOOTH_CONNECT permission")
                        break
                    }
                    bluetoothGatt?.writeCharacteristic(selectedCharacteristic)

                    bytesSent += bytesRead
                    val progress = (bytesSent * 100 / fileSize).toInt()
                    handler.post {
                        findViewById<ProgressBar>(R.id.progress_bar).progress = progress
                        findViewById<TextView>(R.id.tv_file_progress).text = "文件进度: $progress%"
                    }

                    Thread.sleep(50) // 添加延迟以避免发送过快
                }

                inputStream?.close()
                handler.post {
                    Toast.makeText(this, "文件发送完成", Toast.LENGTH_SHORT).show()
                    findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
                }
            } catch (e: IOException) {
                Log.e(tag, "文件发送失败: ${e.message}")
                handler.post {
                    Toast.makeText(this, "文件发送失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    findViewById<ProgressBar>(R.id.progress_bar).visibility = View.GONE
                }
            } finally {
                isFileTransferring = false
            }
        }.start()
    }

    private fun updateServiceSpinner() {
        try {
            val serviceNames = services.map { it.uuid.toString() }
            Log.d(tag, "更新服务下拉菜单，找到${serviceNames.size}个服务")

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serviceNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            runOnUiThread {
                findViewById<Spinner>(R.id.spinner_services).adapter = adapter
            }
        } catch (e: Exception) {
            Log.e(tag, "更新服务下拉菜单失败: ${e.message}")
        }
    }

    private fun updateCharacteristicSpinner() {
        try {
            characteristics.clear()
            selectedService?.characteristics?.let { characteristics.addAll(it) }

            // 创建包含特征UUID和属性信息的显示字符串
            val characteristicDescriptions = characteristics.map { characteristic ->
                val propertiesText = getCharacteristicPropertiesText(characteristic)
                "${characteristic.uuid} [$propertiesText]"
            }

            Log.d(tag, "更新特征下拉菜单，找到${characteristicDescriptions.size}个特征")

            val adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_item, characteristicDescriptions)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

            runOnUiThread {
                findViewById<Spinner>(R.id.spinner_characteristics).adapter = adapter
            }
        } catch (e: Exception) {
            Log.e(tag, "更新特征下拉菜单失败: ${e.message}")
        }
    }

    // 获取特征的能力描述
    private fun getCharacteristicPropertiesText(characteristic: BluetoothGattCharacteristic): String {
        val properties = characteristic.properties
        val propertyFlags = mutableListOf<String>()

        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            propertyFlags.add("读")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            propertyFlags.add("写")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            propertyFlags.add("无响应写")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            propertyFlags.add("通知")
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            propertyFlags.add("指示")
        }

        return propertyFlags.joinToString("/")
    }

    // 根据选中特征的能力更新操作按钮
    private fun updateOperationButtons() {
        val readButton = findViewById<Button>(R.id.btn_read)
        val notifyButton = findViewById<Button>(R.id.btn_toggle_notification)
        val writeButton = findViewById<Button>(R.id.btn_send_command)

        selectedCharacteristic?.let { characteristic ->
            val properties = characteristic.properties

            // 更新读按钮可用性
            val canRead = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
            readButton.isEnabled = canRead
            readButton.alpha = if (canRead) 1.0f else 0.5f

            // 更新通知按钮可用性
            val canNotify = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                    properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            notifyButton.isEnabled = canNotify
            notifyButton.alpha = if (canNotify) 1.0f else 0.5f
            notifyButton.text = when {
                isNotificationsEnabled && isAudioStreamingEnabled -> "关闭音频流"
                isNotificationsEnabled -> "关闭通知"
                else -> "开启通知"
            }

            // 更新写按钮可用性
            val canWrite = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            writeButton.isEnabled = canWrite
            writeButton.alpha = if (canWrite) 1.0f else 0.5f
        } ?: run {
            // 如果没有选中特征，禁用所有按钮
            readButton.isEnabled = false
            readButton.alpha = 0.5f
            notifyButton.isEnabled = false
            notifyButton.alpha = 0.5f
            writeButton.isEnabled = false
            writeButton.alpha = 0.5f
        }
    }

    private fun clearServices() {
        services.clear()
        characteristics.clear()
        selectedService = null
        selectedCharacteristic = null
        updateServiceSpinner()
        updateCharacteristicSpinner()
        // 清除特征能力显示
        findViewById<TextView>(R.id.tv_characteristic_properties).text = ""
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            requestFilePick -> {
                if (resultCode == RESULT_OK && data != null) {
                    selectedFile = data.data
                    Toast.makeText(this, "已选择文件", Toast.LENGTH_SHORT).show()
                }
            }

            requestEnableBt -> {
                if (resultCode == RESULT_OK) {
                    // 蓝牙已启用
                    scanDevices()
                } else {
                    Toast.makeText(this, "蓝牙未开启，无法使用BLE功能", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 停止扫描以节省电量
        stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止扫描
        stopScan()

        // 关闭音频相关资源
        stopAudioPlay()
        releaseAudioPlayer()
        closeFileStream()

        // 关闭GATT连接
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
                bluetoothGatt = null
            } catch (e: Exception) {
                Log.e(tag, "关闭GATT连接异常: ${e.message}")
            }
        }

        // 移除所有延迟执行的任务
        handler.removeCallbacksAndMessages(null)
    }

    private fun disconnectFromDevice() {
        if (bluetoothGatt == null) {
            Toast.makeText(this, "没有连接的设备", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 停止任何正在进行的通知
            if (isNotificationsEnabled) {
                // 如果是音频流模式，先关闭音频相关资源
                if (isAudioStreamingEnabled) {
                    isAudioStreamingEnabled = false
                    stopAudioPlay()
                    closeFileStream()
                }
                isNotificationsEnabled = false
            }

            // 断开GATT连接
            Log.d(tag, "断开设备连接")
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null

            // 清除服务和特征列表
            clearServices()

            Toast.makeText(this, "已断开设备连接", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(tag, "断开设备连接失败: ${e.message}")
            Toast.makeText(this, "断开设备连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}