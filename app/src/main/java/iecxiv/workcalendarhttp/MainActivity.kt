package iecxiv.workcalendarhttp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loading: LinearLayout
    private lateinit var preferences: CalendarSettings
    @Volatile private var config = CalendarConfig()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var networkJob: Job? = null
    private val settings by lazy { getSharedPreferences("calendar_settings", MODE_PRIVATE) }
    private val viewStatePrefs by lazy { getSharedPreferences("calendar_view_state", MODE_PRIVATE) }
    private var settingsDialog: AlertDialog? = null
    private var updateDialog: AlertDialog? = null
    private var pendingImage: File? = null
    private var cornerMask = 0
    private var cornerStarted = 0L
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var tapCandidate = false

    private inner class CalendarViewStateBridge {
        @JavascriptInterface
        fun getState(): String = if (config.remember) viewStatePrefs.getString("transform", "") ?: "" else ""
        @JavascriptInterface
        fun saveState(json: String) {
            if (!config.remember || json.length > 2048) return
            try {
                val state = JSONObject(json)
                val matrix = state.getJSONArray("A")
                val translation = state.getJSONArray("b")
                if (matrix.length() != 2 || translation.length() != 2) return
                for (row in 0..1) {
                    val values = matrix.getJSONArray(row)
                    if (values.length() != 2) return
                    for (column in 0..1) if (!values.getDouble(column).isFinite()) return
                    if (!translation.getDouble(row).isFinite()) return
                }
                synchronized(viewStatePrefs) {
                    if (config.remember) viewStatePrefs.edit().putString("transform", json).apply()
                }
            } catch (_: Exception) { }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setFinishOnTouchOutside(true)
        findViewById<FrameLayout>(R.id.rootLayout).setOnClickListener { finish() }
        webView = findViewById(R.id.webView)
        loading = findViewById(R.id.loadingOverlay)
        preferences = CalendarSettings(this)
        config = try { preferences.read() } catch (_: Exception) {
            Toast.makeText(this, R.string.settings_storage_error, Toast.LENGTH_LONG).show()
            CalendarConfig()
        }
        if (!settings.contains("cached_url") && config.url.isNotBlank() && File(filesDir, "calendar.jpg").exists()) {
            settings.edit().putString("cached_url", config.url).apply()
        }
        webView.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = true; allowContentAccess = true
            useWideViewPort = true; loadWithOverviewMode = true
        }
        webView.addJavascriptInterface(CalendarViewStateBridge(), "CalendarViewState")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { loading.visibility = View.VISIBLE }
            override fun onPageFinished(view: WebView, url: String) { loading.visibility = View.GONE }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        if (config.url.isBlank()) openSettings() else loadApp()
    }

    private fun openSettings() {
        if (isFinishing || isDestroyed || settingsDialog?.isShowing == true) return
        networkJob?.cancel()
        updateDialog?.dismiss()
        loading.visibility = View.GONE
        val firstRun = config.url.isBlank()
        settingsDialog = preferences.show(config, firstRun, {
            settingsDialog = null
            if (firstRun) finish() else loadApp()
        }) { next ->
            synchronized(viewStatePrefs) {
                config = next
                if (!next.remember) viewStatePrefs.edit().remove("transform").apply()
            }
            settingsDialog = null
            loadApp(force = true)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; downTime = SystemClock.elapsedRealtime()
                tapCandidate = settingsDialog?.isShowing != true && updateDialog?.isShowing != true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> tapCandidate = false
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop) tapCandidate = false
            }
            MotionEvent.ACTION_UP -> {
                val now = SystemClock.elapsedRealtime()
                if (tapCandidate && now - downTime <= 300 && ::webView.isInitialized) {
                    val rect = Rect()
                    if (webView.getGlobalVisibleRect(rect) && rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        val edge = minOf(48 * resources.displayMetrics.density, rect.width() / 4f, rect.height() / 4f)
                        val x = event.rawX - rect.left; val y = event.rawY - rect.top
                        val horizontal = when { x <= edge -> 0; x >= rect.width() - edge -> 1; else -> -1 }
                        val vertical = when { y <= edge -> 0; y >= rect.height() - edge -> 1; else -> -1 }
                        if (horizontal >= 0 && vertical >= 0) {
                            if (cornerMask == 0 || now - cornerStarted > 5000) { cornerMask = 0; cornerStarted = now }
                            cornerMask = cornerMask or (1 shl (vertical * 2 + horizontal))
                            if (cornerMask == 15) { cornerMask = 0; webView.post { openSettings() } }
                        }
                    }
                }
                tapCandidate = false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun displayImage(file: File) {
        val html = assets.open("index.html").bufferedReader().use { it.readText() }
        webView.loadDataWithBaseURL("file:///android_asset/", html.replace("calendar.jpg", file.toURI().toString()), "text/html", "UTF-8", null)
    }

    private fun loadApp(force: Boolean = false) {
        networkJob?.cancel(); updateDialog?.dismiss()
        val request = config
        val local = File(filesDir, "calendar.jpg")
        val cached = local.exists() && settings.getString("cached_url", null) == request.url
        if (cached) displayImage(local) else loading.visibility = View.VISIBLE
        networkJob = scope.launch {
            var temp: File? = null
            try {
                withContext(Dispatchers.IO) { temp = downloadImage(request) }
                val downloaded = temp
                if (downloaded == null) {
                    if (!cached || force) showDownloadError()
                    return@launch
                }
                if (isFinishing || isDestroyed) return@launch
                val changed = !cached || withContext(Dispatchers.IO) { checksum(downloaded) != checksum(local) }
                if (!cached || force) {
                    if (downloaded.renameTo(local)) {
                        settings.edit().putString("cached_url", request.url).apply()
                        displayImage(local)
                    } else showDownloadError()
                } else if (changed) {
                    pendingImage = downloaded
                    updateDialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.update_title).setMessage(R.string.update_message)
                        .setPositiveButton(R.string.reload) { _, _ ->
                            pendingImage = null
                            if (downloaded.renameTo(local)) displayImage(local)
                            else Toast.makeText(this@MainActivity, R.string.replace_error, Toast.LENGTH_LONG).show()
                            downloaded.delete()
                        }.setNegativeButton(R.string.later, null).create().also { dialog ->
                            dialog.setOnDismissListener { pendingImage?.delete(); pendingImage = null; updateDialog = null }
                            dialog.show()
                        }
                    temp = null
                }
            } finally { temp?.delete() }
        }
    }

    private fun showDownloadError() {
        if (isFinishing || isDestroyed) return
        loading.visibility = View.GONE
        AlertDialog.Builder(this).setTitle(R.string.download_error_title).setMessage(R.string.download_error_message)
            .setPositiveButton(R.string.retry) { _, _ -> loadApp(force = true) }
            .setNeutralButton(R.string.settings_title) { _, _ -> openSettings() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun downloadImage(request: CalendarConfig): File? {
        var temp: File? = null
        var connection: HttpURLConnection? = null
        try {
            val origin = URL(request.url)
            var current = origin
            var response: HttpURLConnection? = null
            for (redirect in 0..5) {
                val opened = (current.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false; connectTimeout = 5000; readTimeout = 10000
                    requestMethod = "GET"; useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                    if (request.useHeaders) request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (request.basic) {
                        val token = Base64.encodeToString((request.user + ":" + request.password).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        setRequestProperty("Authorization", "Basic " + token)
                    }
                }
                connection = opened
                val code = opened.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = opened.getHeaderField("Location") ?: return null
                    val next = URL(current, location)
                    if (!next.protocol.equals(origin.protocol, true) || !next.host.equals(origin.host, true) ||
                        next.defaultPort.let { if (next.port == -1) it else next.port } !=
                        origin.defaultPort.let { if (origin.port == -1) it else origin.port } || next.userInfo != null) return null
                    opened.disconnect(); connection = null; current = next
                } else {
                    if (code != HttpURLConnection.HTTP_OK) return null
                    response = opened; break
                }
            }
            val success = response ?: return null
            val downloaded = File.createTempFile("calendar-", ".tmp", filesDir)
            temp = downloaded
            success.inputStream.use { input ->
                downloaded.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    var count = input.read(buffer)
                    while (count != -1) {
                        total += count
                        if (total > 30L * 1024 * 1024) return null
                        output.write(buffer, 0, count); count = input.read(buffer)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(downloaded.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            temp = null; return downloaded
        } catch (_: Exception) { return null }
        finally { connection?.disconnect(); temp?.delete() }
    }

    private fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192); var count = input.read(buffer)
            while (count != -1) { digest.update(buffer, 0, count); count = input.read(buffer) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun onDestroy() {
        scope.cancel(); updateDialog?.dismiss(); settingsDialog?.dismiss(); pendingImage?.delete()
        webView.removeJavascriptInterface("CalendarViewState"); webView.destroy()
        super.onDestroy()
    }
}
