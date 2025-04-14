package com.example.wifip2p

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    @RequiresPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d("P2P", "Wi-Fi Direct está ACTIVADO")
                } else {
                    Log.d("P2P", "Wi-Fi Direct está DESACTIVADO")
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d("P2P", "Se detectaron nuevos dispositivos cercanos")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
                ) {
                    manager.requestPeers(channel, activity.peerListListenerPublic)
                } else {
                    Log.w("P2P", "No se tiene permiso NEARBY_WIFI_DEVICES. No se puede escanear dispositivos.")
                }

            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo?.isConnected == true) {
                    Log.d("P2P", "Conectado a un dispositivo")
                    manager.requestConnectionInfo(channel, activity.connectionInfoListenerPublic)
                } else {
                    Log.d("P2P", "Se desconectó la conexión P2P")
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Log.d("P2P", "Cambió el estado del dispositivo actual")
            }
        }
    }
}
