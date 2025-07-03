package com.qs.myapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.qs.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothService: BluetoothService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bluetoothService = BluetoothService(this)

        // 动态权限请求
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        requestPermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            MyApplicationTheme {
                BluetoothScreen(bluetoothService)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(bluetoothService: BluetoothService) {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }
    var connectStatus by remember { mutableStateOf("未连接") }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var sendText by remember { mutableStateOf(TextFieldValue("")) }
    var receivedText by remember { mutableStateOf("") }
    val devices = remember { bluetoothService.getBondedDevices()?.toList() ?: emptyList() }

    // 自动监听数据
    DisposableEffect(isConnected) {
        if (isConnected) {
            bluetoothService.startListening { data ->
                // 追加到UI（主线程）
                val str = String(data)
                // Compose状态更新需用快照
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    receivedText += str + "\n"
                }
            }
        } else {
            bluetoothService.stopListening()
        }
        onDispose {
            bluetoothService.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("蓝牙串口Demo") })
        },
        content = { padding ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)) {
                Text("蓝牙支持: ${bluetoothService.isBluetoothSupported()}")
                Text("蓝牙已开启: ${bluetoothService.isBluetoothEnabled()}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("配对设备:")
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(devices) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDevice = device.address
                                    connectStatus = "正在连接..."
                                    val result = bluetoothService.connect(device)
                                    isConnected = result
                                    connectStatus = if (result) "已连接: ${device.name}" else "连接失败"
                                }
                                .padding(8.dp)
                        ) {
                            Text(text = device.name ?: "未知设备")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = device.address)
                        }
                    }
                }
                Text("连接状态: $connectStatus")
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = sendText,
                        onValueChange = { sendText = it },
                        label = { Text("发送内容") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    //发送按钮
                    Button(
                        onClick = {
                            if (isConnected) {
                                bluetoothService.send((sendText.text + "\r\n").toByteArray())//添加换行
                            }
                        },
                        enabled = isConnected
                    ) {
                        Text("发送")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("接收内容:")
                Text(receivedText, modifier = Modifier.weight(1f))
            }
        }
    )
}