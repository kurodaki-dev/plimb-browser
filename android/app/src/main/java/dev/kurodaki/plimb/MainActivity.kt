package dev.kurodaki.plimb

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.PermissionRequest
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.isVisible
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

/**
 * Plimb Browser — ecran principal.
 *
 * Gere les onglets (une WebView par onglet), la barre d adresse, les telechargements,
 * les pop-ups, le bloquage des publicites et l injection des extensions.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME_PAGE = "file:///android_asset/home.html"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
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
    private var lastDesktopMode = false
    private var lastJsEnabled = true

    private val prefs by lazy { Prefs.settings(this) }
    private val webViewClient = PlimbWebViewClient(this)
    private val webChromeClient = ChromeClient()

    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingFileChooser ?: return@registerForActivityResult
            pendingFileChooser = null
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
            callback.onReceiveValue(uris)
        }

    private inner class ChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (view !== currentWeb()) return
            progressBar.progress = newProgress
            progressBar.isVisible = newProgress in 1..99
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (view === currentWeb()) updateChrome()
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            pendingFileChooser?.onReceiveValue(null)
            pendingFileChooser = filePathCallback
            return try {
                fileChooserLauncher.launch(fileChooserParams.createIntent())
                true
            } catch (e: ActivityNotFoundException) {
                pendingFileChooser = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val granted = request.resources
                ?.filter {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                }
                ?.toTypedArray()
                ?: emptyArray()
            if (granted.isNotEmpty()) request.grant(granted) else request.deny()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(this@MainActivity)
            popup.settings.javaScriptEnabled = prefs.getBoolean(Prefs.KEY_JS, true)
            popup.settings.domStorageEnabled = true
            popup.settings.setSupportMultipleWindows(false)
            transport.webView = popup
            resultMsg.sendToTarget()
            popup.webViewClient = PopupClient(this@MainActivity, popup)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        setContentView(R.layout.activity_main)
        bindViews()
        setSupportActionBar(toolbar)
        lastDesktopMode = prefs.getBoolean(Prefs.KEY_DESKTOP, false)
        lastJsEnabled = prefs.getBoolean(Prefs.KEY_JS, true)
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

    override fun onResume() {
        super.onResume()
        currentWeb()?.onResume()

        val desktop = prefs.getBoolean(Prefs.KEY_DESKTOP, false)
        if (desktop != lastDesktopMode) {
            lastDesktopMode = desktop
            applyDesktopMode()
        }
        val jsEnabled = prefs.getBoolean(Prefs.KEY_JS, true)
        if (jsEnabled != lastJsEnabled) {
            lastJsEnabled = jsEnabled
            tabs.forEach { it.webView.settings.javaScriptEnabled = jsEnabled }
        }
    }

    override fun onPause() {
        saveSession()
        currentWeb()?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        tabs.forEach { it.webView.destroy() }
        tabs.clear()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_block_ads)?.isChecked =
            prefs.getBoolean(Prefs.KEY_BLOCK_ADS, true)
        menu.findItem(R.id.action_desktop)?.isChecked = lastDesktopMode
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
                val value = !prefs.getBoolean(Prefs.KEY_BLOCK_ADS, true)
                prefs.edit { putBoolean(Prefs.KEY_BLOCK_ADS, value) }
                currentWeb()?.reload()
                snack(getString(if (value) R.string.block_ads_on else R.string.block_ads_off))
                invalidateOptionsMenu()
                true
            }
            R.id.action_desktop -> {
                val value = !lastDesktopMode
                prefs.edit { putBoolean(Prefs.KEY_DESKTOP, value) }
                lastDesktopMode = value
                applyDesktopMode()
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

    // ---------------------------------------------------------------- UI

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
            if (!hasFocus) urlBar.setText(currentWeb()?.url ?: "")
        }
        btnBack.setOnClickListener { currentWeb()?.takeIf { it.canGoBack() }?.goBack() }
        btnForward.setOnClickListener { currentWeb()?.takeIf { it.canGoForward() }?.goForward() }
        btnReload.setOnClickListener {
            val web = currentWeb() ?: return@setOnClickListener
            if (web.progress in 1..99) web.stopLoading() else web.reload()
        }
        btnHome.setOnClickListener { navigate(HOME_PAGE) }
        btnNewTab.setOnClickListener { openTab(HOME_PAGE) }
        btnTabs.setOnClickListener { showTabsSheet() }
    }

    // ------------------------------------------------------------- Onglets

    /** Ouvre un nouvel onglet sur l URL donnee. */
    fun openTab(url: String, select: Boolean = true): PlimbTab {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        configure(webView)
        val tab = PlimbTab(webView)
        tabs.add(tab)
        if (select) selectTab(tabs.size - 1)
        webView.loadUrl(url)
        return tab
    }

    /** Transforme une WebView enfant (pop-up) en onglet a part entiere. */
    fun adoptPopup(popup: WebView, url: String): PlimbTab {
        configure(popup)
        val tab = PlimbTab(popup)
        tabs.add(tab)
        selectTab(tabs.size - 1)
        popup.loadUrl(url)
        return tab
    }

    private fun configure(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = prefs.getBoolean(Prefs.KEY_JS, true)
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            setGeolocationEnabled(false)
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString =
                if (prefs.getBoolean(Prefs.KEY_DESKTOP, false)) DESKTOP_UA else null
        }
        web.webViewClient = webViewClient
        web.webChromeClient = webChromeClient
        web.setDownloadListener { url, userAgent, disposition, mimetype, _ ->
            downloadFile(url, userAgent, disposition, mimetype)
        }
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    }

    private fun selectTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        tabs.forEachIndexed { position, tab ->
            if (position == index) {
                webContainer.removeAllViews()
                webContainer.addView(tab.webView)
                tab.webView.onResume()
            } else {
                tab.webView.onPause()
            }
        }
        currentIndex = index
        urlBar.setText(tabs[index].webView.url ?: "")
        updateChrome()
    }

    private fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        val tab = tabs.removeAt(index)
        webContainer.removeView(tab.webView)
        tab.webView.destroy()
        if (tabs.isEmpty()) {
            currentIndex = -1
            finish()
        } else {
            selectTab(index.coerceAtMost(tabs.size - 1))
        }
    }

    fun updateChrome() {
        val web = currentWeb()
        btnBack.isEnabled = web?.canGoBack() == true
        btnForward.isEnabled = web?.canGoForward() == true
        val loading = web.progress in 1..99
        btnReload.setImageResource(if (loading) R.drawable.ic_close else R.drawable.ic_refresh)
        btnTabs.text = tabs.size.toString()
        if (!urlBar.hasFocus()) urlBar.setText(web?.url ?: "")
        invalidateOptionsMenu()
    }

    fun currentTab(): PlimbTab? = tabs.getOrNull(currentIndex)

    fun currentWeb(): WebView? = currentTab()?.webView

    fun isBlockingAds(): Boolean = prefs.getBoolean(Prefs.KEY_BLOCK_ADS, true)

    // --------------------------------------------------------- Navigation

    fun navigate(url: String) {
        currentWeb()?.loadUrl(url)
        updateChrome()
    }

    private fun submitUrl() {
        val input = urlBar.text.toString().trim()
        if (input.isEmpty()) return
        hideKeyboard()
        currentWeb()?.loadUrl(normalizeInput(input))
    }

    /** URL ou requete de recherche selon ce que l utilisateur a tape. */
    fun normalizeInput(input: String): String {
        val text = input.trim()
        if (text.startsWith("file://") || text.startsWith("about:")) return text
        if (text.startsWith("http://") || text.startsWith("https://")) return text
        val looksLikeUrl = text.contains('.') && !text.contains(' ') && text.indexOf('.') > 0
        return if (looksLikeUrl) "https://$text" else Prefs.searchUrl(this, text)
    }

    private fun navigateToNewTab(url: String) {
        val current = currentWeb()
        when {
            current == null -> openTab(url)
            current.url.isNullOrBlank() || current.url == HOME_PAGE -> {
                current.loadUrl(url)
                updateChrome()
            }
            else -> openTab(url)
        }
    }

    private fun goBackOrExit() {
        val web = currentWeb()
        when {
            web != null && web.canGoBack() -> web.goBack()
            tabs.size > 1 -> closeTab(currentIndex)
            else -> finish()
        }
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

    fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            snack(getString(R.string.error_no_app))
        }
    }

    // ------------------------------------------------------- Telechargement

    @Suppress("DEPRECATION")
    private fun downloadFile(url: String, userAgent: String, disposition: String, mimeType: String) {
        val fileName = URLUtil.guessFileName(url, disposition, mimeType)
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                currentWeb()?.url?.let { addRequestHeader("Referer", it) }
                setTitle(fileName)
                setDescription(getString(R.string.app_name))
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            snack(getString(R.string.download_started, fileName))
        } catch (e: Exception) {
            snack(getString(R.string.download_failed, fileName))
        }
    }

    // ------------------------------------------------------------- Session

    private fun saveSession() {
        if (!prefs.getBoolean(Prefs.KEY_RESTORE, true)) return
        val urls = tabs.mapNotNull { tab ->
            tab.webView.url?.takeIf { !it.startsWith("file://") }
        }
        prefs.edit {
            putStringSet(Prefs.KEY_SESSION, urls.toSet())
            putString(Prefs.KEY_LAST_URL, urls.firstOrNull() ?: "")
        }
    }

    private fun restoreSession() {
        if (!prefs.getBoolean(Prefs.KEY_RESTORE, true)) return
        val urls = prefs.getStringSet(Prefs.KEY_SESSION, emptySet())?.filterNotNull() ?: return
        urls.filter { it.isNotBlank() }.forEach { openTab(it, select = false) }
        prefs.edit { remove(Prefs.KEY_SESSION) }
    }

    // ------------------------------------------------------------- Divers

    private fun share() {
        val url = currentWeb()?.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, currentWeb()?.title ?: getString(R.string.app_name))
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

    private fun openExtensions() {
        startActivity(Intent(this, ExtensionsActivity::class.java))
    }

    fun snack(message: String) {
        Snackbar.make(webContainer, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlBar.windowToken, 0)
        urlBar.clearFocus()
    }

    // ------------------------------------- Appels du WebViewClient fils

    fun onPageStarted(view: WebView, url: String) {
        if (view !== currentWeb()) return
        progressBar.isVisible = true
        progressBar.progress = 0
        updateChrome()
    }

    fun onPageCommitted(view: WebView, url: String) {
        if (view === currentWeb() && !urlBar.hasFocus()) urlBar.setText(url)
    }

    fun onPageFinished(view: WebView, url: String) {
        if (view !== currentWeb()) return
        progressBar.isVisible = false
        updateChrome()
        val tab = tabs.firstOrNull { it.webView === view } ?: return
        if (tab.lastInjected != url) {
            tab.lastInjected = url
            Extensions.inject(this, view)
        }
    }
}
