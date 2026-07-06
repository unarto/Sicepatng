package com.sicepat.xrayapp.handler

import com.sicepat.xrayapp.AppConfig
import com.sicepat.xrayapp.dto.CertSha256Request
import com.sicepat.xrayapp.dto.CertSha256Result
import com.sicepat.xrayapp.dto.entities.ProfileItem
import com.sicepat.xrayapp.enums.EConfigType
import com.sicepat.xrayapp.util.HttpUtil
import com.sicepat.xrayapp.util.JsonUtil
import com.sicepat.xrayapp.util.LogUtil
import com.sicepat.xrayapp.util.Utils
import libv2ray.Libv2ray

object CertificateFingerprintManager {
    private const val TIMEOUT_MS = 5000L

    fun fetchForManualFill(profile: ProfileItem): String? {
        val request = buildRequest(profile) ?: return null
        val result = if (profile.configType == EConfigType.HYSTERIA2) {
            fetch("quic", request) { arg ->
                try {
                    val method = Libv2ray::class.java.getMethod("fetchQuicCertSha256", String::class.java)
                    method.invoke(null, arg) as String
                } catch (e: Exception) {
                    throw UnsatisfiedLinkError("fetchQuicCertSha256 missing")
                }
            }
        } else {
            fetch("tls", request) { arg ->
                try {
                    val method = Libv2ray::class.java.getMethod("fetchTlsCertSha256", String::class.java)
                    method.invoke(null, arg) as String
                } catch (e: Exception) {
                    throw UnsatisfiedLinkError("fetchTlsCertSha256 missing")
                }
            }
        }

        return result
            ?.takeIf { it.error.isBlank() }
            ?.sha256
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildRequest(profile: ProfileItem): CertSha256Request? {
        if (!isFetchable(profile)) return null

        val server = profile.server?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = profile.serverPort?.toIntOrNull()?.takeIf { it > 0 } ?: AppConfig.DEFAULT_PORT

        return CertSha256Request(
            address = resolveDialAddress(server),
            port = port,
            serverName = inferServerName(profile),
            timeoutMs = TIMEOUT_MS,
        )
    }

    private fun isFetchable(profile: ProfileItem): Boolean {
        return profile.configType == EConfigType.HYSTERIA2 || profile.security == AppConfig.TLS
    }

    private fun fetch(
        type: String,
        request: CertSha256Request,
        fetcher: (String) -> String,
    ): CertSha256Result? {
        return try {
            JsonUtil.fromJsonSafe(fetcher(JsonUtil.toJson(request)), CertSha256Result::class.java)
        } catch (e: UnsatisfiedLinkError) {
            LogUtil.e(AppConfig.TAG, "Fetch $type cert SHA-256 API missing in libv2ray", e)
            null
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Fetch $type cert SHA-256 failed", e)
            null
        }
    }

    private fun resolveDialAddress(server: String): String {
        if (Utils.isPureIpAddress(server) || !Utils.isDomainName(server)) return server

        val preferIpv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_PREFER_IPV6, false)
        return HttpUtil.resolveHostToIP(server, preferIpv6)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: server
    }

    private fun inferServerName(profile: ProfileItem): String? {
        val sni = profile.sni?.takeIf { it.isNotBlank() }
        return sni?.takeUnless { Utils.isPureIpAddress(it) }
    }
}
