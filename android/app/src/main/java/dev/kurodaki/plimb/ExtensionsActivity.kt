package dev.kurodaki.plimb

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Ecran de gestion des extensions (activees / desactivees / installees). */
class ExtensionsActivity : AppCompatActivity() {

    private lateinit var adapter: ExtensionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extensions)

        val toolbar: MaterialToolbar = findViewById(R.id.extensions_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recycler: RecyclerView = findViewById(R.id.extensions_recycler)
        val installButton: MaterialButton = findViewById(R.id.extensions_install)
        val refreshButton: MaterialButton = findViewById(R.id.extensions_refresh)

        adapter = ExtensionsAdapter(
            onToggle = { extension, enabled ->
                extension.setEnabled(this, enabled)
            },
            onDelete = { extension ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.ext_delete)
                    .setMessage(getString(R.string.ext_delete_confirm, extension.name))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Extensions.removeUser(this, extension.id)
                        refresh()
                        snack(getString(R.string.ext_deleted, extension.name))
                    }
                    .show()
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        installButton.setOnClickListener { promptInstall() }
        refreshButton.setOnClickListener { Extensions.reset(); refresh() }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        adapter.submit(Extensions.all(this))
    }

    private fun snack(message: String) {
        com.google.android.material.snackbar.Snackbar
            .make(findViewById(android.R.id.content), message,
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
    }

    private fun promptInstall() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.ext_name_hint)
        }
        val urlInput = EditText(this).apply {
            hint = getString(R.string.ext_url_hint)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(nameInput)
        layout.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle(R.string.ext_install_title)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ext_install_action) { _, _ ->
                val name = nameInput.text.toString().trim()
                val raw = urlInput.text.toString().trim()
                if (raw.isNotEmpty()) installScript(name, raw)
            }
            .show()
    }

    private fun installScript(name: String, url: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 15000
                        instanceFollowRedirects = true
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    body
                }
            }
            result.onSuccess { script ->
                if (script.isBlank()) {
                    snack(getString(R.string.ext_install_error))
                    return@onSuccess
                }
                val fallbackId = (url.substringAfterLast('/').substringBefore('.')).ifBlank {
                    "script-${System.currentTimeMillis()}"
                }
                val id = fallbackId.lowercase().replace(Regex("[^a-z0-9_-]"), "-")
                val extension = Extensions.installUserScript(
                    context = this@ExtensionsActivity,
                    id = id,
                    name = name,
                    source = url,
                    script = script
                )
                if (extension == null) {
                    snack(getString(R.string.ext_install_error))
                } else {
                    refresh()
                    snack(getString(R.string.ext_install_ok, extension.name))
                }
            }.onFailure {
                snack(getString(R.string.ext_install_error))
            }
        }
    }
}
