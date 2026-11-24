package iecxiv.workcalendarhttp

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
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
    private val imageFileName = "calendar.jpg"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { getSharedPreferences("calendar_settings", MODE_PRIVATE) }
    private val viewStatePrefs by lazy { getSharedPreferences("calendar_view_state", MODE_PRIVATE) }
    private var updateDialog: AlertDialog? = null
    private var pendingImage: File? = null

    private inner class CalendarViewStateBridge {
        @JavascriptInterface
        fun getState(): String = viewStatePrefs.getString("transform", "") ?: ""

        @JavascriptInterface
        fun saveState(json: String) {
            if (json.length > 2048) return
            try {
                val state = JSONObject(json)
                val matrix = state.getJSONArray("A")
                val translation = state.getJSONArray("b")
                if (matrix.length() != 2 || translation.length() != 2) return
                for (row in 0..1) {
                    val values = matrix.getJSONArray(row)
                    if (values.length() != 2) return
                    for (column in 0..1) {
                        if (!values.getDouble(column).isFinite()) return
                    }
                    if (!translation.getDouble(row).isFinite()) return
                }
                viewStatePrefs.edit().putString("transform", json).apply()
            } catch (_: Exception) {
                // Ignore invalid view state.
            }
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
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.addJavascriptInterface(CalendarViewStateBridge(), "CalendarViewState")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loading.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView, url: String) {
                loading.visibility = View.GONE
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        val url = settings.getString("image_url", null)
        if (url.isNullOrBlank()) showUrlDialog() else loadApp(url)
    }

    private fun validUrl(value: String): Boolean = try {
        val url = URL(value)
        val uri = url.toURI()
        (url.protocol.equals("http", true) || url.protocol.equals("https", true)) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port in 1..65535)
    } catch (_: Exception) { false }

    private fun showUrlDialog(current: String = "") {
        loading.visibility = View.GONE
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.image_url_hint)
            setSingleLine(true)
            setText(current)
            setSelectAllOnFocus(true)
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.image_url_title)
            .setMessage(R.string.image_url_message)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim()
                if (!validUrl(url)) {
                    input.error = getString(R.string.image_url_invalid)
                } else {
                    dialog.dismiss()
                    loadApp(url)
                }
            }
        }
        dialog.show()
    }

    private fun loadApp(url: String) {
        val local = File(filesDir, imageFileName)
        val configured = settings.getString("image_url", null) == url
        if (configured && local.exists()) {
            displayImage(local)
            scope.launch { checkForUpdates(url, local) }
            return
        }
        loading.visibility = View.VISIBLE
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val temp = downloadImage(url) ?: return@withContext false
                try { temp.renameTo(local) } finally { temp.delete() }
            }
            if (isFinishing || isDestroyed) return@launch
            if (saved) {
                settings.edit().putString("image_url", url).apply()
                displayImage(local)
            } else {
                showDownloadError(url)
            }
        }
    }

    private fun showDownloadError(url: String) {
        loading.visibility = View.GONE
        AlertDialog.Builder(this)
            .setTitle(R.string.download_error_title)
            .setMessage(R.string.download_error_message)
            .setPositiveButton(R.string.retry) { _, _ -> loadApp(url) }
            .setNeutralButton(R.string.edit_url) { _, _ -> showUrlDialog(url) }
            .setNegativeButton(R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun displayImage(file: File) {
        val html = assets.open("index.html").bufferedReader().use { it.readText() }
        val htmlWithImage = html.replace("calendar.jpg", file.toURI().toString())
        webView.loadDataWithBaseURL("file:///android_asset/", htmlWithImage, "text/html", "UTF-8", null)
    }

    // Download beside the destination so rename can atomically replace the old image.
    private fun downloadImage(url: String): File? {
        var connection: HttpURLConnection? = null
        var temp: File? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 10000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val downloaded = File.createTempFile("calendar-", ".tmp", filesDir)
            temp = downloaded
            connection.inputStream.use { input ->
                downloaded.outputStream().use { output -> input.copyTo(output) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(downloaded.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            temp = null
            return downloaded
        } catch (_: Exception) {
            return null
        } finally {
            connection?.disconnect()
            temp?.delete()
        }
    }

    private suspend fun checkForUpdates(url: String, local: File) {
        var temp: File? = null
        try {
            withContext(Dispatchers.IO) { temp = downloadImage(url) }
            val downloaded = temp ?: return
            val changed = withContext(Dispatchers.IO) {
                val checksum = getFileChecksum(downloaded)
                checksum != null && checksum != getFileChecksum(local)
            }
            if (!changed || isFinishing || isDestroyed) return
            pendingImage = downloaded
            updateDialog = AlertDialog.Builder(this)
                .setTitle(R.string.update_title)
                .setMessage(R.string.update_message)
                .setPositiveButton(R.string.reload) { _, _ ->
                    pendingImage = null
                    scope.launch {
                        try {
                            val replaced = withContext(Dispatchers.IO) { downloaded.renameTo(local) }
                            if (!isFinishing && !isDestroyed) {
                                if (replaced) displayImage(local)
                                else Toast.makeText(this@MainActivity, R.string.replace_error, Toast.LENGTH_LONG).show()
                            }
                        } finally { downloaded.delete() }
                    }
                }
                .setNegativeButton(R.string.later, null)
                .create().also { dialog ->
                    dialog.setOnDismissListener {
                        pendingImage?.delete()
                        pendingImage = null
                        updateDialog = null
                    }
                    dialog.show()
                }
            temp = null
        } finally { temp?.delete() }
    }

    private fun getFileChecksum(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    override fun onDestroy() {
        scope.cancel()
        updateDialog?.dismiss()
        pendingImage?.delete()
        webView.removeJavascriptInterface("CalendarViewState")
        webView.destroy()
        super.onDestroy()
    }
}
