package com.zenembed.hackathonapplication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView

@SuppressLint("MissingPermission")
class DevicesListAdapter(private val onDeviceSelected: (device: BluetoothDevice) -> Unit) : RecyclerView.Adapter<DevicesListAdapter.ViewHolder>() {

    private var items = listOf<BluetoothDevice>()

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scan_device, parent, false)
        )
    }

    fun setData(items: List<BluetoothDevice>) {
        this.items = items
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(device: BluetoothDevice) {
            val tvName = view.findViewById<AppCompatTextView>(R.id.tvDeviceName)
            tvName.text = "name: ${device.name}\naddr: ${device.address}\nalias: ${device.alias}"
            view.setOnClickListener { onDeviceSelected.invoke(device) }
        }
    }

}