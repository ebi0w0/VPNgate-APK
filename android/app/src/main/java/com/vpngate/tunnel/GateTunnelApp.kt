package com.vpngate.tunnel

import android.app.Application
import android.util.Log
import com.vpngate.tunnel.data.local.VpnDatabase
import com.vpngate.tunnel.worker.WeeklyVpnSyncWorker

class GateTunnelApp : Application() {
    companion object {
        private const val TAG = "GateTunnelApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing GateTunnel VPN Application")

        // Pre-warm local Room database cache
        VpnDatabase.getInstance(this)

        // Schedule periodic weekly WorkManager server discovery sync
        WeeklyVpnSyncWorker.enqueueWeeklySync(this)
    }
}
