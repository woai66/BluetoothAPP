package com.qs.myapplication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class BluetoothService(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var listenJob: Job? = null
    private var onDataReceived: ((ByteArray) -> Unit)? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // SPP UUID
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun getBondedDevices(): Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

    fun connect(device: BluetoothDevice): Boolean {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            closeConnection()
            return false
        }
    }

    fun send(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun receive(): ByteArray? {
        return try {
            val buffer = ByteArray(1024)
            val bytes = inputStream?.read(buffer) ?: -1
            if (bytes > 0) buffer.copyOf(bytes) else null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    fun startListening(onData: (ByteArray) -> Unit) {
        onDataReceived = onData
        listenJob?.cancel()
        listenJob = coroutineScope.launch {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        onDataReceived?.invoke(buffer.copyOf(bytes))
                    }
                } catch (e: IOException) {
                    break
                }
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    fun closeConnection() {
        stopListening()
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
