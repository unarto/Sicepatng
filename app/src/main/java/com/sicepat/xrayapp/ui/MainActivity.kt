package com.sicepat.xrayapp.ui

import android.animation.LayoutTransition
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import kotlinx.coroutines.isActive
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.tabs.TabLayoutMediator
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.core.CoreServiceManager
import com.sicepat.xrayapp.databinding.ActivityMainBinding
import com.sicepat.xrayapp.enums.EConfigType
import com.sicepat.xrayapp.enums.PermissionType
import com.sicepat.xrayapp.extension.toast
import com.sicepat.xrayapp.extension.toastError
import com.sicepat.xrayapp.handler.AngConfigManager
import com.sicepat.xrayapp.handler.MmkvManager
import com.sicepat.xrayapp.handler.SettingsChangeManager
import com.sicepat.xrayapp.handler.SettingsManager
import com.sicepat.xrayapp.handler.SubscriptionUpdater
import com.sicepat.xrayapp.util.LogUtil
import com.sicepat.xrayapp.util.Utils
import com.sicepat.xrayapp.extension.toSpeedString
import com.sicepat.xrayapp.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity(), NavigationBarView.OnItemSelectedListener {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var groupPagerAdapter: GroupPagerAdapter
    private var tabMediator: TabLayoutMediator? = null

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private val requestActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SettingsChangeManager.consumeRestartService() && mainViewModel.isRunning.value == true) {
            restartV2Ray()
        }
        if (SettingsChangeManager.consumeSetupGroupTab()) {
            setupGroupTab()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, false, getString(R.string.title_server))

        // setup viewpager and tablayout
        groupPagerAdapter = GroupPagerAdapter(this, emptyList())
        binding.viewPager.adapter = groupPagerAdapter
        binding.viewPager.isUserInputEnabled = true

        // setup navigation drawer
        binding.navView.selectedItemId = R.id.nav_dashboard
        binding.navView.setOnItemSelectedListener(this)

        binding.fab.setOnClickListener { handleFabAction() }
        binding.fabPing.setOnClickListener {
            toast(getString(R.string.connection_test_testing_count, mainViewModel.serversCache.count()))
            mainViewModel.testAllRealPing()
        }
        binding.layoutTest.setOnClickListener { handleLayoutTestClick() }

        setupDashboard()
        setupTools()
        setupGroupTab()
        setupViewModel()
        SubscriptionUpdater.sync()
        mainViewModel.reloadServerList()

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {
        }
    }

    private var dashboardUpdateJob: kotlinx.coroutines.Job? = null
    private var connectionStartTime = 0L

    private var isDashboardEditMode = false
    private val dashboardCards = mutableMapOf<String, FrameLayout>()
    private val dashboardCloseButtons = mutableMapOf<String, ImageView>()
    private lateinit var dashboardAddSection: LinearLayout
    private lateinit var dashboardAddList: LinearLayout

    private fun startDashboardUpdates() {
        dashboardUpdateJob?.cancel()
        dashboardUpdateJob = lifecycleScope.launch(Dispatchers.Main) {
            var lastQueryTime = System.currentTimeMillis()
            while (isActive) {
                var intranetIp = "-"
                var usedMemMb = 0L

                if (mainViewModel.isRunning.value == true) {
                    val queryTime = System.currentTimeMillis()
                    val sinceLastQueryIn = queryTime - lastQueryTime
                    val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0
                    
                    if (connectionStartTime != 0L) {
                        val duration = (queryTime - connectionStartTime) / 1000
                        val hours = duration / 3600
                        val minutes = (duration % 3600) / 60
                        val seconds = duration % 60
                        val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        binding.fab.text = timeString
                    }
                    
                    var proxyUplink = 0L
                    var proxyDownlink = 0L
                    var directUplink = 0L
                    var directDownlink = 0L
                    
                    withContext(Dispatchers.IO) {
                        com.sicepat.xrayapp.core.CoreServiceManager.queryAllOutboundTrafficStats().forEach { stat ->
                            when {
                                stat.tag == AppConfig.TAG_DIRECT -> {
                                    when (stat.direction) {
                                        AppConfig.UPLINK -> directUplink += stat.value
                                        AppConfig.DOWNLINK -> directDownlink += stat.value
                                    }
                                }
                                stat.tag.startsWith(AppConfig.TAG_PROXY) -> {
                                    when (stat.direction) {
                                        AppConfig.UPLINK -> proxyUplink += stat.value
                                        AppConfig.DOWNLINK -> proxyDownlink += stat.value
                                    }
                                }
                            }
                        }
                        intranetIp = getLocalIpAddress()
                        if (intranetIp.isEmpty()) intranetIp = "-"
                        val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        val processMemoryInfo = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
                        usedMemMb = (processMemoryInfo[0].totalPss / 1024).toLong()
                    }
                    
                    val upSpeed = (proxyUplink + directUplink) / sinceLastQueryInSeconds
                    val downSpeed = (proxyDownlink + directDownlink) / sinceLastQueryInSeconds
                    
                    binding.tvNetworkSpeed.text = "↑ ${upSpeed.toLong().toSpeedString()}   ↓ ${downSpeed.toLong().toSpeedString()}"
                    binding.speedChartView.addSpeed(downSpeed.toFloat())
                    
                    binding.tvTrafficUsage.text = "↑ ${proxyUplink.toSpeedString()}\n↓ ${proxyDownlink.toSpeedString()}"
                    binding.ringTrafficUsage.updateUsage(proxyUplink, proxyDownlink)
                    
                    lastQueryTime = queryTime
                } else {
                    binding.tvNetworkSpeed.text = "↑ 0 B/s   ↓ 0 B/s"
                    binding.speedChartView.addSpeed(0f)
                    binding.tvTrafficUsage.text = "↑ 0 B\n↓ 0 B"
                    binding.ringTrafficUsage.updateUsage(0L, 0L)
                }
                
                if (mainViewModel.isRunning.value == true) {
                    binding.tvProviderIp.text = intranetIp
                    binding.tvMemoryInfo.text = "$usedMemMb MB"
                    
                    val elapsedSeconds = ((System.currentTimeMillis() - connectionStartTime) / 1000).toInt()
                    if (elapsedSeconds > 0 && elapsedSeconds % 5 == 0) {
                        mainViewModel.testCurrentServerRealPing()
                    }
                } else {
                    binding.tvProviderIp.text = "-"
                    binding.tvMemoryInfo.text = "0 MB"
                }

                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    private fun setupTools() {
        val btnHotshare = findViewById<android.view.View>(R.id.btn_hotshare)
        val btnHotspotRoot = findViewById<android.view.View>(R.id.btn_hotspot_root)
        val btnPerAppProxy = findViewById<android.view.View>(R.id.btn_per_app_proxy)
        val btnRoutingSettings = findViewById<android.view.View>(R.id.btn_routing_settings)
        val btnUserAsset = findViewById<android.view.View>(R.id.btn_user_asset)
        val btnLogcat = findViewById<android.view.View>(R.id.btn_logcat)
        val btnSettings = findViewById<android.view.View>(R.id.btn_settings)
        val btnCheckUpdate = findViewById<android.view.View>(R.id.btn_check_update)
        val btnBackupRestore = findViewById<android.view.View>(R.id.btn_backup_restore)
        val btnAbout = findViewById<android.view.View>(R.id.btn_about)
        
        btnHotshare?.setOnClickListener {
            startActivity(Intent(this, HotshareActivity::class.java))
        }
        
        btnHotspotRoot?.setOnClickListener {
            com.sicepat.xrayapp.root.RootLanSharing.startClientSharing(this)
            toast("Hotspot Root Client Sharing Started")
        }

        btnPerAppProxy?.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, PerAppProxyActivity::class.java))
        }
        
        btnRoutingSettings?.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, RoutingSettingActivity::class.java))
        }
        
        btnUserAsset?.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, UserAssetActivity::class.java))
        }
        
        btnLogcat?.setOnClickListener {
            startActivity(Intent(this, LogcatActivity::class.java))
        }
        
        btnSettings?.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        
        btnCheckUpdate?.setOnClickListener {
            startActivity(Intent(this, CheckUpdateActivity::class.java))
        }
        
        btnBackupRestore?.setOnClickListener {
            requestActivityLauncher.launch(Intent(this, BackupActivity::class.java))
        }
        
        btnAbout?.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun initDynamicDashboard() {
        val layoutDashboard = binding.layoutDashboard.getChildAt(0) as LinearLayout
        layoutDashboard.layoutTransition = android.animation.LayoutTransition()
        
        val columnsLayout = layoutDashboard.getChildAt(1) as LinearLayout
        val leftColumn = columnsLayout.getChildAt(0) as LinearLayout
        val rightColumn = columnsLayout.getChildAt(1) as LinearLayout
        leftColumn.layoutTransition = android.animation.LayoutTransition()
        rightColumn.layoutTransition = android.animation.LayoutTransition()

        dashboardCards["NetworkSpeed"] = binding.tvNetworkSpeed.parent.parent as FrameLayout
        dashboardCards["TrafficUsage"] = binding.ringTrafficUsage.parent.parent as FrameLayout
        dashboardCards["NetworkDelay"] = binding.tvNetworkDelay.parent.parent as FrameLayout
        dashboardCards["VpnMode"] = binding.switchVpnMode.parent.parent as FrameLayout
        dashboardCards["OutboundMode"] = binding.rgOutboundMode.parent.parent as FrameLayout
        dashboardCards["ProviderIp"] = binding.tvProviderIp.parent.parent as FrameLayout
        dashboardCards["MemoryInfo"] = binding.tvMemoryInfo.parent.parent as FrameLayout

        dashboardAddSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * resources.displayMetrics.density).toInt() }
            
            val divider = View(this@MainActivity).apply {
                setBackgroundColor(Color.parseColor("#44FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(
                    (32 * resources.displayMetrics.density).toInt(), 
                    (4 * resources.displayMetrics.density).toInt()
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = (8 * resources.displayMetrics.density).toInt()
                }
            }
            addView(divider)
            
            val title = TextView(this@MainActivity).apply {
                text = "Add"
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
            }
            addView(title)
            
            dashboardAddList = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                layoutTransition = android.animation.LayoutTransition()
            }
            addView(dashboardAddList)
        }
        layoutDashboard.addView(dashboardAddSection)

        val dragListener = View.OnDragListener { v, event ->
            val targetLayout = v as? LinearLayout ?: return@OnDragListener false
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> true
                android.view.DragEvent.ACTION_DRAG_ENTERED -> true
                android.view.DragEvent.ACTION_DRAG_LOCATION -> true
                android.view.DragEvent.ACTION_DRAG_EXITED -> true
                android.view.DragEvent.ACTION_DROP -> {
                    val draggedView = event.localState as? View ?: return@OnDragListener false
                    val sourceLayout = draggedView.parent as? ViewGroup ?: return@OnDragListener false
                    
                    if (targetLayout == layoutDashboard) {
                        // For root layout, only allow dropping before columnsLayout
                        if (event.y > columnsLayout.y) return@OnDragListener false
                    }
                    
                    sourceLayout.removeView(draggedView)
                    
                    var insertIndex = -1
                    for (i in 0 until targetLayout.childCount) {
                        val child = targetLayout.getChildAt(i)
                        // Ignore non-card elements
                        if (child == columnsLayout || child == dashboardAddSection) continue
                        
                        if (event.y < child.y + child.height / 2) {
                            insertIndex = i
                            break
                        }
                    }
                    
                    if (insertIndex == -1) {
                        if (targetLayout == layoutDashboard) {
                            targetLayout.addView(draggedView, targetLayout.indexOfChild(columnsLayout))
                        } else {
                            targetLayout.addView(draggedView)
                        }
                    } else {
                        targetLayout.addView(draggedView, insertIndex)
                    }
                    
                    val key = dashboardCards.entries.find { it.value == draggedView }?.key
                    if (key != null) {
                        val closeBtn = dashboardCloseButtons[key]
                        if (targetLayout == dashboardAddList) {
                            closeBtn?.setImageResource(R.drawable.ic_add_24dp)
                        } else {
                            closeBtn?.setImageResource(R.drawable.ic_close)
                        }
                    }
                    
                    saveDashboardState()
                    true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    val draggedView = event.localState as? View
                    draggedView?.post { draggedView.visibility = View.VISIBLE }
                    true
                }
                else -> false
            }
        }

        leftColumn.setOnDragListener(dragListener)
        rightColumn.setOnDragListener(dragListener)
        layoutDashboard.setOnDragListener(dragListener)
        dashboardAddList.setOnDragListener(dragListener)

        dashboardCards.forEach { (key, card) ->
            val closeBtn = ImageView(this).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1565C0"))
                }
                val dp24 = (24 * resources.displayMetrics.density).toInt()
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                layoutParams = FrameLayout.LayoutParams(dp24, dp24).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, dp8, dp8, 0)
                }
                visibility = View.GONE
                setOnClickListener {
                    if (parent == dashboardAddList) {
                        restoreCard(key)
                    } else {
                        removeCard(key)
                    }
                }
            }
            card.addView(closeBtn)
            dashboardCloseButtons[key] = closeBtn
            
            card.setOnLongClickListener {
                if (!isDashboardEditMode) {
                    toggleDashboardEditMode()
                } else {
                    val clipData = android.content.ClipData.newPlainText("card", key)
                    val shadow = View.DragShadowBuilder(card)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        card.startDragAndDrop(clipData, shadow, card, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        card.startDrag(clipData, shadow, card, 0)
                    }
                    card.visibility = View.INVISIBLE
                }
                true
            }
        }
        
        layoutDashboard.setOnClickListener {
            if (isDashboardEditMode) {
                toggleDashboardEditMode()
            }
        }
        
        loadDashboardState()
    }

    private fun removeCard(key: String) {
        val card = dashboardCards[key] ?: return
        val parent = card.parent as? ViewGroup ?: return
        if (parent == dashboardAddList) return

        parent.removeView(card)
        dashboardAddList.addView(card)
        
        val closeBtn = dashboardCloseButtons[key]
        closeBtn?.setImageResource(R.drawable.ic_add_24dp)
        
        saveDashboardState()
    }

    private fun restoreCard(key: String) {
        val card = dashboardCards[key] ?: return
        
        dashboardAddList.removeView(card)
        
        val columnsLayout = (binding.layoutDashboard.getChildAt(0) as LinearLayout).getChildAt(1) as LinearLayout
        val leftColumn = columnsLayout.getChildAt(0) as LinearLayout
        val rightColumn = columnsLayout.getChildAt(1) as LinearLayout
        
        when (key) {
            "TrafficUsage", "NetworkDelay", "VpnMode" -> leftColumn.addView(card)
            "OutboundMode", "ProviderIp", "MemoryInfo" -> rightColumn.addView(card)
            "NetworkSpeed" -> {
                val dashboard = binding.layoutDashboard.getChildAt(0) as LinearLayout
                dashboard.addView(card, 0)
            }
        }
        
        val closeBtn = dashboardCloseButtons[key]
        closeBtn?.setImageResource(R.drawable.ic_close)
        
        saveDashboardState()
    }

    private fun toggleDashboardEditMode() {
        isDashboardEditMode = !isDashboardEditMode
        
        dashboardCloseButtons.values.forEach { btn ->
            btn.visibility = if (isDashboardEditMode) View.VISIBLE else View.GONE
        }
        
        dashboardAddSection.visibility = if (isDashboardEditMode) View.VISIBLE else View.GONE
    }

    private fun saveDashboardState() {
        val hiddenKeys = dashboardCards.filter { it.value.parent == dashboardAddList }.keys.toList()
        MmkvManager.encodeSettings("hidden_dashboard_widgets", com.google.gson.Gson().toJson(hiddenKeys))
    }

    private fun loadDashboardState() {
        val json = MmkvManager.decodeSettingsString("hidden_dashboard_widgets") ?: "[]"
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            val hiddenKeys: List<String> = com.google.gson.Gson().fromJson(json, type)
            hiddenKeys.forEach { key ->
                removeCard(key)
                // hide the close button since we might not be in edit mode
                dashboardCloseButtons[key]?.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDashboard() {
        initDynamicDashboard()
        
        val isVpn = SettingsManager.isVpnMode()
        binding.switchVpnMode.isChecked = isVpn
        binding.tvVpnMode.text = if (isVpn) "VPN" else "Proxy Only"
        
        binding.switchVpnMode.setOnCheckedChangeListener { _, isChecked ->
            MmkvManager.encodeSettings(AppConfig.PREF_MODE, if (isChecked) AppConfig.VPN else "proxy_only")
            binding.tvVpnMode.text = if (isChecked) "VPN" else "Proxy Only"
            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            }
        }
        startDashboardUpdates()

        val routingMode = MmkvManager.decodeSettingsString(AppConfig.PREF_ROUTING_MODE) ?: "0"
        when (routingMode) {
            "1" -> binding.rgOutboundMode.check(R.id.rb_global)
            "2" -> binding.rgOutboundMode.check(R.id.rb_direct)
            else -> binding.rgOutboundMode.check(R.id.rb_rule)
        }
        
        binding.rgOutboundMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_global -> "1"
                R.id.rb_direct -> "2"
                else -> "0"
            }
            MmkvManager.encodeSettings(AppConfig.PREF_ROUTING_MODE, mode)
            if (mainViewModel.isRunning.value == true) {
                restartV2Ray()
            }
        }
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(false, isRunning)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun setupGroupTab() {
        val groups = mainViewModel.getSubscriptions(this)
        groupPagerAdapter.update(groups)

        tabMediator?.detach()
        tabMediator = TabLayoutMediator(binding.tabGroup, binding.viewPager) { tab, position ->
            groupPagerAdapter.groups.getOrNull(position)?.let {
                tab.text = it.remarks
                tab.tag = it.id
            }
        }.also { it.attach() }

        val targetIndex = groups.indexOfFirst { it.id == mainViewModel.subscriptionId }.takeIf { it >= 0 } ?: (groups.size - 1)
        binding.viewPager.setCurrentItem(targetIndex, false)

        binding.tabGroup.isVisible = groups.size > 1
        refreshGroupTabTitles(true)
    }

    fun refreshGroupTabTitles(refreshAll: Boolean = false) {
        val groupsToRefresh = if (refreshAll || mainViewModel.subscriptionId.isEmpty()) {
            groupPagerAdapter.groups
        } else {
            groupPagerAdapter.groups.filter { it.id == mainViewModel.subscriptionId }
        }

        groupsToRefresh.forEach { group ->
            if (group.id.isEmpty()) {
                return@forEach
            }
            val tabIndex = groupPagerAdapter.groups.indexOfFirst { it.id == group.id }
            if (tabIndex >= 0) {
                val count = MmkvManager.decodeServerList(group.id).size
                binding.tabGroup.getTabAt(tabIndex)?.text = "${group.remarks} ($count)"
            }
        }
    }

    private fun handleFabAction() {
        applyRunningState(isLoading = true, isRunning = false)

        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.isRunning.value == true) {
            setTestState(getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        } else {
            // service not running: keep existing no-op (could show a message if desired)
        }
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
        
        if (content.isNullOrEmpty() || content.contains("timeout", ignoreCase = true) || content.contains("error", ignoreCase = true)) {
            binding.tvNetworkDelay.text = "timeout"
            binding.tvNetworkDelay.setTextColor(android.graphics.Color.parseColor("#F44336"))
        } else {
            val lines = content.split("\n")
            if (lines.size > 1) {
                binding.tvNetworkDelay.text = lines[1]
            } else {
                binding.tvNetworkDelay.text = content
            }
            binding.tvNetworkDelay.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorWhite))
        }
    }

    private fun applyRunningState(isLoading: Boolean, isRunning: Boolean) {
        if (isLoading) {
            binding.fab.setIconResource(R.drawable.ic_fab_check)
            binding.fab.shrink()
            return
        }

        if (isRunning) {
            if (connectionStartTime == 0L) {
                connectionStartTime = System.currentTimeMillis()
            }
            binding.fab.setIconResource(R.drawable.ic_stop_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_active))
            binding.fab.contentDescription = getString(R.string.action_stop_service)
            binding.fab.extend()
            setTestState(getString(R.string.connection_connected))
            binding.layoutTest.isFocusable = true
        } else {
            connectionStartTime = 0L
            binding.fab.text = ""
            binding.fab.shrink()
            binding.fab.setIconResource(R.drawable.ic_play_24dp)
            binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_fab_inactive))
            binding.fab.contentDescription = getString(R.string.tasker_start_service)
            setTestState(getString(R.string.connection_not_connected))
            binding.layoutTest.isFocusable = false
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val isProxies = binding.layoutProxies.visibility == android.view.View.VISIBLE
        for (i in 0 until menu.size()) {
            menu.getItem(i).isVisible = isProxies
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    mainViewModel.filterConfig(newText.orEmpty())
                    return false
                }
            })

            searchView.setOnCloseListener {
                mainViewModel.filterConfig("")
                false
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_qrcode -> {
            importQRcode()
            true
        }

        R.id.import_clipboard -> {
            importClipboard()
            true
        }

        R.id.import_local -> {
            importConfigLocal()
            true
        }

        R.id.import_manually_policy_group -> {
            importManually(EConfigType.POLICYGROUP.value)
            true
        }

        R.id.import_manually_proxy_chain -> {
            importManually(EConfigType.PROXYCHAIN.value)
            true
        }

        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }

        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }

        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }

        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }

        R.id.import_manually_http -> {
            importManually(EConfigType.HTTP.value)
            true
        }

        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }

        R.id.import_manually_wireguard -> {
            importManually(EConfigType.WIREGUARD.value)
            true
        }

        R.id.import_manually_hysteria2 -> {
            importManually(EConfigType.HYSTERIA2.value)
            true
        }

        R.id.export_all -> {
            exportAll()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            delAllConfig()
            true
        }

        R.id.del_duplicate_config -> {
            delDuplicateConfig()
            true
        }

        R.id.del_invalid_config -> {
            delInvalidConfig()
            true
        }

        R.id.sort_by_test_results -> {
            sortByTestResults()
            true
        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.locate_selected_config -> {
            locateSelectedServer()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType: Int) {
        if (createConfigType == EConfigType.POLICYGROUP.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
        } else if (createConfigType == EConfigType.PROXYCHAIN.value) {
            startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
        } else {
            startActivity(
                Intent()
                    .putExtra("createConfigType", createConfigType)
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerActivity::class.java)
            )
        }
    }

    /**
     * import config from qrcode
     */
    private fun importQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importBatchConfig(scanResult)
            }
        }
        return true
    }

    /**
     * import config from clipboard
     */
    private fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
            return false
        }
        return true
    }

    private fun importBatchConfig(server: String?) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(server, mainViewModel.subscriptionId, true)
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            toast(getString(R.string.title_import_config_count, count))
                            mainViewModel.reloadServerList()
                            refreshGroupTabTitles()
                        }

                        countSub > 0 -> setupGroupTab()
                        else -> toastError(R.string.toast_failure)
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

    /**
     * import config from local config file
     */
    private fun importConfigLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from local file", e)
            return false
        }
        return true
    }


    /**
     * import config from sub
     */
    fun importConfigViaSub(): Boolean {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = mainViewModel.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(
                        getString(
                            R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount
                        )
                    )
                }
                if (result.configCount > 0) {
                    mainViewModel.reloadServerList()
                    refreshGroupTabTitles()
                }
                hideLoading()
            }
        }
        return true
    }

    private fun exportAll() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val ret = mainViewModel.exportAllServer()
            launch(Dispatchers.Main) {
                if (ret > 0)
                    toast(getString(R.string.title_export_config_count, ret))
                else
                    toastError(R.string.toast_failure)
                hideLoading()
            }
        }
    }

    private fun delAllConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeAllServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delDuplicateConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeDuplicateServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_duplicate_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun delInvalidConfig() {
        AlertDialog.Builder(this).setMessage(R.string.del_invalid_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                showLoading()
                lifecycleScope.launch(Dispatchers.IO) {
                    val ret = mainViewModel.removeInvalidServer()
                    launch(Dispatchers.Main) {
                        mainViewModel.reloadServerList()
                        refreshGroupTabTitles()
                        toast(getString(R.string.title_del_config_count, ret))
                        hideLoading()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                //do noting
            }
            .show()
    }

    private fun sortByTestResults() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            mainViewModel.sortByTestResults()
            launch(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
            }
        }
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri).use { input ->
                importBatchConfig(input?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
        }
    }

    /**
     * Locates and scrolls to the currently selected server.
     * If the selected server is in a different group, automatically switches to that group first.
     */
    private fun locateSelectedServer() {
        val targetSubscriptionId = mainViewModel.findSubscriptionIdBySelect()
        if (targetSubscriptionId.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }

        val targetGroupIndex = groupPagerAdapter.groups.indexOfFirst { it.id == targetSubscriptionId }
        if (targetGroupIndex < 0) {
            toast(R.string.toast_server_not_found_in_group)
            return
        }

        // Switch to target group if needed, then scroll to the server
        if (binding.viewPager.currentItem != targetGroupIndex) {
            binding.viewPager.setCurrentItem(targetGroupIndex, true)
            binding.viewPager.postDelayed({ scrollToSelectedServer(targetGroupIndex) }, 1000)
        } else {
            scrollToSelectedServer(targetGroupIndex)
        }
    }

    /**
     * Scrolls to the selected server in the specified fragment.
     * @param groupIndex The index of the group/fragment to scroll in
     */
    private fun scrollToSelectedServer(groupIndex: Int) {
        val itemId = groupPagerAdapter.getItemId(groupIndex)
        val fragment = supportFragmentManager.findFragmentByTag("f$itemId") as? GroupServerFragment

        if (fragment?.isAdded == true && fragment.view != null) {
            fragment.scrollToSelectedServer()
        } else {
            toast(R.string.toast_fragment_not_available)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_dashboard -> {
                binding.layoutDashboard.visibility = android.view.View.VISIBLE
                binding.layoutProxies.visibility = android.view.View.GONE
                binding.layoutTools.visibility = android.view.View.GONE
                binding.fab.visibility = android.view.View.VISIBLE
                binding.fabPing.visibility = android.view.View.GONE
                supportActionBar?.title = "Dashboard"
                invalidateOptionsMenu()
                return true
            }
            R.id.nav_proxies -> {
                binding.layoutDashboard.visibility = android.view.View.GONE
                binding.layoutProxies.visibility = android.view.View.VISIBLE
                binding.layoutTools.visibility = android.view.View.GONE
                binding.fab.visibility = android.view.View.GONE
                binding.fabPing.visibility = android.view.View.VISIBLE
                supportActionBar?.title = getString(R.string.title_server)
                invalidateOptionsMenu()
                return true
            }
            R.id.nav_profiles -> {
                requestActivityLauncher.launch(Intent(this, SubSettingActivity::class.java))
                return false
            }
            R.id.nav_tools -> {
                binding.layoutDashboard.visibility = android.view.View.GONE
                binding.layoutProxies.visibility = android.view.View.GONE
                binding.layoutTools.visibility = android.view.View.VISIBLE
                binding.fab.visibility = android.view.View.GONE
                binding.fabPing.visibility = android.view.View.GONE
                supportActionBar?.title = "Tools"
                invalidateOptionsMenu()
                return true
            }
        }

        return true
    }

    override fun onDestroy() {
        tabMediator?.detach()
        super.onDestroy()
    }
}// Force redeploy
