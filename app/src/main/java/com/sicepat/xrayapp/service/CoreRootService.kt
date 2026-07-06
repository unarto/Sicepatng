package com.sicepat.xrayapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.contracts.ServiceControl
import com.sicepat.xrayapp.core.CoreServiceManager
import com.sicepat.xrayapp.handler.SettingsManager
import com.sicepat.xrayapp.root.RootProxyManager
import com.sicepat.xrayapp.util.LogUtil
import com.sicepat.xrayapp.util.MyContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.SoftReference

/**
 * Foreground service for the root (system-wide) run modes. Unlike [CoreVpnService] it
 * does not use Android VpnService — traffic is routed by iptables instead
 * (see [RootProxyManager]).
 *
 * The in-process core is started first (so its listener is up and the foreground
 * notification is posted promptly), then the root routing rules are installed off the
 * main thread. On teardown the rules are removed before the core stops.
 */
class CoreRootService : Service(), ServiceControl {

    private var setupJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        LogUtil.i(AppConfig.TAG, "StartCore-Root: Service created")
        CoreServiceManager.serviceControl = SoftReference(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtil.i(AppConfig.TAG, "StartCore-Root: command received")
        // Start the in-process core first (this also posts the foreground notification),
        // then install the root routing off the main thread.
        setupJob = CoroutineScope(Dispatchers.IO).launch {
            if (!CoreServiceManager.startCoreLoop(null)) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: core failed to start")
                stopService()
                return@launch
            }
            if (!RootProxyManager.start(this@CoreRootService)) {
                LogUtil.e(AppConfig.TAG, "StartCore-Root: failed to start root mode, stopping")
                stopService()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val appContext = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            setupJob?.cancelAndJoin()
            RootProxyManager.stop(appContext)
            CoreServiceManager.stopCoreLoop()
        }
    }

    override fun getService(): Service = this

    override fun startService() {
        // do nothing
    }

    override fun stopService() {
        stopSelf()
    }

    override fun vpnProtect(socket: Int): Boolean = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let {
            MyContextWrapper.wrap(newBase, SettingsManager.getLocale())
        }
        super.attachBaseContext(context)
    }
}
