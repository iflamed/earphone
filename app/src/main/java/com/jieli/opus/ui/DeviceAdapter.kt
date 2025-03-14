package com.jieli.opus.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.jieli.opus.R

class DeviceAdapter(
    private val devices: List<BluetoothDevice>,
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {
    private val TAG = "DeviceAdapter"

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.tv_device_name)
        val deviceAddress: TextView = view.findViewById(R.id.tv_device_address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        try {
            val device = devices[position]
            
            // 安全获取设备名称
            val deviceName = try {
                if (ActivityCompat.checkSelfPermission(
                        holder.itemView.context,
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
                Log.e(TAG, "获取设备名称出错: ${e.message}")
                "获取名称错误"
            }
            
            holder.deviceName.text = deviceName
            holder.deviceAddress.text = device.address
            Log.d(TAG, "绑定设备: $deviceName (${device.address}) 在位置 $position")
            
            holder.itemView.setOnClickListener { 
                Log.d(TAG, "点击设备: ${device.address}")
                onDeviceClick(device) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "onBindViewHolder错误 position=$position: ${e.message}")
        }
    }

    override fun getItemCount(): Int {
        val count = devices.size
        Log.d(TAG, "getItemCount: $count")
        return count
    }
} 