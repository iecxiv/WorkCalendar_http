package iecxiv.calendar

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.InputType
import android.util.AtomicFile
import android.util.Base64
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class CalendarConfig(
    val url: String = "",
    val basic: Boolean = false,
    val user: String = "",
    val password: String = "",
    val useHeaders: Boolean = false,
    val headers: List<Pair<String, String>> = emptyList(),
    val remember: Boolean = true
)

internal class CalendarSettings(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "calendar-config.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey("calendar_config_v1", null)
        if (existing != null) return existing as SecretKey
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("calendar_config_v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun read(): CalendarConfig {
        val bytes = try { file.readFully() } catch (_: java.io.FileNotFoundException) {
            return CalendarConfig(url = context.getSharedPreferences("calendar_settings", Context.MODE_PRIVATE)
                .getString("image_url", "") ?: "")
        }
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
            Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(
            envelope.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
        val headers = json.optJSONArray("headers") ?: JSONArray()
        return CalendarConfig(json.getString("url"), json.optBoolean("basic"),
            json.optString("user"), json.optString("password"), json.optBoolean("useHeaders"),
            (0 until headers.length()).map {
                val row = headers.getJSONObject(it)
                row.getString("name") to row.getString("value")
            }, json.optBoolean("remember", true))
    }

    fun write(config: CalendarConfig) {
        val headers = JSONArray().apply { config.headers.forEach { (name, value) ->
            put(JSONObject().put("name", name).put("value", value))
        } }
        val json = JSONObject().put("url", config.url).put("basic", config.basic)
            .put("user", config.user).put("password", config.password)
            .put("useHeaders", config.useHeaders).put("headers", headers)
            .put("remember", config.remember)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        val output = file.startWrite()
        try {
            output.write(envelope.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun show(current: CalendarConfig, firstRun: Boolean, onCancel: () -> Unit,
             onSave: (CalendarConfig) -> Unit): AlertDialog {
        val builder = AlertDialog.Builder(this.context)
        val context = builder.context
        fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(12))
        }
        fun section(label: Int) {
            column.addView(AppCompatTextView(context).apply {
                setText(label)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(if (column.childCount == 0) 4 else 16)
                    bottomMargin = dp(8)
                }
            })
        }
        fun field(label: Int, value: String, secret: Boolean = false): AppCompatEditText {
            val caption = AppCompatTextView(context).apply {
                setText(label)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
            }
            column.addView(caption)
            return AppCompatEditText(context).apply {
                id = android.view.View.generateViewId()
                caption.labelFor = id
                inputType = InputType.TYPE_CLASS_TEXT or if (secret)
                    InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                setText(value)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                column.addView(this)
            }
        }
        section(R.string.settings_section_image)
        val url = field(R.string.image_url_title, current.url)
        section(R.string.settings_section_auth)
        val basic = AppCompatCheckBox(context).apply {
            minHeight = dp(48)
            setText(R.string.settings_basic); isChecked = current.basic; column.addView(this)
        }
        val user = field(R.string.settings_user, current.user)
        val password = field(R.string.settings_password, current.password, true)
        fun updateBasic() { user.isEnabled = basic.isChecked; password.isEnabled = basic.isChecked }
        basic.setOnCheckedChangeListener { _, _ -> updateBasic() }; updateBasic()
        section(R.string.settings_section_headers)
        val enabledHeaders = AppCompatCheckBox(context).apply {
            minHeight = dp(48)
            setText(R.string.settings_headers); isChecked = current.useHeaders; column.addView(this)
        }
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; column.addView(this) }
        val inputs = mutableListOf<Pair<AppCompatEditText, AppCompatEditText>>()
        fun addHeader(name: String = "", value: String = "") {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            }
            val nameInput = AppCompatEditText(context).apply {
                hint = context.getString(R.string.settings_header_name); setSingleLine(true); setText(name)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
            }
            val valueInput = AppCompatEditText(context).apply {
                hint = context.getString(R.string.settings_header_value); setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(value)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
            }
            val pair = nameInput to valueInput
            inputs.add(pair)
            row.addView(nameInput); row.addView(valueInput)
            row.addView(AppCompatButton(context).apply {
                setText(R.string.settings_remove)
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.END }
                setOnClickListener { inputs.remove(pair); rows.removeView(row) }
            })
            rows.addView(row)
        }
        current.headers.forEach { (name, value) -> addHeader(name, value) }
        val add = AppCompatButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
            setText(R.string.settings_add_header); setOnClickListener { addHeader() }; column.addView(this)
        }
        fun updateHeaders() {
            rows.visibility = if (enabledHeaders.isChecked) android.view.View.VISIBLE else android.view.View.GONE
            add.visibility = rows.visibility
        }
        enabledHeaders.setOnCheckedChangeListener { _, _ -> updateHeaders() }; updateHeaders()
        section(R.string.settings_section_view)
        val remember = AppCompatCheckBox(context).apply {
            minHeight = dp(48)
            setText(R.string.settings_remember); isChecked = current.remember; column.addView(this)
        }
        column.addView(AppCompatTextView(context).apply {
            setText(R.string.settings_security)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        })
        val dialog = builder.setTitle(R.string.settings_title)
            .setView(ScrollView(context).apply { addView(column) })
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setCancelable(!firstRun).create()
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = url.text.toString().trim()
                val valid = try {
                    val parsed = URL(address).toURI()
                    (parsed.scheme.equals("http", true) || parsed.scheme.equals("https", true)) &&
                        !parsed.host.isNullOrBlank() && parsed.rawUserInfo == null &&
                        (parsed.port == -1 || parsed.port in 1..65535)
                } catch (_: Exception) { false }
                if (!valid) { url.error = context.getString(R.string.image_url_invalid); return@setOnClickListener }
                val username = user.text.toString()
                if (basic.isChecked && (username.contains(':') || username.any { it == '\r' || it == '\n' })) {
                    user.error = context.getString(R.string.settings_invalid_user); return@setOnClickListener
                }
                val headers = inputs.map { it.first.text.toString().trim() to it.second.text.toString() }
                    .filterNot { it.first.isEmpty() && it.second.isEmpty() }
                val names = mutableSetOf<String>()
                if (enabledHeaders.isChecked && headers.any { (name, value) ->
                    !name.matches(Regex("[A-Za-z0-9_-]+")) || value.any { it.code < 32 || it.code == 127 } ||
                        !names.add(name.lowercase(java.util.Locale.ROOT)) ||
                        name.lowercase(java.util.Locale.ROOT) in setOf("host", "content-length", "connection", "transfer-encoding") ||
                        (basic.isChecked && name.equals("Authorization", true))
                }) {
                    Toast.makeText(context, R.string.settings_invalid_headers, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val config = CalendarConfig(address, basic.isChecked, username, password.text.toString(),
                    enabledHeaders.isChecked, headers, remember.isChecked)
                try { write(config) } catch (_: Exception) {
                    Toast.makeText(context, R.string.settings_storage_error, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss(); onSave(config)
            }
        }
        dialog.show()
        return dialog
    }
}
