package com.localaisearch.data.repository

import android.content.Context
import android.content.Intent
import android.content.ComponentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Embedded Tor controller used by the optional app-only Tor route.
 *
 * Tor itself is provided by Guardian Project's tor-android library.  The app
 * writes a small torrc before starting the service, including user-supplied
 * Bridge lines when present, then routes OkHttp traffic through localhost:9050.
 */
object TorManager {
    const val DEFAULT_SOCKS_PORT = 9050
    @Volatile private var activeSocksPort: Int = DEFAULT_SOCKS_PORT

    enum class Status { OFF, STARTING, ON, ERROR }

    private val _statusFlow = MutableStateFlow(Status.OFF)
    val statusFlow: StateFlow<Status> = _statusFlow.asStateFlow()

    @Volatile
    var status: Status = Status.OFF
        private set(value) {
            field = value
            _statusFlow.value = value
        }

    @Volatile
    var lastError: String? = null
        private set

    private var appContext: Context? = null

    private const val TOR_SERVICE_CLASS = "org.torproject.jni.TorService"
    private const val ACTION_START = "org.torproject.android.intent.action.START"
    private const val ACTION_STOP = "org.torproject.android.intent.action.STOP"
    private const val EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME"

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun start(bridgeLines: String): Result<Unit> = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext Result.failure(IllegalStateException("Tor manager is not initialized"))
        status = Status.STARTING
        lastError = null

        try {
            val torrc = getTorrc(context)
            torrc.parentFile?.mkdirs()
            activeSocksPort = findFreeSocksPort()
            writeTorrc(torrc, bridgeLines, activeSocksPort)

            // Use the public TorService action recommended by tor-android.
            // This avoids relying on implementation details of the embedded Service.
            runCatching {
                context.startService(torIntent(context, ACTION_STOP))
            }
            delay(400)
            context.startService(torIntent(context, ACTION_START))

            // Fail fast instead of leaving the UI in an indefinite connecting state.
            val ready = waitForSocks(activeSocksPort, 30_000L)
            if (!ready) {
                status = Status.ERROR
                lastError = "Tor did not become ready within 30 seconds. Check the bridge configuration or network connection."
                return@withContext Result.failure(IllegalStateException(lastError))
            }

            NetworkClientFactory.updateProxy(
                ProxyConfig(enabled = true, type = "SOCKS", host = "127.0.0.1", port = activeSocksPort)
            )
            status = Status.ON
            Result.success(Unit)
        } catch (e: Exception) {
            status = Status.ERROR
            lastError = e.message ?: "Unable to start Tor"
            Result.failure(e)
        }
    }

    fun stop() {
        val context = appContext ?: return
        runCatching {
            context.startService(torIntent(context, ACTION_STOP))
        }
        runCatching { context.stopService(torIntent(context, ACTION_STOP)) }
        if (NetworkClientFactory.currentProxy().host == "127.0.0.1" &&
            NetworkClientFactory.currentProxy().port == activeSocksPort &&
            NetworkClientFactory.currentProxy().type.equals("SOCKS", ignoreCase = true)
        ) {
            NetworkClientFactory.updateProxy(ProxyConfig())
        }
        status = Status.OFF
        lastError = null
    }


    private fun torIntent(context: Context, action: String): Intent =
        Intent(action).apply {
            component = ComponentName(context.packageName, TOR_SERVICE_CLASS)
            putExtra(EXTRA_PACKAGE_NAME, context.packageName)
        }

    private fun getTorrc(context: Context): File {
        return try {
            val clazz = Class.forName(TOR_SERVICE_CLASS)
            val method = clazz.getMethod("getTorrc", Context::class.java)
            (method.invoke(null, context) as? File)
                ?: File(context.filesDir, "tor/torrc")
        } catch (_: Throwable) {
            File(context.filesDir, "tor/torrc")
        }
    }

    private fun writeTorrc(file: File, bridgeLines: String, socksPort: Int) {
        val clean = bridgeLines.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .joinToString("\n")
        val content = buildString {
            append("SocksPort 127.0.0.1:").append(socksPort).append('\n')
            append("AvoidDiskWrites 1\n")
            append("SafeLogging 1\n")
            if (clean.isNotBlank()) {
                append("UseBridges 1\n")
                clean.lines().forEach { line ->
                    // Keep bridge configuration user-supplied; never invent bridge credentials.
                    if (line.startsWith("Bridge ")) append(line).append('\n')
                    else append("Bridge ").append(line).append('\n')
                }
            }
        }
        file.writeText(content)
    }

    private fun findFreeSocksPort(): Int {
        for (port in DEFAULT_SOCKS_PORT..(DEFAULT_SOCKS_PORT + 10)) {
            val inUse = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 120)
                    true
                }
            } catch (_: Exception) {
                false
            }
            if (!inUse) return port
        }
        return DEFAULT_SOCKS_PORT
    }

    private fun waitForSocks(port: Int, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 800)
                }
                return true
            } catch (_: Exception) {
                Thread.sleep(350)
            }
        }
        return false
    }
}
