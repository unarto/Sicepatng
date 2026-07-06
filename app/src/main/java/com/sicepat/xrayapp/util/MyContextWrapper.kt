package com.sicepat.xrayapp.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

open class MyContextWrapper(base: Context?) : ContextWrapper(base) {
    companion object {
        /**
         * Wraps the context with a new locale.
         *
         * @param context The original context.
         * @param newLocale The new locale to set.
         * @return A ContextWrapper with the new locale.
         */
        fun wrap(context: Context, newLocale: Locale?): ContextWrapper {
            var mContext = context
            val res: Resources = mContext.resources
            val configuration: Configuration = res.configuration

            val locale = newLocale ?: Locale.getDefault()
            configuration.setLocale(locale)
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)

            val scaleEnabled = com.sicepat.xrayapp.handler.MmkvManager.decodeSettingsBool(com.sicepat.xrayapp.AppConfig.PREF_THEME_TEXT_SCALING_ENABLED, false)
            if (scaleEnabled) {
                val scaleValue = com.sicepat.xrayapp.handler.MmkvManager.decodeSettingsFloat(com.sicepat.xrayapp.AppConfig.PREF_THEME_TEXT_SCALING_VALUE, 100f) / 100f
                configuration.fontScale = scaleValue
            }

            mContext = mContext.createConfigurationContext(configuration)
            return ContextWrapper(mContext)
        }
    }
}