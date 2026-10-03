package com.vpngate.tunnel.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vpngate.tunnel.data.local.VpnDatabase
import com.vpngate.tunnel.domain.repository.VpnServerRepository
import java.util.concurrent.TimeUnit

class WeeklyVpnSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "weekly_vpngate_sync_worker"
        private const val TAG = "WeeklyVpnSyncWorker"

        /**
         * Enqueues the weekly periodic sync job.
         * Runs automatically once every 7 days with network constraints.
         */
        fun enqueueWeeklySync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<WeeklyVpnSyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(7, TimeUnit.DAYS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.i(TAG, "Enqueued weekly VPN Gate background sync worker (7-day interval)")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting automatic weekly VPN Gate server sync...")
        return try {
            val database = VpnDatabase.getInstance(applicationContext)
            val repository = VpnServerRepository(database.serverDao())

            val result = repository.refreshServers()
            if (result.isSuccess) {
                // Prune servers older than 7 days that weren't in the new list
                repository.pruneOldServers()
                Log.i(TAG, "Weekly VPN Gate sync completed successfully. Servers updated.")
                Result.success()
            } else {
                Log.w(TAG, "Weekly VPN Gate sync failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during weekly sync: ${e.message}", e)
            Result.retry()
        }
    }
}
