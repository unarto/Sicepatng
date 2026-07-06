package com.sicepat.xrayapp.root

import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.root.RootManager.isRootAvailable
import com.sicepat.xrayapp.root.RootManager.refresh
import com.sicepat.xrayapp.util.LogUtil
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects whether the device grants root (`su`) access using libsu.
 */
object RootManager {

    @Volatile
    private var cached: Boolean? = null

    /** Last known result without probing. Defaults to false when never probed. */
    fun cachedRoot(): Boolean = cached ?: false

    /**
     * Returns whether root is available, probing once if unknown.
     */
    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) cached?.let { return it }
        val result = probe()
        cached = result
        return result
    }

    /** Probes root off the main thread, updates the cache, and returns the result. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val result = probe()
        cached = result
        result
    }

    private fun probe(): Boolean {
        return try {
            val isRoot = Shell.getShell().isRoot
            LogUtil.i(AppConfig.TAG, "RootManager: root available (libsu) = $isRoot")
            isRoot
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "RootManager: no root access (${e.message})")
            false
        }
    }
}
