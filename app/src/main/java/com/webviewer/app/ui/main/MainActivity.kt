package com.webviewer.app.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.webviewer.app.R
import com.webviewer.app.databinding.ActivityMainBinding
import com.webviewer.app.ui.settings.SettingsActivity
import com.webviewer.app.util.MacroDroidHelper
import com.webviewer.app.util.NetworkUtil
import com.webviewer.app.util.UrlValidator
import com.webviewer.app.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    // Upload file dalla WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // Contatore pressioni lunghe MacroDroid
    private var macroPressCount = 0

    // Launcher per file chooser upload
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { arrayOf(it) }
                ?: cameraImageUri?.let { arrayOf(it) }
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    // Launcher permessi
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* gestito inline */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Schermo intero edge-to-edge — gestisce barre Samsung correttamente
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemBars()
        setupWebView()
        setupSettingsButton()
        observeViewModel()
        vm.loadActiveProject()
    }

    // ---- Schermo intero ----

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---- WebView Setup ----

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = binding.webView
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S911B) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        // Cookie: wv passato esplicitamente — risolve il type mismatch
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        wv.webViewClient = AppWebViewClient()
        wv.webChromeClient = AppWebChromeClient()
    }

    // ---- WebViewClient ----

    inner class AppWebViewClient : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            vm.setLoadState(MainViewModel.LoadState.Loading)
            binding.progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            vm.setLoadState(MainViewModel.LoadState.Success)
            binding.progressBar.visibility = View.GONE
            vm.setUrl(url)
            CookieManager.getInstance().flush()
            // Chiudi automaticamente il banner GAS dopo 800ms
            // (tempo necessario per il rendering del DOM)
            view.postDelayed({ dismissGasBanner(view) }, 800)
        }

        override fun onReceivedError(
            view: WebView, request: WebResourceRequest, error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                binding.progressBar.visibility = View.GONE
                val msg = "Errore ${error.errorCode}: ${error.description}"
                vm.setLoadState(MainViewModel.LoadState.Error(msg, request.url.toString()))
                showErrorOverlay(msg)
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView, request: WebResourceRequest
        ): Boolean {
            val url = request.url.toString()
            // Gestisci intent:// e market:// links
            return when {
                url.startsWith("intent://") -> {
                    tryHandleIntentUrl(url)
                    true
                }
                url.startsWith("market://") -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                }
                else -> false  // WebView gestisce normalmente
            }
        }

        private fun tryHandleIntentUrl(url: String) {
            try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            } catch (e: Exception) { /* ignora */ }
        }
    }

    /**
     * Chiude il banner "Questa applicazione è stata creata da un utente di Google Apps Script"
     * L'avviso GAS ha sempre la stessa struttura DOM — selettori multipli per robustezza.
     * Delay 800ms per attendere il rendering completo.
     */
    private fun dismissGasBanner(view: WebView) {
        val js = """
            (function() {
                // Selettori possibili per il pulsante X del banner GAS
                var selectors = [
                    // Pulsante dismiss generico GAS
                    'button[aria-label="Chiudi"]',
                    'button[aria-label="Close"]',
                    'button[aria-label="Dismiss"]',
                    // Classe specifica banner GAS (cambia raramente)
                    '.dismissButton',
                    '.close-button',
                    // SVG icon X dentro pulsante — cerca il genitore cliccabile
                    'button svg[focusable="false"]',
                    // Fallback: primo pulsante nel banner in alto
                    'header button',
                    '.notice-bar button',
                    '[role="banner"] button'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) {
                        // Risali al pulsante se abbiamo trovato un figlio
                        var btn = el.closest('button') || el;
                        btn.click();
                        return 'clicked: ' + selectors[i];
                    }
                }
                // Fallback finale: cerca per testo vicino alla X visiva
                var buttons = document.querySelectorAll('button');
                for (var b = 0; b < buttons.length; b++) {
                    var rect = buttons[b].getBoundingClientRect();
                    // La X è in alto a destra — x > 80% larghezza, y < 200px
                    if (rect.right > window.innerWidth * 0.75 && rect.top < 200 && rect.width < 80) {
                        buttons[b].click();
                        return 'clicked by position';
                    }
                }
                return 'not found';
            })();
        """.trimIndent()
        view.evaluateJavascript(js) { result ->
            android.util.Log.d("GasBanner", "dismiss result: $result")
        }
    }

    // ---- WebChromeClient ----

    inner class AppWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progressBar.progress = newProgress
        }

        // Popup / finestre nuove — carica nella stessa WebView
        override fun onCreateWindow(
            view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message
        ): Boolean {
            val newWebView = WebView(this@MainActivity)
            newWebView.webViewClient = AppWebViewClient()
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = newWebView
            resultMsg.sendToTarget()
            return true
        }

        // Upload file
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            this@MainActivity.filePathCallback?.onReceiveValue(null)
            this@MainActivity.filePathCallback = filePathCallback
            requestFileChooser()
            return true
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback
        ) {
            callback.invoke(origin, true, false)
        }
    }

    // ---- File chooser / Camera ----

    private fun requestFileChooser() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
        }
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) launchFileChooser()
        else permissionLauncher.launch(permissions)
    }

    private fun launchFileChooser() {
        // Intent galleria
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        // Intent camera
        val photoFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
        }
        val chooser = Intent.createChooser(galleryIntent, "Seleziona file").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        }
        fileChooserLauncher.launch(chooser)
    }

    // ---- Mini icona settings (angolo alto destro) ----

    private fun setupSettingsButton() {
        val btn = binding.btnSettings
        var dX = 0f; var dY = 0f
        var startX = 0f; var startY = 0f
        var dragging = false

        btn.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    dragging = false
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val moveX = Math.abs(event.rawX - startX)
                    val moveY = Math.abs(event.rawY - startY)
                    if (moveX > 8f || moveY > 8f) {
                        dragging = true
                        // Limiti schermo
                        val parent = v.parent as android.view.View
                        val newX = (event.rawX + dX)
                            .coerceIn(0f, (parent.width - v.width).toFloat())
                        val newY = (event.rawY + dY)
                            .coerceIn(0f, (parent.height - v.height).toFloat())
                        v.x = newX
                        v.y = newY
                        v.performClick()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        // TAP breve → Settings
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }

        // Pressione lunga → MacroDroid
        btn.setOnLongClickListener {
            handleMacroDroidLongPress()
            true
        }
    }

    private fun handleMacroDroidLongPress() {
        val s = vm.settings.value
        if (!MacroDroidHelper.isInstalled(this, s.macroDroidPackage)) {
            Toast.makeText(this, "MacroDroid non installato", Toast.LENGTH_SHORT).show()
            return
        }
        macroPressCount++
        if (macroPressCount % 2 == 1) {
            // Prima pressione → avvia macro
            val ok = MacroDroidHelper.triggerMacro(this, s.macroDroidPackage, s.macroDroidMacroName)
            Toast.makeText(this, if (ok) "▶ Macro avviata" else "Errore avvio macro", Toast.LENGTH_SHORT).show()
        } else {
            // Seconda pressione → ferma macro
            val ok = MacroDroidHelper.stopMacro(this, s.macroDroidPackage, s.macroDroidMacroName)
            Toast.makeText(this, if (ok) "⏹ Macro fermata" else "Stop inviato", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Observer ----

    private fun observeViewModel() {
        lifecycleScope.launch {
            vm.currentUrl.collectLatest { url ->
                if (url.isNotBlank() && binding.webView.url != url) {
                    loadUrl(url)
                }
            }
        }
        lifecycleScope.launch {
            vm.toastMessage.collectLatest { msg ->
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
        lifecycleScope.launch {
            vm.loadState.collectLatest { state ->
                when (state) {
                    is MainViewModel.LoadState.NoNetwork -> showNoNetworkOverlay()
                    is MainViewModel.LoadState.Error -> showErrorOverlay(state.message)
                    else -> hideOverlay()
                }
            }
        }
    }

    private fun loadUrl(url: String) {
        if (!NetworkUtil.isConnected(this)) {
            vm.setLoadState(MainViewModel.LoadState.NoNetwork)
            return
        }
        val normalized = UrlValidator.normalize(url)
        binding.webView.loadUrl(normalized)
    }

    // ---- Overlay errori ----

    private fun showErrorOverlay(message: String) {
        binding.overlayError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
        binding.btnRetry.setOnClickListener {
            hideOverlay()
            val url = vm.currentUrl.value
            if (url.isNotBlank()) binding.webView.reload()
        }
    }

    private fun showNoNetworkOverlay() {
        binding.overlayError.visibility = View.VISIBLE
        binding.tvErrorMessage.text = "Nessuna connessione di rete"
        binding.btnRetry.setOnClickListener {
            hideOverlay()
            if (NetworkUtil.isConnected(this)) binding.webView.reload()
        }
    }

    private fun hideOverlay() {
        binding.overlayError.visibility = View.GONE
    }

    // ---- Back navigation ----

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        hideSystemBars()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
