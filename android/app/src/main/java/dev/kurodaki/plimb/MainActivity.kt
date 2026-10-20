package dev.kurodaki.plimb

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Plimb Browser — ecran principal, base sur le moteur GeckoView (Firefox).
 *
 * Gere les onglets (une GeckoSession par onglet), la barre d adresse, les telechargements,
 * les permissions, le plein ecran et le gestionnaire d extensions (WebExtensions).
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME_PAGE = "file:///android_asset/home.html"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64; rv:141.0) Gecko/20100101 Firefox/141.0"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var urlBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var webContainer: FrameLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnNewTab: ImageButton
    private lateinit var btnTabs: TextView

    private val tabs = mutableListOf<PlimbTab>()
    private var currentIndex = -1

    private val prefs by lazy { Prefs.settings(this) }
    private val runtime: GeckoRuntime by lazy {
        PlimbApp.runtime ?: GeckoRuntime.create(
            this,
            org.mozilla.geckoview.GeckoRuntimeSettings.Builder().build()
        )
    }

    private var pendingFileCallback: ((Array<Uri>?) -> Unit)? = null
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    private val filePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingFileCallback ?: return@registerForActivityResult
            pendingFileCallback = null
            var uris: Array<Uri>? = null
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val clip = data?.clipData
                uris = when {
                    clip != null -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    data?.data != null -> arrayOf(data.data!!)
                    else -> null
                }
            }
            callback(uris)
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val callback = pendingPermissionCallback ?: return@registerForActivityResult
            pendingPermissionCallback = null
            callback(grants.values.all { it })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(R.layout.activity_main)
        bindViews()
        setSupportActionBar(toolbar)

        registerExtensionDelegates()
        AddonManager.installBuiltIns(runtime)
        setupControls()

        if (tabs.isEmpty()) restoreSession()
        handleIntent(intent)
        if (tabs.isEmpty()) openTab(HOME_PAGE) else selectTab(0)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBackOrExit()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        saveSession()
        currentTab()?.session?.setActive(false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        currentTab()?.session?.setActive(true)
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            tab.session.setActive(false)
            tab.session.close()
        }
        tabs.clear()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_desktop)?.isChecked =
            prefs.getBoolean(Prefs.KEY_DESKTOP, false)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                btnReload.performClick()
                true
            }
            R.id.action_share -> {
                share()
                true
            }
            R.id.action_extensions -> {
                openExtensions()
                true
            }
            R.id.action_block_ads -> {
                toggleBlocking()
                true
            }
            R.id.action_desktop -> {
                toggleDesktopMode()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_quit -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ------------------------------------------------------------------- UI

    private fun applyTheme() {
        val dark = prefs.getBoolean(Prefs.KEY_DARK, false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun bindViews() {
        toolbar = findViewById(R.id.toolbar)
        urlBar = findViewById(R.id.url_bar)
        progressBar = findViewById(R.id.progress_bar)
        webContainer = findViewById(R.id.web_container)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnReload = findViewById(R.id.btn_reload)
        btnHome = findViewById(R.id.btn_home)
        btnNewTab = findViewById(R.id.btn_new_tab)
        btnTabs = findViewById(R.id.btn_tabs)
    }

    private fun setupControls() {
        urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_NULL) {
                submitUrl()
                true
            } else {
                hideKeyboard()
                false
            }
        }
        urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) urlBar.setText(currentTab()?.url ?: "")
        }
        btnBack.setOnClickListener {
            currentTab()?.takeIf { it.canGoBack }?.session?.goBack()
        }
        btnForward.setOnClickListener {
            currentTab()?.takeIf { it.canGoForward }?.session?.goForward()
        }
        btnReload.setOnClickListener {
            val tab = currentTab() ?: return@setOnClickListener
            if (tab.isLoading) tab.session.stop() else tab.session.reload()
        }
        btnHome.setOnClickListener { navigate(HOME_PAGE) }
        btnNewTab.setOnClickListener { openTab(HOME_PAGE) }
        btnTabs.setOnClickListener { showTabsSheet() }
    }

    // -------------------------------------------------------------- Onglets

    /** Cree un onglet et charge l URL demandee. */
    fun openTab(url: String, select: Boolean = true): PlimbTab {
        val session = GeckoSession()
        val tab = PlimbTab(session, GeckoView(this))
        val callbacks = TabCallbacks(this, tab)
        tab.callbacks = callbacks

        session.progressDelegate = callbacks
        session.navigationDelegate = callbacks
        session.contentDelegate = callbacks
        session.promptDelegate = callbacks
        session.permissionDelegate = callbacks
        session.open(runtime)

        tab.geckoView.setSession(session)
        tab.url = url
        tabs.add(tab)
        if (select) selectTab(tabs.size - 1)
        session.loadUri(url)
        return tab
    }

    private fun selectTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        tabs.forEachIndexed { position, tab ->
            if (position == index) {
                webContainer.removeAllViews()
                webContainer.addView(tab.geckoView)
                tab.session.setActive(true)
                runtime.webExtensionController.setTabActive(tab.session, true)
            } else {
                tab.session.setActive(false)
                runtime.webExtensionController.setTabActive(tab.session, false)
            }
        }
        currentIndex = index
        urlBar.setText(tabs[index].url)
        updateChrome()
    }

    fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        val tab = tabs.removeAt(index)
        webContainer.removeView(tab.geckoView)
        tab.session.setActive(false)
        tab.session.close()
        if (tabs.isEmpty()) {
            currentIndex = -1
            finish()
        } else {
            selectTab(index.coerceAtMost(tabs.size - 1))
        }
    }

    fun indexOf(tab: PlimbTab): Int = tabs.indexOf(tab)

    fun currentTab(): PlimbTab? = tabs.getOrNull(currentIndex)

    fun updateChrome() {
        val tab = currentTab() ?: return
        btnBack.isEnabled = tab.canGoBack
        btnForward.isEnabled = tab.canGoForward
        btnReload.setImageResource(
            if (tab.isLoading) R.drawable.ic_close else R.drawable.ic_refresh
        )
        btnTabs.text = tabs.size.toString()
        progressBar.progress = tab.progress
        progressBar.isVisible = tab.isLoading
        if (!urlBar.hasFocus()) urlBar.setText(tab.url)
        invalidateOptionsMenu()
    }

    /** Appele par les delegues Gecko quand l onglet actif change d etat. */
    fun onTabStateChanged() {
        runOnUiThread { updateChrome() }
    }

    // ------------------------------------------------------------ Navigation

    fun navigate(url: String) {
        currentTab()?.session?.loadUri(url)
        updateChrome()
    }

    private fun submitUrl() {
        val input = urlBar.text.toString().trim()
        if (input.isEmpty()) return
        hideKeyboard()
        currentTab()?.session?.loadUri(normalizeInput(input))
    }

    fun normalizeInput(input: String): String {
        val text = input.trim()
        if (text.startsWith("file://") || text.startsWith("about:")) return text
        if (text.startsWith("http://") || text.startsWith("https://")) return text
        val looksLikeUrl = text.contains('.') && !text.contains(' ') && text.indexOf('.') > 0
        return if (looksLikeUrl) "https://$text" else Prefs.searchUrl(this, text)
    }

    private fun navigateToNewTab(url: String) {
        val current = currentTab()
        when {
            current == null -> openTab(url)
            current.url.isEmpty() || current.url == HOME_PAGE -> navigate(url)
            else -> openTab(url)
        }
    }

    private fun goBackOrExit() {
        val tab = currentTab()
        when {
            tab != null && tab.canGoBack -> tab.session.goBack()
            tabs.size > 1 -> closeTab(currentIndex)
            else -> finish()
        }
    }

    fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            snack(getString(R.string.error_no_app))
        }
    }

    // ----------------------------------------------------- Telechargements

    fun onDownload(session: GeckoSession, response: WebResponse) {
        val stream: InputStream = response.body ?: return
        val name = response.filename?.takeIf { it.isNotBlank() }
            ?: URLUtil.guessFileName(response.uri, null, response.contentType)
        CoroutineScope(Dispatchers.Main).launch {
            snack(getString(R.string.download_started, name))
            val saved = saveDownload(name, stream)
            snack(
                getString(
                    if (saved != null) R.string.download_done else R.string.download_failed,
                    name
                )
            )
        }
    }

    private suspend fun saveDownload(name: String, stream: InputStream): String? =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext null
                    resolver.openOutputStream(uri)?.use { output -> stream.copyTo(output) }
                    uri.toString()
                } else {
                    @Suppress("DEPRECATION")
                    val directory =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!directory.exists()) directory.mkdirs()
                    val file = File(directory, name)
                    FileOutputStream(file).use { output -> stream.copyTo(output) }
                    file.absolutePath
                }
            } catch (e: Exception) {
                Log.w("Plimb", "Telechargement impossible : $name", e)
                null
            } finally {
                runCatching { stream.close() }
            }
        }

    // -------------------------------------------------------------- Prompts

    fun pickFile(
        mimeTypes: Array<out String>?,
        multiple: Boolean,
        callback: (Array<Uri>?) -> Unit
    ) {
        pendingFileCallback = callback
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypes?.firstOrNull() ?: "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            mimeTypes?.let { putExtra(Intent.EXTRA_MIME_TYPES, it) }
        }
        runCatching { filePickerLauncher.launch(intent) }.onFailure {
            pendingFileCallback = null
            callback(null)
        }
    }

    fun requestAppPermissions(permissions: Array<out String>, callback: (Boolean) -> Unit) {
        pendingPermissionCallback = callback
        permissionLauncher.launch(permissions)
    }

    fun onFullScreenRequested(fullScreen: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // --------------------------------------------------- Extensions / add-ons

    private fun registerExtensionDelegates() {
        runtime.webExtensionController.setPromptDelegate(
            object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<out String>?,
                    origins: Array<out String>?,
                    dataCollectionPermissions: Array<out String>?
                ): GeckoResult<WebExtension.PermissionPromptResponse> {
                    val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                    val details = buildString {
                        append(extension.metaData?.description ?: "")
                        if (!permissions.isNullOrEmpty()) {
                            append("\n\nPermissions : ")
                            append(permissions.joinToString(", "))
                        }
                        if (!origins.isNullOrEmpty()) {
                            append("\nSites concernes : ")
                            append(origins.joinToString(", "))
                        }
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(extension.metaData?.name ?: extension.id)
                        .setMessage(details.trim())
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            result.complete(
                                WebExtension.PermissionPromptResponse(false, false, false)
                            )
                        }
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            result.complete(
                                WebExtension.PermissionPromptResponse(true, true, true)
                            )
                        }
                        .setOnCancelListener {
                            result.complete(
                                WebExtension.PermissionPromptResponse(false, false, false)
                            )
                        }
                        .show()
                    return result
                }
            }
        )

        runtime.webExtensionController.setAddonManagerDelegate(
            object : WebExtensionController.AddonManagerDelegate {}
        )
    }

    private fun openExtensions() {
        startActivity(Intent(this, ExtensionsActivity::class.java))
    }

    // ------------------------------------------------------------- Reglages

    private fun toggleBlocking() {
        val value = !prefs.getBoolean(Prefs.KEY_BLOCK_ADS, true)
        prefs.edit { putBoolean(Prefs.KEY_BLOCK_ADS, value) }
        AddonManager.setEnabled(
            runtime,
            AddonEntry(
                id = "plimb-blocker@plimb.browser",
                name = "",
                version = "",
                description = "",
                enabled = value,
                builtIn = true
            ),
            value
        )
        currentTab()?.session?.reload()
        snack(getString(if (value) R.string.block_ads_on else R.string.block_ads_off))
        invalidateOptionsMenu()
    }

    private fun toggleDesktopMode() {
        val value = !prefs.getBoolean(Prefs.KEY_DESKTOP, false)
        prefs.edit { putBoolean(Prefs.KEY_DESKTOP, value) }
        applyDesktopMode()
        invalidateOptionsMenu()
    }

    private fun applyDesktopMode() {
        val desktop = prefs.getBoolean(Prefs.KEY_DESKTOP, false)
        tabs.forEach { tab ->
            tab.session.settings.userAgentOverride =
                if (desktop) DESKTOP_UA else null
        }
        currentTab()?.session?.reload()
    }

    // -------------------------------------------------------------- Session

    private fun saveSession() {
        if (!prefs.getBoolean(Prefs.KEY_RESTORE, true)) return
        val urls = tabs.map { it.url }.filter { it.isNotBlank() && !it.startsWith("file://") }
        prefs.edit { putStringSet(Prefs.KEY_SESSION, urls.toSet()) }
    }

    private fun restoreSession() {
        if (!prefs.getBoolean(Prefs.KEY_RESTORE, true)) return
        val urls = prefs.getStringSet(Prefs.KEY_SESSION, emptySet())?.filterNotNull() ?: return
        urls.filter { it.isNotBlank() }.forEach { openTab(it, select = false) }
        prefs.edit { remove(Prefs.KEY_SESSION) }
    }

    // --------------------------------------------------------------- Divers

    private fun share() {
        val url = currentTab()?.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, currentTab()?.title ?: getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun showTabsSheet() {
        val sheet = TabsSheetFragment(
            items = tabs.toList(),
            activeIndex = currentIndex,
            onSelect = { index -> selectTab(index) },
            onClose = { index -> closeTab(index) }
        )
        sheet.show(supportFragmentManager, "tabs")
    }

    fun snack(message: String) {
        Snackbar.make(webContainer, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlBar.windowToken, 0)
        urlBar.clearFocus()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let { navigateToNewTab(it) }
            Intent.ACTION_SEND -> {
                val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!shared.isNullOrBlank()) {
                    val line = shared.lines().firstOrNull { it.isNotBlank() }
                    if (!line.isNullOrBlank()) navigateToNewTab(normalizeInput(line))
                }
            }
        }
    }
}
