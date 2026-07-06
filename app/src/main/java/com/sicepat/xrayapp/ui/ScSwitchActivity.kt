package com.sicepat.xrayapp.ui

import android.os.Bundle
import com.sicepat.xrayapp.R
import com.sicepat.xrayapp.core.CoreServiceManager

class ScSwitchActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moveTaskToBack(true)

        setContentView(R.layout.activity_none)

        if (CoreServiceManager.isRunning()) {
            CoreServiceManager.stopVService(this)
        } else {
            CoreServiceManager.startVServiceFromToggle(this)
        }
        finish()
    }
}
