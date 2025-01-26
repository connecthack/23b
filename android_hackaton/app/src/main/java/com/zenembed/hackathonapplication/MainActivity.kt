package com.zenembed.hackathonapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), BluetoothHelper.Listener {

    private val devicesAdapter = DevicesListAdapter(::onDeviceSelected)
    private val chatAdapter = ChatAdapter()
    private val serviceUUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val writeUUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val notifyUUID = UUID.fromString("2c00026e-ce18-43b1-b24e-392346089fcc")
    private val notifyDescriptorUUId = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val helper by lazy { BluetoothHelper(this, this) }

    private lateinit var etSearch: AppCompatEditText
    private lateinit var rv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            log("$isKeyboardVisible")
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            if (isKeyboardVisible) {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            } else {
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            }
            insets
        }

        etSearch = findViewById<AppCompatEditText>(R.id.etMessage)
        findViewById<FrameLayout>(R.id.btnSend).setOnClickListenerForGroup {
            val message = etSearch.text.toString().trim()
            sendMessage(message, MessageType.User)
            etSearch.text?.clear()
        }

        findViewById<FrameLayout>(R.id.btnGood).setOnClickListenerForGroup { sendMessage("GOOD", MessageType.Macros) }
        findViewById<FrameLayout>(R.id.btnAlarm).setOnClickListenerForGroup { sendMessage("ALARM", MessageType.Macros) }
        findViewById<FrameLayout>(R.id.btnReady).setOnClickListenerForGroup { sendMessage("READY", MessageType.Macros) }

        rv = findViewById<RecyclerView>(R.id.mainRv)
        rv.adapter = devicesAdapter
        if (checkPermissions())
            helper.startScan()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            helper.startScan()
        } else {
            val builder = AlertDialog.Builder(this)
            builder.setMessage("Gde permissions, pidor?")
            builder.setPositiveButton("Seychas dam", object : DialogInterface.OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    checkPermissions()
                }
            })
            builder.show()
        }
    }

    override fun onDeviceDiscovered() {
        devicesAdapter.setData(helper.deviceList)
    }

    override fun onConnectionStateChange(status: Int, newState: Int) {
        log("connection state changed status: $status newState: $newState")
    }

    override fun onServicesDiscovered(status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log("gatt service discovered")
            val gatt = helper.gatt ?: return

            val service = gatt.getService(serviceUUID)

            val characteristic = service?.getCharacteristic(notifyUUID)

            runOnUiThread {
                rv.adapter = chatAdapter
            }

            if (characteristic == null) {
                log("cannot find characteristic with notify uuid $notifyUUID")
                return
            }

            val registered = gatt.setCharacteristicNotification(characteristic, true)
            log("registered to notify characteristic $registered")

            val descriptor = characteristic.getDescriptor(notifyDescriptorUUId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

        } else {
            log("onServicesDiscovered: $status")
        }
    }

    override fun onCharacteristicChanged(value: ByteArray) {
        val message = String(value)
        log("onCharacteristicChanged $message")
        runOnUiThread {
            chatAdapter.addMessage(ChatMessage(MessageType.Remote, message))
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>().apply {

            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        val hasPermissions = permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

        if (!hasPermissions) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        }
        return hasPermissions
    }

    private fun sendMessage(message: String, type: MessageType) {
        chatAdapter.addMessage(ChatMessage(type, message))

        val gatt = helper.gatt ?: return
        val service = gatt.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(writeUUID)

        if (characteristic == null) {
            log("cannot find characteristic with write uuid $writeUUID")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, message.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            characteristic.value = message.toByteArray()
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun onDeviceSelected(device: BluetoothDevice) {
        helper.stopScan()
        helper.connectToDevice(device)
        log("connect to device: ${device.name}")
    }

    private inline fun ViewGroup.setOnClickListenerForGroup(crossinline func: () -> Unit) {
        this.setOnClickListener { func.invoke() }
        for (i in 0 until this.childCount) this.getChildAt(i).setOnClickListener { func.invoke() }
    }


    private fun log(message: String) {
        Log.e("BluetoothActivity", message)
    }
}