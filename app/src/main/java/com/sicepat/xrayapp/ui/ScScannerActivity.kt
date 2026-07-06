package com.sicepat.xrayapp.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.extension.toastError
import com.sicepat.xrayapp.extension.toastSuccess
import com.sicepat.xrayapp.handler.AngConfigManager

class ScScannerActivity : HelperBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val (count, countSub) = AngConfigManager.importBatchConfig(scanResult, "", false)
                    withContext(Dispatchers.Main) {
                        if (count + countSub > 0) {
                            toastSuccess(R.string.toast_success)
                        } else {
                            toastError(R.string.toast_failure)
                        }
                        startActivity(Intent(this@ScScannerActivity, MainActivity::class.java))
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }
}
