package com.localaisearch.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.IntentFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
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
    private val lifecycleGeneration = AtomicLong(0L)
    @Volatile private var circuitEstablished = false

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
    private const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
    private const val ACTION_ERROR = "org.torproject.android.intent.action.ERROR"
    private const val EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME"
    private const val EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS"

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun start(bridgeLines: String): Result<Unit> = withContext(Dispatchers.IO) {
        val context = appContext ?: return@withContext Result.failure(IllegalStateException("Tor manager is not initialized"))

        // Idempotent start: do not launch multiple TorService instances.
        if (status == Status.ON) return@withContext Result.success(Unit)
        if (status == Status.STARTING) {
            return@withContext Result.failure(IllegalStateException("Tor is already starting"))
        }

        val generation = lifecycleGeneration.incrementAndGet()
        status = Status.STARTING
        lastError = null
        circuitEstablished = false

        try {
            val torrc = getTorrc(context)
            torrc.parentFile?.mkdirs()
            activeSocksPort = findFreeSocksPort()
            writeTorrc(torrc, bridgeLines, activeSocksPort)

            // Register before START so the official TorService STATUS=ON broadcast
            // cannot be missed. A listening SOCKS socket alone is NOT proof that
            // Tor has completed bootstrapping a circuit.
            val readyReceiver = createReadyReceiver(generation)
            ContextCompat.registerReceiver(
                context,
                readyReceiver,
                IntentFilter(ACTION_STATUS).apply { addAction(ACTION_ERROR) },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            try {
                runCatching { context.startService(torIntent(context, ACTION_STOP)) }
                delay(500)
                if (lifecycleGeneration.get() != generation || status != Status.STARTING) {
                    return@withContext Result.failure(IllegalStateException("Tor start was cancelled"))
                }
                context.startService(torIntent(context, ACTION_START))

                val ready = awaitCircuitReady(context, readyReceiver, generation, 45_000L)
                if (!ready) {
                    if (lifecycleGeneration.get() == generation) {
                        status = Status.ERROR
                        lastError = "Tor did not establish a circuit within 45 seconds. Check the network or bridge configuration."
                    }
                    return@withContext Result.failure(IllegalStateException(lastError ?: "Tor start cancelled"))
                }

                if (lifecycleGeneration.get() != generation || status != Status.STARTING) {
                    return@withContext Result.failure(IllegalStateException("Tor start was cancelled"))
                }

                // Only after Tor reports a completed circuit do we route app traffic.
                NetworkClientFactory.updateProxy(
                    ProxyConfig(enabled = true, type = "SOCKS", host = "127.0.0.1", port = activeSocksPort)
                )
                status = Status.ON
                Result.success(Unit)
            } finally {
                runCatching { context.unregisterReceiver(readyReceiver) }
            }
        } catch (e: Exception) {
            if (lifecycleGeneration.get() == generation) {
                status = Status.ERROR
                lastError = e.message ?: "Unable to start Tor"
            }
            Result.failure(e)
        }
    }

    private fun createReadyReceiver(generation: Long): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (lifecycleGeneration.get() != generation) return
            if (intent.action == ACTION_ERROR) {
                val message = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!message.isNullOrBlank()) lastError = message
                status = Status.ERROR
                return
            }
            if (intent.action == ACTION_STATUS) {
                when (intent.getStringExtra(EXTRA_STATUS)) {
                    "ON" -> circuitEstablished = true
                    "OFF", "STOPPING" -> if (status == Status.STARTING) lastError = "Tor service stopped before a circuit was established"
                }
            }
        }
    }

    private suspend fun awaitCircuitReady(
        context: Context,
        receiver: BroadcastReceiver,
        generation: Long,
        timeoutMs: Long
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val deadline = System.currentTimeMillis() + timeoutMs
        val waiter = Thread {
            while (System.currentTimeMillis() < deadline && !continuation.isCompleted) {
                if (lifecycleGeneration.get() != generation) {
                    continuation.resume(false)
                    return@Thread
                }
                // The service's STATUS=ON is emitted only after CIRCUIT_ESTABLISHED.
                // Also verify the local SOCKS listener before considering routing ready.
                if (status == Status.ERROR) {
                    continuation.resume(false)
                    return@Thread
                }
                try { Thread.sleep(200) } catch (_: InterruptedException) { return@Thread }
            }
            val ready = circuitEstablished && isPortOpen(activeSocksPort, 1000) && lifecycleGeneration.get() == generation && status == Status.STARTING
            if (!ready && lastError == null && lifecycleGeneration.get() == generation) {
                lastError = "Tor circuit was not established before timeout"
            }
            continuation.resume(ready)
        }.apply { isDaemon = true }
        waiter.start()
        continuation.invokeOnCancellation { waiter.interrupt() }
    }

    fun stop() {
        val context = appContext ?: return
        lifecycleGeneration.incrementAndGet()
        circuitEstablished = false
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
                    // Accept both a complete torrc line and a bare bridge descriptor.
                    // Do not turn ClientTransportPlugin/UseBridges directives into
                    // invalid `Bridge ...` lines.
                    when {
                        line.startsWith("Bridge ") ||
                            line.startsWith("ClientTransportPlugin ") ||
                            line.startsWith("UseBridges ") -> append(line).append('\n')
                        else -> append("Bridge ").append(line).append('\n')
                    }
                }
            }
        }
        file.writeText(content)
    }

    private fun findFreeSocksPort(): Int {
        for (port in DEFAULT_SOCKS_PORT..(DEFAULT_SOCKS_PORT + 10)) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 120)
                    // A successful connect means another process owns the port.
                    continue
                }
            } catch (_: Exception) {
                return port
            }
        }
        return DEFAULT_SOCKS_PORT
    }

    private fun isPortOpen(port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
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
