package dev.kurodaki.plimb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/**
 * Gestionnaire d extensions : liste des add-ons installes, activation,
 * suppression et installation depuis un fichier .xpi ou une URL.
 */
class ExtensionsActivity : AppCompatActivity() {

    private lateinit var adapter: ExtensionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extensions)

        val toolbar: MaterialToolbar = findViewById(R.id.extensions_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recycler: RecyclerView = findViewById(R.id.extensions_recycler)
        val storeButton: MaterialButton = findViewById(R.id.extensions_store)
        val installFileButton: MaterialButton = findViewById(R.id.extensions_install_file)
        val installUrlButton: MaterialButton = findViewById(R.id.extensions_install_url)

        adapter = ExtensionsAdapter(
            onToggle = { entry, enabled ->
                AddonManager.setEnabled(PlimbApp.runtime ?: return@ExtensionsAdapter, entry, enabled)
                refresh()
            },
            onRemove = { entry -> confirmRemove(entry) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        storeButton.setOnClickListener {
            startActivity(Intent(this, AddonStoreActivity::class.java))
        }
        installFileButton.setOnClickListener { pickXpi() }
        installUrlButton.setOnClickListener { promptUrlInstall() }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val runtime = PlimbApp.runtime ?: return
        AddonManager.list(runtime).accept(
            { entries ->
                adapter.submit(entries.sortedWith(
                    compareByDescending<AddonEntry> { it.builtIn }.thenBy { it.name.lowercase() }
                ))
            },
            { error ->
                Toast.makeText(this, R.string.ext_unavailable, Toast.LENGTH_SHORT).show()
                android.util.Log.w("Plimb", "Liste des extensions indisponible", error)
            }
        )
    }

    private fun pickXpi() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        filePicker.launch(intent)
    }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            installFromUri(uri)
        }

    private fun installFromUri(uri: android.net.Uri) {
        // GeckoView accepte les URI file: — on copie donc l .xpi choisi dans le cache.
        val runtime = PlimbApp.runtime ?: return
        val target = java.io.File(cacheDir, "addon-" + System.currentTimeMillis() + ".xpi")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            toast(getString(R.string.ext_install_error))
            return
        }
        AddonManager.install(runtime, "file://${target.absolutePath}").accept(
            { extension ->
                toast(getString(R.string.ext_install_ok, extension.metaData?.name ?: extension.id))
                refresh()
            },
            { error ->
                toast(getString(R.string.ext_install_error))
                android.util.Log.w("Plimb", "Installation .xpi impossible", error)
            }
        )
    }

    private fun promptUrlInstall() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.ext_url_hint)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setPadding(48, 32, 48, 8)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.ext_install_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ext_install_action) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) installFromUrl(url)
            }
            .show()
    }

    private fun installFromUrl(url: String) {
        val runtime = PlimbApp.runtime ?: return
        if (!url.startsWith("https://")) {
            toast(getString(R.string.ext_install_error))
            return
        }
        AddonManager.install(runtime, url).accept(
            { extension ->
                toast(getString(R.string.ext_install_ok, extension.metaData?.name ?: extension.id))
                refresh()
            },
            { error ->
                toast(getString(R.string.ext_install_error))
                android.util.Log.w("Plimb", "Installation distante impossible", error)
            }
        )
    }

    private fun confirmRemove(entry: AddonEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.ext_delete)
            .setMessage(getString(R.string.ext_delete_confirm, entry.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AddonManager.uninstall(PlimbApp.runtime ?: return@setPositiveButton, entry)
                refresh()
            }
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
