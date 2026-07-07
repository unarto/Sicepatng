package com.sicepat.xrayapp.handler

import com.sicepat.xrayapp.dto.entities.ProfileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.CertificateException
import android.util.Base64

object CertificateFingerprintManager {
    fun getFingerprint(): String = ""
    fun getFingerprints(): List<String> = emptyList()

    fun fetchForManualFill(config: ProfileItem?): String? {
        if (config == null) return null
        
        val server = config.server ?: return null
        val port = config.serverPort?.toIntOrNull() ?: 443

        var host = config.sni
        if (host.isNullOrEmpty()) host = config.host
        if (host.isNullOrEmpty()) host = server

        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val factory: SSLSocketFactory = sslContext.socketFactory

            val socket = factory.createSocket(server, port) as SSLSocket
            socket.soTimeout = 5000

            if (!host.isNullOrEmpty()) {
                try {
                    val method = socket.javaClass.getMethod("setHostname", String::class.java)
                    method.invoke(socket, host)
                } catch (e: Exception) {
                    // Ignore
                }
            }

            socket.startHandshake()
            val certs = socket.session.peerCertificates
            if (certs.isNotEmpty()) {
                val cert = certs[0]
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(cert.encoded)
                return Base64.encodeToString(digest, Base64.NO_WRAP)
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
