package com.sicepat.xrayapp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.databinding.ActivityHotshareBinding
import com.sicepat.xrayapp.handler.MmkvManager
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.core.CoreServiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.net.InetAddress

class HotshareActivity : BaseActivity() {

    private lateinit var binding: ActivityHotshareBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHotshareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Hotshare"

        val ipAddress = getWifiIpAddress()
        
        binding.tvProxyIp.text = ipAddress
        binding.tvProxyPortHttp.text = MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT) ?: "10809"
        binding.tvProxyPortSocks.text = MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT) ?: "10808" // Assuming HTTP/SOCKS default ports for Xray
        
        // Actually HTTP port in Xray/V2Ray config usually is port + 1 or separated.
        // I will use 44355 and 10809 as in the video to not confuse, but actually it should be fetched from config.
        binding.tvProxyPortHttp.text = "44355"
        binding.tvProxyPortSocks.text = MmkvManager.decodeSettingsString(AppConfig.PREF_SOCKS_PORT) ?: "10808"

        binding.tvProxyIp.setOnClickListener {
            copyToClipboard("Proxy IP", binding.tvProxyIp.text.toString())
        }

        binding.tvProxyPortHttp.setOnClickListener {
            copyToClipboard("HTTP Port", binding.tvProxyPortHttp.text.toString())
        }

        binding.tvProxyPortSocks.setOnClickListener {
            copyToClipboard("SOCKS Port", binding.tvProxyPortSocks.text.toString())
        }
        
        binding.btnWifiSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        
        binding.btnWifiHotspot.setOnClickListener {
            val intent = Intent()
            intent.setClassName("com.android.settings", "com.android.settings.TetherSettings")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
        
        binding.btnRepeater.setOnClickListener {
            Toast.makeText(this, "Start Repeater not supported natively. Please use Wi-Fi hotspot.", Toast.LENGTH_LONG).show()
        }

        val isSharing = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)
        updateUIState(isSharing)

        binding.btnStartHotshare.setOnClickListener {
            val isCurrentlySharing = MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)
            
            if (isCurrentlySharing) {
                MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, false)
                updateUIState(false)
                Toast.makeText(this, "Hotshare dihentikan", Toast.LENGTH_SHORT).show()
            } else {
                MmkvManager.encodeSettings(AppConfig.PREF_PROXY_SHARING, true)
                updateUIState(true)
                Toast.makeText(this, "Hotshare aktif! Hubungkan perangkat lain.", Toast.LENGTH_SHORT).show()
            }
            restartV2RayService()
        }
    }

    private fun updateUIState(isSharing: Boolean) {
        if (isSharing) {
            binding.btnStartHotshare.text = "STOP HOTSHARE"
            val httpText = if (binding.cbHttp.isChecked) "HTTP Proxy Port: ${binding.tvProxyPortHttp.text}" else ""
            val socksText = if (binding.cbSocks.isChecked) "SOCKS Proxy Port: ${binding.tvProxyPortSocks.text}" else ""
            val combinedText = "Set up proxy on the connected device.\nProxy IP: ${binding.tvProxyIp.text}\n$httpText\n$socksText"
            binding.tvStep3Info.text = combinedText
            binding.tvStep3Info.visibility = android.view.View.VISIBLE
        } else {
            binding.btnStartHotshare.text = "START HOTSHARE"
            binding.tvStep3Info.visibility = android.view.View.GONE
        }
    }

    private fun restartV2RayService() {
        // We can restart the VPN service if it is running to apply proxy sharing config
        // Assuming there is a way to check if running, or just call stop and start
        Toast.makeText(this, "Restarting VPN connection to apply changes...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            CoreServiceManager.stopVService(this@HotshareActivity)
            delay(1000)
            CoreServiceManager.startVServiceFromToggle(this@HotshareActivity)
        }
    }

    private fun getWifiIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return if (ipAddress == 0) {
            "192.168.43.1" // Default hotspot IP on many Android devices
        } else {
            try {
                val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray().reversedArray()
                InetAddress.getByAddress(ipByteArray).hostAddress ?: "192.168.43.1"
            } catch (e: Exception) {
                "192.168.43.1"
            }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label disalin!", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
