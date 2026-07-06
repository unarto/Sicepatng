package com.sicepat.xrayapp.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.databinding.ActivityHotspotRootBinding
import com.sicepat.xrayapp.handler.MmkvManager
import com.sicepat.xrayapp.core.CoreServiceManager
import com.sicepat.xrayapp.root.RootManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HotspotRootActivity : BaseActivity() {

    private lateinit var binding: ActivityHotspotRootBinding
    private var isUpdatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHotspotRootBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Hotspot Root"

        val isRootSharingEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ROOT_LAN_SHARING)
        isUpdatingSwitch = true
        binding.switchHotspotRoot.isChecked = isRootSharingEnabled
        isUpdatingSwitch = false

        binding.switchHotspotRoot.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitch) return@setOnCheckedChangeListener

            if (isChecked) {
                lifecycleScope.launch {
                    // Cek root dengan libsu secara asynchronous
                    val isRoot = RootManager.refresh()
                    if (isRoot) {
                        MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, true)
                        Toast.makeText(this@HotspotRootActivity, "Hotspot Root diaktifkan", Toast.LENGTH_SHORT).show()
                        restartV2RayService()
                    } else {
                        isUpdatingSwitch = true
                        binding.switchHotspotRoot.isChecked = false
                        isUpdatingSwitch = false
                        Toast.makeText(this@HotspotRootActivity, "Akses root tidak tersedia (libsu)", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                MmkvManager.encodeSettings(AppConfig.PREF_ROOT_LAN_SHARING, false)
                Toast.makeText(this, "Hotspot Root dinonaktifkan", Toast.LENGTH_SHORT).show()
                restartV2RayService()
            }
        }
    }

    private fun restartV2RayService() {
        Toast.makeText(this, "Menerapkan konfigurasi...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            CoreServiceManager.stopVService(this@HotspotRootActivity)
            delay(1000)
            CoreServiceManager.startVServiceFromToggle(this@HotspotRootActivity)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
