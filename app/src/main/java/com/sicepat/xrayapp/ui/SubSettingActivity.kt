package com.sicepat.xrayapp.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager

import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.EditText
import android.widget.TextView
import com.sicepat.xrayapp.dto.entities.SubscriptionItem
import java.util.UUID
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.contracts.BaseAdapterListener
import com.sicepat.xrayapp.databinding.ActivitySubSettingBinding
import com.sicepat.xrayapp.databinding.ItemQrcodeBinding
import com.sicepat.xrayapp.extension.toast
import com.sicepat.xrayapp.handler.AngConfigManager
import com.sicepat.xrayapp.handler.MmkvManager
import com.sicepat.xrayapp.helper.SimpleItemTouchHelperCallback
import com.sicepat.xrayapp.util.LogUtil
import com.sicepat.xrayapp.util.QRCodeDecoder
import com.sicepat.xrayapp.util.Utils
import com.sicepat.xrayapp.viewmodel.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubSettingActivity : HelperBaseActivity() {
    private val binding by lazy { ActivitySubSettingBinding.inflate(layoutInflater) }
    private val ownerActivity: SubSettingActivity
        get() = this
    private val viewModel: SubscriptionsViewModel by viewModels()
    private lateinit var adapter: SubSettingRecyclerAdapter
    private var mItemTouchHelper: ItemTouchHelper? = null
    private val share_method: Array<out String> by lazy {
        resources.getStringArray(R.array.share_sub_method)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(binding.root)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = "Profiles")

        adapter = SubSettingRecyclerAdapter(viewModel, ActivityAdapterListener())

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter

        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        
        mItemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback(adapter))
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)
        
        binding.root.findViewById<android.view.View>(R.id.fab)?.setOnClickListener {
            showImportProfileBottomSheet()
        }

    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_sub_setting, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        

        R.id.sub_update -> {
            showLoading()

            lifecycleScope.launch(Dispatchers.IO) {
                val result = AngConfigManager.updateConfigViaSubAll()
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
                    hideLoading()
                    refreshData()
                }
            }

            true
        }

        
        R.id.sort_az -> {
            lifecycleScope.launch(Dispatchers.IO) {
                // Assuming SettingsManager has a way or we can just sort the subscriptions in MmkvManager
                // The subscriptions are stored via MmkvManager
                val subs = com.sicepat.xrayapp.handler.MmkvManager.decodeSubscriptions()
                val sortedSubs = subs.sortedBy { it.subscription.remarks.lowercase() }
                val sortedIds = sortedSubs.map { it.guid }.toMutableList()
                com.sicepat.xrayapp.handler.MmkvManager.encodeSubsList(sortedIds)
                launch(Dispatchers.Main) {
                    refreshData()
                }
            }
            true
        }
        else -> super.onOptionsItemSelected(item)

    }

    @SuppressLint("NotifyDataSetChanged")
    
    private fun showImportProfileBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_import_profile, null)
        
        view.findViewById<TextView>(R.id.btn_qr_code).setOnClickListener {
            bottomSheetDialog.dismiss()
            launchQRCodeScanner { scanResult ->
                if (!scanResult.isNullOrEmpty()) {
                    val subItem = SubscriptionItem()
                    subItem.url = scanResult
                    subItem.remarks = "New Profile"
                    val guid = UUID.randomUUID().toString()
                    com.sicepat.xrayapp.handler.MmkvManager.encodeSubscription(guid, subItem)
                    startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", guid))
                }
            }
        }
        
        view.findViewById<TextView>(R.id.btn_file).setOnClickListener {
            bottomSheetDialog.dismiss()
            launchFileChooser { uri ->
                if (uri != null) {
                    val subItem = SubscriptionItem()
                    subItem.url = uri.toString()
                    subItem.remarks = "New Profile (File)"
                    val guid = UUID.randomUUID().toString()
                    com.sicepat.xrayapp.handler.MmkvManager.encodeSubscription(guid, subItem)
                    startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", guid))
                }
            }
        }
        
        view.findViewById<TextView>(R.id.btn_url).setOnClickListener {
            bottomSheetDialog.dismiss()
            val editText = EditText(this)
            editText.hint = "URL"
            AlertDialog.Builder(this)
                .setTitle("Import from URL")
                .setView(editText)
                .setPositiveButton("Submit") { _, _ ->
                    val url = editText.text.toString().trim()
                    if (url.isNotEmpty()) {
                        val subItem = SubscriptionItem()
                        subItem.url = url
                        subItem.remarks = "New Profile"
                        val guid = UUID.randomUUID().toString()
                        com.sicepat.xrayapp.handler.MmkvManager.encodeSubscription(guid, subItem)
                        startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", guid))
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        viewModel.reload()
        adapter.notifyDataSetChanged()
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(ownerActivity, SubEditActivity::class.java)
                    .putExtra("subId", guid)
            )
        }

        override fun onRemove(guid: String, position: Int) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                AlertDialog.Builder(ownerActivity)
                    .setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.remove(guid)
                        refreshData()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                viewModel.remove(guid)
                refreshData()
            }
        }

        override fun onShare(url: String) {
            AlertDialog.Builder(ownerActivity)
                .setItems(share_method.asList().toTypedArray()) { _, i ->
                    try {
                        when (i) {
                            0 -> {
                                val ivBinding =
                                    ItemQrcodeBinding.inflate(LayoutInflater.from(ownerActivity))
                                ivBinding.ivQcode.setImageBitmap(
                                    QRCodeDecoder.createQRCode(
                                        url

                                    )
                                )
                                AlertDialog.Builder(ownerActivity).setView(ivBinding.root).show()
                            }

                            1 -> {
                                Utils.setClipboard(ownerActivity, url)
                            }

                            else -> ownerActivity.toast("else")
                        }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Share subscription failed", e)
                    }
                }.show()
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
