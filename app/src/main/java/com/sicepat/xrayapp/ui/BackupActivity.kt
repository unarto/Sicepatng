package com.sicepat.xrayapp.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.AppConfig.WEBDAV_BACKUP_FILE_NAME
import com.sicepat.xrayapp.BuildConfig
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.databinding.ActivityBackupBinding
import com.sicepat.xrayapp.databinding.DialogWebdavBinding
import com.sicepat.xrayapp.dto.entities.WebDavConfig
import com.sicepat.xrayapp.extension.toastError
import com.sicepat.xrayapp.extension.toastSuccess
import com.sicepat.xrayapp.handler.MmkvManager
import com.sicepat.xrayapp.handler.SettingsChangeManager
import com.sicepat.xrayapp.handler.SettingsManager
import com.sicepat.xrayapp.handler.WebDavManager
import com.sicepat.xrayapp.util.LogUtil
import com.sicepat.xrayapp.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupActivity : HelperBaseActivity() {
    private val binding by lazy { ActivityBackupBinding.inflate(layoutInflater) }

    private val config_backup_options: Array<out String> by lazy {
        resources.getStringArray(R.array.config_backup_options)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(binding.root)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_configuration_backup_restore))

        binding.layoutBackup.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_configuration_backup)
                .setItems(config_backup_options) { dialog, which ->
                    when (which) {
                        0 -> backupViaLocal()
                        1 -> backupViaWebDav()
                    }
                }
                .show()
        }

        binding.layoutShare.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val ret = backupConfigurationToCache()
                withContext(Dispatchers.Main) {
                    if (ret.first) {
                        startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("application/zip")
                                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(
                                        Intent.EXTRA_STREAM,
                                        FileProvider.getUriForFile(
                                            this@BackupActivity, BuildConfig.APPLICATION_ID + ".cache", File(ret.second)
                                        )
                                    ), getString(R.string.title_configuration_share)
                            )
                        )
                    } else {
                        toastError(R.string.toast_failure)
                    }
                }
            }
        }

        binding.layoutRestore.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.title_configuration_restore)
                .setItems(config_backup_options) { dialog, which ->
                    when (which) {
                        0 -> restoreViaLocal()
                        1 -> restoreViaWebDav()
                    }
                }
                .show()
        }

        binding.layoutWebdavConfigSetting.setOnClickListener {
            showWebDavSettingsDialog()
        }
    }

    /**
     * Backup configuration to cache directory
     * Returns Pair<success, zipFilePath>
     */
    private suspend fun backupConfigurationToCache(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val folderName = "${getString(R.string.app_name)}_${dateFormatted}"
        val backupDir = this@BackupActivity.cacheDir.absolutePath + "/$folderName"
        val outputZipFilePath = "${this@BackupActivity.cacheDir.absolutePath}/$folderName.zip"

        val count = MMKV.backupAllToDirectory(backupDir)
        if (count <= 0) {
            return@withContext Pair(false, "")
        }

        if (ZipUtil.zipFromFolder(backupDir, outputZipFilePath)) {
            Pair(true, outputZipFilePath)
        } else {
            Pair(false, "")
        }
    }

    private suspend fun restoreConfiguration(zipFile: File): Boolean = withContext(Dispatchers.IO) {
        val backupDir = this@BackupActivity.cacheDir.absolutePath + "/${System.currentTimeMillis()}"

        if (!ZipUtil.unzipToFolder(zipFile, backupDir)) {
            false
        } else {
            val count = MMKV.restoreAllFromDirectory(backupDir)
            withContext(Dispatchers.Main) {
                SettingsChangeManager.makeSetupGroupTab()
                SettingsChangeManager.makeRestartService()
                SettingsManager.initApp(this@BackupActivity)
            }
            count > 0
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val targetFile =
                        File(cacheDir.absolutePath, "${System.currentTimeMillis()}.zip")
                    contentResolver.openInputStream(uri).use { input ->
                        targetFile.outputStream().use { fileOut ->
                            input?.copyTo(fileOut)
                        }
                    }
                    val restored = restoreConfiguration(targetFile)
                    withContext(Dispatchers.Main) {
                        if (restored) {
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Error during file restore", e)
                    withContext(Dispatchers.Main) {
                        toastError(R.string.toast_failure)
                    }
                }
            }
        }
    }

    private fun backupViaLocal() {
        val dateFormatted = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss",
            Locale.getDefault()
        ).format(System.currentTimeMillis())
        val defaultFileName = "${getString(R.string.app_name)}_${dateFormatted}.zip"

        launchCreateDocument(defaultFileName) { uri ->
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val ret = backupConfigurationToCache()
                        if (ret.first) {
                            // Copy the cached zip file to user-selected location
                            contentResolver.openOutputStream(uri)?.use { output ->
                                File(ret.second).inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            // Clean up cache file
                            File(ret.second).delete()
                            withContext(Dispatchers.Main) {
                                toastSuccess(R.string.toast_success)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                toastError(R.string.toast_failure)
                            }
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to backup configuration", e)
                        withContext(Dispatchers.Main) {
                            toastError(R.string.toast_failure)
                        }
                    }
                }
            }
        }
    }

    private fun restoreViaLocal() {
        showFileChooser()
    }

    private fun backupViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val ret = backupConfigurationToCache()
                if (!ret.first) {
                    withContext(Dispatchers.Main) {
                        toastError(R.string.toast_failure)
                    }
                    return@launch
                }

                tempFile = File(ret.second)
                WebDavManager.init(saved)

                val ok = try {
                    WebDavManager.uploadFile(tempFile, WEBDAV_BACKUP_FILE_NAME)
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "WebDAV upload error", e)
                    false
                }

                withContext(Dispatchers.Main) {
                    if (ok) toastSuccess(R.string.toast_success) else toastError(R.string.toast_failure)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV backup error", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                }
            } finally {
                try {
                    tempFile?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun restoreViaWebDav() {
        val saved = MmkvManager.decodeWebDavConfig()
        if (saved == null || saved.baseUrl.isEmpty()) {
            toastError(R.string.title_webdav_config_setting_unknown)
            return
        }

        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var target: File? = null
            try {
                target = File(cacheDir, "download_${System.currentTimeMillis()}.zip")
                WebDavManager.init(saved)
                val ok = WebDavManager.downloadFile(WEBDAV_BACKUP_FILE_NAME, target)
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        toastError(R.string.toast_failure)
                    }
                    return@launch
                }

                val restored = restoreConfiguration(target)
                withContext(Dispatchers.Main) {
                    if (restored) {
                        toastSuccess(R.string.toast_success)
                    } else {
                        toastError(R.string.toast_failure)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "WebDAV download error", e)
                withContext(Dispatchers.Main) { toastError(R.string.toast_failure) }
            } finally {
                try {
                    target?.delete()
                } catch (_: Exception) {
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                }
            }
        }
    }

    private fun showWebDavSettingsDialog() {
        val dialogBinding = DialogWebdavBinding.inflate(layoutInflater)

        MmkvManager.decodeWebDavConfig()?.let { cfg ->
            dialogBinding.etWebdavUrl.setText(cfg.baseUrl)
            dialogBinding.etWebdavUser.setText(cfg.username ?: "")
            dialogBinding.etWebdavPass.setText(cfg.password ?: "")
            dialogBinding.etWebdavRemotePath.setText(cfg.remoteBasePath ?: "/")
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_webdav_config_setting)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.menu_item_save_config) { _, _ ->
                val url = dialogBinding.etWebdavUrl.text.toString().trim()
                val user = dialogBinding.etWebdavUser.text.toString().trim().ifEmpty { null }
                val pass = dialogBinding.etWebdavPass.text.toString()
                val remotePath = dialogBinding.etWebdavRemotePath.text.toString().trim().ifEmpty { AppConfig.WEBDAV_BACKUP_DIR }
                val cfg = WebDavConfig(baseUrl = url, username = user, password = pass, remoteBasePath = remotePath)
                MmkvManager.encodeWebDavConfig(cfg)
                toastSuccess(R.string.toast_success)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}