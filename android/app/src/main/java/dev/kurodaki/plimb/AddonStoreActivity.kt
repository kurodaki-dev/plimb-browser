package dev.kurodaki.plimb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime

/**
 * Catalogue d add-ons : recherche dans addons.mozilla.org et installation directe.
 * Seuls les add-ons signes par Mozilla peuvent etre installes (contrainte de GeckoView).
 */
class AddonStoreActivity : AppCompatActivity() {

    private lateinit var adapter: AddonStoreAdapter
    private var runtime: GeckoRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addon_store)
        runtime = PlimbApp.runtime

        val toolbar: MaterialToolbar = findViewById(R.id.store_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val searchInput: EditText = findViewById(R.id.store_search)
        val searchButton: MaterialButton = findViewById(R.id.store_search_button)
        val status: TextView = findViewById(R.id.store_status)
        val recycler: RecyclerView = findViewById(R.id.store_recycler)

        adapter = AddonStoreAdapter { listing -> install(listing) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val runSearch = {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) search(query)
        }
        searchButton.setOnClickListener { runSearch() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
                runSearch(); true
            } else false
        }

        search("ad blocker")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun search(query: String) {
        val status: TextView = findViewById(R.id.store_status)
        status.text = getString(R.string.store_searching, query)
        CoroutineScope(Dispatchers.Main).launch {
            val listings = withContext(Dispatchers.IO) { AmoClient.search(query) }
            adapter.submit(listings)
            status.text =
                if (listings.isEmpty()) getString(R.string.store_no_result, query)
                else getString(R.string.store_result_count, listings.size)
        }
    }

    private fun install(listing: AddonListing) {
        val engine = runtime ?: return
        AddonManager.install(engine, listing.downloadUrl).accept(
            { extension ->
                Toast.makeText(
                    this,
                    getString(R.string.ext_install_ok, extension.metaData?.name ?: listing.name),
                    Toast.LENGTH_LONG
                ).show()
            },
            { error ->
                Toast.makeText(this, R.string.ext_install_error, Toast.LENGTH_LONG).show()
                android.util.Log.w("Plimb", "Installation AMO impossible", error)
            }
        )
    }
}
