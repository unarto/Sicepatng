package com.sicepat.xrayapp.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.databinding.ActivityLogcatBinding
import com.sicepat.xrayapp.extension.toast
import com.sicepat.xrayapp.extension.toastError
import com.sicepat.xrayapp.handler.AngConfigManager
import com.sicepat.xrayapp.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        lifecycleScope.launch {
            try {
                intent.apply {
                    if (action == Intent.ACTION_SEND) {
                        if ("text/plain" == type) {
                            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                                parseUri(it, null)
                            }
                        }
                    } else if (action == Intent.ACTION_VIEW) {
                        when (data?.host) {
                            "install-config" -> {
                                val uri: Uri? = intent.data
                                val shareUrl = uri?.getQueryParameter("url").orEmpty()
                                parseUri(shareUrl, uri?.fragment)
                            }
                            "install-sub" -> {
                                val uri: Uri? = intent.data
                                val shareUrl = uri?.getQueryParameter("url").orEmpty()
                                parseUri(shareUrl, uri?.fragment)
                            }
                            else -> {
                                toastError(R.string.toast_failure)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
            } finally {
                startActivity(Intent(this@UrlSchemeActivity, MainActivity::class.java))
                finish()
            }
        }
    }

    private suspend fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            return
        }
        LogUtil.i(AppConfig.TAG, uriString)
        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
                decodedUrl += "#${fragment}"
            }
            LogUtil.i(AppConfig.TAG, decodedUrl)
            withContext(Dispatchers.IO) {
                val (count, countSub) = AngConfigManager.importBatchConfig(decodedUrl, "", false)
                withContext(Dispatchers.Main) {
                    if (count + countSub > 0) {
                        toast(R.string.import_subscription_success)
                    } else {
                        toast(R.string.import_subscription_failure)
                    }
                }
            }
        }
    }
}
