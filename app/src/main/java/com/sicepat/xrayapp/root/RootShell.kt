package com.sicepat.xrayapp.root

import android.content.Context
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.util.LogUtil
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal root command runner backed by the `su` binary.
 *
 * Scripts are written to the app's private root runtime dir and executed with
 * `su -c sh <file>` so shell quoting stays simple. stderr is merged into stdout to
 * avoid pipe-buffer deadlocks.
 */
object RootShell {

    data class Result(val code: Int, val output: String) {
        val success: Boolean get() = code == 0
    }

    /** Write [script] to `<filesDir>/root/<name>` and run it as root. */
    suspend fun runScript(context: Context, name: String, script: String): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val file = File(dir, name).apply {
            writeText(script)
            setExecutable(true, false)
        }
        exec("sh ${file.absolutePath}")
    }

    suspend fun exec(command: String, timeoutSeconds: Long = 30): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                LogUtil.e(AppConfig.TAG, "RootShell: timed out: $command")
                Result(-1, output)
            }
            val result = Result(process.exitValue(), output)
            if (!result.success) {
                LogUtil.w(AppConfig.TAG, "RootShell: '$command' exited ${result.code}: ${output.trim()}")
            }
            result
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell: failed to run '$command'", e)
            Result(-1, e.message ?: e.javaClass.simpleName)
        }
    }
}
