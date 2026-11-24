package net.iecxiv.calendarimage

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var loading: LinearLayout
    private val imageUrl = "http://192.168.1.28:8080/calendar.jpg"
    private val imageFileName = "calendar.jpg"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setFinishOnTouchOutside(true)

        findViewById<FrameLayout>(R.id.rootLayout).setOnClickListener {
            finish()
        }

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
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { loading.visibility = View.VISIBLE }
            override fun onPageFinished(view: WebView, url: String) { loading.visibility = View.GONE }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
        loadApp()
    }

    private fun loadApp() {
        val local = File(filesDir, imageFileName)
        if (local.exists()) {
            displayImage(local)
            scope.launch(Dispatchers.IO) {
                checkForUpdates(local)
            }
        } else {
            loading.visibility = View.VISIBLE
            scope.launch {
                withContext(Dispatchers.IO) {
                    downloadFile(local)
                }
                displayImage(local)
            }
        }
    }

    private fun displayImage(file: File) {
        val html = assets.open("index.html").bufferedReader().use { it.readText() }
        val imageUri = file.toURI().toString()
        val htmlWithImage = html.replace("calendar.jpg", imageUri)
        webView.loadDataWithBaseURL("file:///android_asset/", htmlWithImage, "text/html", "UTF-8", null)
    }

    private fun downloadFile(target: File) {
        val connection = try {
            (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Cache-Control", "no-cache")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }
        try {
            val temp = File(cacheDir, "calendar.jpg.tmp")
            connection.connect()
            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                temp.copyTo(target, overwrite = true)
                if (temp.exists()) temp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun checkForUpdates(local: File) {
        val temp = File(cacheDir, "calendar.jpg.tmp")
        val hasNewVersion = withContext(Dispatchers.IO) {
            val connection = try {
                (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("Cache-Control", "no-cache")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }

            try {
                connection.connect()
                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val localChecksum = getFileChecksum(local)
                    val serverChecksum = getFileChecksum(temp)
                    serverChecksum != null && serverChecksum != localChecksum
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (temp.exists()) temp.delete()
                false
            } finally {
                connection.disconnect()
            }
        }

        if (hasNewVersion) {
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Calendario actualizado disponible")
                        .setMessage("Hay una nueva versión del calendario disponible en el servidor. ¿Desea recargar?")
                        .setPositiveButton("Recargar") { _, _ ->
                            scope.launch(Dispatchers.IO) {
                                if (temp.exists()) {
                                    temp.copyTo(local, overwrite = true)
                                    temp.delete()
                                }
                                withContext(Dispatchers.Main) {
                                    displayImage(local)
                                }
                            }
                        }
                        .setNegativeButton("Más tarde") { _, _ ->
                            if (temp.exists()) temp.delete()
                        }
                        .setOnCancelListener {
                            if (temp.exists()) temp.delete()
                        }
                        .show()
                } else {
                    if (temp.exists()) temp.delete()
                }
            }
        } else {
            if (temp.exists()) temp.delete()
        }
    }

    private fun getFileChecksum(file: File): String? {
        if (!file.exists()) return null
        return try {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() { scope.cancel(); webView.destroy(); super.onDestroy() }
}
