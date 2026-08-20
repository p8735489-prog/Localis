package com.localaisearch.data.repository

import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class ProxyConfig(
    val enabled: Boolean = false,
    val type: String = "HTTP",
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
) {
    val isValid: Boolean
        get() = !enabled || (host.isNotBlank() && port in 1..65535)
}

/**
 * App-wide network policy. Existing OkHttp clients also see proxy changes because
 * the ProxySelector reads the atomic config for every new route.
 */
object NetworkClientFactory {
    private val proxyRef = AtomicReference(ProxyConfig())

    private val dynamicProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val config = proxyRef.get()
            if (!config.enabled || !config.isValid) return listOf(Proxy.NO_PROXY)
            val type = if (config.type.equals("SOCKS", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            return listOf(Proxy(type, InetSocketAddress(config.host.trim(), config.port)))
        }
        override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) { }
    }

    fun updateProxy(config: ProxyConfig) {
        proxyRef.set(if (config.isValid) config else ProxyConfig())
    }

    fun currentProxy(): ProxyConfig = proxyRef.get()

    fun builder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Do not keep idle direct connections alive across a Tor/proxy switch.
        // Every new request must re-run the ProxySelector so enabling Tor cannot
        // silently reuse an already-open direct connection.
        .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
        .proxySelector(dynamicProxySelector)
        .authenticator(object : Authenticator {
            override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
                val config = proxyRef.get()
                if (!config.enabled || !config.isValid || config.username.isBlank()) return null
                if (response.request.header("Proxy-Authorization") != null) return null
                return response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(config.username, config.password))
                    .build()
            }
        })

    fun build(): OkHttpClient = builder().build()
}
