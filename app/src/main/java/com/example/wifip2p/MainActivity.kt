package com.example.wifip2p

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

class MainActivity : ComponentActivity() {

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WifiDirectBroadcastReceiver
    private lateinit var intentFilter: IntentFilter

    private val PERMISSION_REQUEST_CODE = 1001

    // Listeners públicos que usará el BroadcastReceiver
    val peerListListenerPublic = WifiP2pManager.PeerListListener { peers ->
        Log.d("P2P", "Dispositivos encontrados: ${peers.deviceList.size}")
        devices.clear()
        for (device in peers.deviceList) {
            val name = device.deviceName.ifBlank { "Sin nombre" }
            val address = device.deviceAddress
            devices.add("$name\n$address")
        }
    }


    val connectionInfoListenerPublic = WifiP2pManager.ConnectionInfoListener { info ->
        Log.d("P2P", "¿Es el grupo dueño?: ${info.isGroupOwner}")
        Log.d("P2P", "Dirección del grupo: ${info.groupOwnerAddress?.hostAddress}")
    }

    val devices = mutableStateListOf<String>()


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
                        devices = devices,
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
    modifier: Modifier = Modifier,
    devices: List<String>
) {
    Column(modifier = modifier.padding(16.dp)) {
        Button(
            onClick = onDiscoverClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Buscar dispositivos")
        }

        Text(text = "Dispositivos detectados:")

        LazyColumn {
            items(devices) { device ->
                Text(
                    text = device,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
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
            devices = listOf("Dispositivo de ejemplo\n00:11:22:33:44:55")
        )
    }
}
