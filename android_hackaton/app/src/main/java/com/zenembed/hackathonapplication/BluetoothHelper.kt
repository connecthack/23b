package com.zenembed.hackathonapplication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import java.util.ArrayList

@SuppressLint("MissingPermission")
class BluetoothHelper(private val context: Context, private val listener: Listener) {

    val deviceList = ArrayList<BluetoothDevice>()
    var gatt: BluetoothGatt? = null
        private set

    private var bluetoothAdapter: BluetoothAdapter?
    private var bleScanner: BluetoothLeScanner?

    init {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        bluetoothAdapter = bluetoothManager?.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    interface Listener {
        fun onDeviceDiscovered()
        fun onConnectionStateChange(status: Int, newState: Int)
        fun onServicesDiscovered(status: Int)
        fun onCharacteristicChanged(value: ByteArray)
    }


    fun startScan() {
        bleScanner?.startScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (!deviceList.contains(result.device) && result.device.name != null && result.device.name.startsWith("MeshGuard_")) {
                deviceList.add(result.device)
                listener.onDeviceDiscovered()
                log(deviceList.toString())
            }
        }
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            listener.onConnectionStateChange(status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt?.discoverServices()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            listener.onCharacteristicChanged(value)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            listener.onServicesDiscovered(status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, value, status)
            log("onCharacteristicRead ${String(value)}")
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            log("onCharacteristicWrite: ${characteristic?.value}")
        }
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
    }

    private fun log(message: String) {
        Log.e("BluetoothHelper", message)
    }
}
