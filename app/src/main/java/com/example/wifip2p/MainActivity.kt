package com.example.wifip2p

//import android.net.wifi.p2p.WpsInfo
import android.Manifest
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wifip2p.ui.theme.WifiP2PTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.dp
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pConfig
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.io.OutputStreamWriter
import java.net.Socket


class MainActivity : ComponentActivity() {

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WifiDirectBroadcastReceiver
    private lateinit var intentFilter: IntentFilter
    var isGroupOwner by mutableStateOf<Boolean?>(null)
    var groupOwnerIp by mutableStateOf<String?>(null)

    private val PERMISSION_REQUEST_CODE = 1001

    // Listeners públicos que usará el BroadcastReceiver
    val peerListListenerPublic = WifiP2pManager.PeerListListener { peers ->
        Log.d("P2P", "Dispositivos encontrados: ${peers.deviceList.size}")
        devices.clear()
        devices.addAll(peers.deviceList)
    }

    val connectionInfoListenerPublic = WifiP2pManager.ConnectionInfoListener { info ->
        isGroupOwner = info.isGroupOwner
        groupOwnerIp = info.groupOwnerAddress?.hostAddress

        if (info.isGroupOwner) {
            startServerSocket()
        }


        Log.d("P2P", "¿Es el grupo dueño?: $isGroupOwner")
        Log.d("P2P", "Dirección del grupo: $groupOwnerIp")
    }

    val devices = mutableStateListOf<WifiP2pDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicializar Wi-Fi Direct
        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        // Preparar filtro para eventos
        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        // Crear el receptor
        receiver = WifiDirectBroadcastReceiver(manager, channel, this)

        // Pedir permisos
        requestPermissionsIfNecessary()

        // Interfaz Compose
        setContent {
            WifiP2PTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GreetingWithButton(
                        onDiscoverClick = { discoverDevices() },
                        onDeviceClick = { device -> connectToDevice(device) },
                        onSendMessageClick = { sendMessageToServer("Hola desde el cliente") },
                        devices = devices,
                        isGroupOwner = isGroupOwner,
                        groupOwnerIp = groupOwnerIp,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestPermissionsIfNecessary() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    fun discoverDevices() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        ) {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("P2P", "Descubrimiento iniciado correctamente")
                }

                override fun onFailure(reason: Int) {
                    Log.e("P2P", "Fallo al iniciar descubrimiento. Código: $reason")
                }
            })
        } else {
            Log.w("P2P", "No se puede descubrir dispositivos: falta permiso NEARBY_WIFI_DEVICES")
        }
    }

    fun connectToDevice(device: WifiP2pDevice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        ) {
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
                wps.setup = 0 // 0 equivale a WpsInfo.PBC (Push Button Configuration)
            }

            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("P2P", "Conexión iniciada con ${device.deviceName}")
                }

                override fun onFailure(reason: Int) {
                    Log.e("P2P", "Error al conectar con ${device.deviceName}, código: $reason")
                }
            })
        } else {
            Log.w("P2P", "Falta permiso NEARBY_WIFI_DEVICES para conectar")
        }
    }

    fun startServerSocket() {
        Thread {
            try {
                val serverSocket = ServerSocket(8888)
                Log.d("P2P-SOCKET", "Servidor iniciado. Esperando conexión...")

                val client = serverSocket.accept()
                Log.d("P2P-SOCKET", "Cliente conectado: ${client.inetAddress.hostAddress}")

                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val message = reader.readLine()
                Log.d("P2P-SOCKET", "Mensaje recibido: $message")

                client.close()
                serverSocket.close()
            } catch (e: Exception) {
                Log.e("P2P-SOCKET", "Error en el servidor: ${e.message}")
            }
        }.start()
    }

    fun sendMessageToServer(message: String) {
        groupOwnerIp?.let { ip ->
            Thread {
                try {
                    val socket = Socket(ip, 8888)
                    val writer = OutputStreamWriter(socket.getOutputStream())
                    writer.write(message + "\n")
                    writer.flush()
                    socket.close()
                    Log.d("P2P-SOCKET", "Mensaje enviado al servidor: $message")
                } catch (e: Exception) {
                    Log.e("P2P-SOCKET", "Error al enviar mensaje: ${e.message}")
                }
            }.start()
        } ?: Log.e("P2P-SOCKET", "IP del servidor no disponible")
    }


    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}

@Composable
fun GreetingWithButton(
    onDiscoverClick: () -> Unit,
    onDeviceClick: (WifiP2pDevice) -> Unit,
    onSendMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
    devices: List<WifiP2pDevice>,
    isGroupOwner: Boolean?,
    groupOwnerIp: String?
) {
    Column(modifier = modifier.padding(16.dp)) {

        // Botón para buscar dispositivos
        Button(
            onClick = onDiscoverClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Buscar dispositivos")
        }

        // Lista de dispositivos detectados
        Text(text = "Dispositivos detectados:")

        LazyColumn {
            items(devices) { device ->
                Button(
                    onClick = { onDeviceClick(device) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column {
                        Text(text = device.deviceName.ifBlank { "Sin nombre" })
                        Text(
                            text = device.deviceAddress,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mostrar rol del dispositivo
        Text(
            text = when (isGroupOwner) {
                true -> "🔵 Este dispositivo es el servidor (Group Owner)"
                false -> "🟢 Este dispositivo es el cliente"
                null -> "⚪ Aún no hay conexión establecida"
            },
            style = MaterialTheme.typography.bodyMedium
        )

        // Mostrar IP del servidor
        groupOwnerIp?.let { ip ->
            Text(
                text = "IP del servidor: $ip",
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Solo el cliente puede enviar mensajes
        if (isGroupOwner == false) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onSendMessageClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar mensaje al servidor")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WifiP2PTheme {
        GreetingWithButton(
            onDiscoverClick = {},
            onDeviceClick = {},
            onSendMessageClick = {},
            devices = emptyList(),
            isGroupOwner = null,
            groupOwnerIp = null
        )
    }
}
