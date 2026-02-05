package com.crownsmedia.kioskbrowser

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.text.InputType
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        private const val INACTIVITY_TIMEOUT = 120_000L // 2 Minuten - Zurück zu startUrl
        private const val REFRESH_TIMEOUT = 600_000L // 10 Minuten - Aktualisierung für Aktualität
        private const val SYNC_INTERVAL = 60_000L // 1 Minute
        private const val UI_CHECK_INTERVAL = 5_000L // 5 Sekunden
        private const val PREFS_NAME = "kiosk_settings"
        private const val TAP_TIMEOUT = 1_000L
        private const val TAP_COUNT_THRESHOLD = 5
        private const val CORNER_SIZE = 200
    }

    // === PROPERTIES ===
    private lateinit var webView: WebView
    private lateinit var homeButton: android.widget.Button
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getEncryptedSharedPreferences() }
    
    private var startUrl = "" // Auto-Start und Inaktivitäts-Ziel
    private var homeUrl = "" // Grid/Hauptmenü (Home-Button Ziel)
    private var allowedUrls = listOf<String>()
    private var savedPin: String? = null
    
    private var isOpeningSettings = false
    private var hasRootAccess = false
    private var lockTaskStarted = false
    private var rootCommandsExecuted = false
    
    private var screenshotObserver: ScreenshotObserver? = null
    private var inactivityTimer: Runnable? = null
    private var refreshTimer: Runnable? = null

    // === RUNNABLES ===
    private val periodicSyncRunnable = object : Runnable {
        override fun run() {
            loadSettingsFromServerIfEnabled()
            
            // Memory Management: Cache und History periodisch leeren
            webView.clearCache(false) // false = Cookies behalten
            if (webView.copyBackForwardList().size > 50) {
                webView.clearHistory()
                Log.d("Memory", "History geleert (${webView.copyBackForwardList().size} Einträge)")
            }
            
            handler.postDelayed(this, SYNC_INTERVAL)
        }
    }

    private val navigationBarMonitor = object : Runnable {
        override fun run() {
            if (!isOpeningSettings) hideSystemUI()
            handler.postDelayed(this, UI_CHECK_INTERVAL)
        }
    }

    // === LIFECYCLE ===
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupWindowFlags()
        setContentView(R.layout.activity_main)
        
        checkRootAccess()
        if (hasRootAccess && !rootCommandsExecuted) {
            executeRootCommands()
            rootCommandsExecuted = true
        }
        
        setupDeviceOwner()
        
        webView = findViewById(R.id.webView)
        homeButton = findViewById(R.id.homeButton)
        
        setupWebView()
        setupHomeButton()
        loadSettingsAndStart()
        
        setupAdminGesture()
        setupBackButtonBlock()
        handler.post(navigationBarMonitor)
        setupScreenshotObserver()
    }

    override fun onResume() {
        super.onResume()
        isOpeningSettings = false
        
        checkForSettingsUpdate()
        setupWindowFlags()
        hideSystemUI()
        resetInactivityTimer()
        startLockTaskIfNeeded()
        
        handler.post(periodicSyncRunnable)
        handler.post(navigationBarMonitor)
        
        if (hasRootAccess) cleanScreenshotFolders()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(periodicSyncRunnable)
        handler.removeCallbacks(navigationBarMonitor)
        inactivityTimer?.let { handler.removeCallbacks(it) }
        refreshTimer?.let { handler.removeCallbacks(it) }
        
        if (isOpeningSettings) {
            try {
                stopLockTask()
                lockTaskStarted = false
            } catch (e: Exception) {
                Log.e("LockTask", "Fehler: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenshotObserver?.stopWatching()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isOpeningSettings) hideSystemUI()
    }

    // === WINDOW & UI ===
    private fun setupWindowFlags() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // === ENCRYPTED PREFERENCES ===
    private fun getEncryptedSharedPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                this,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("Security", "EncryptedPrefs Fehler, nutze normale: ${e.message}")
            // Fallback auf normale SharedPreferences
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    hide(android.view.WindowInsets.Type.systemBars())
                    systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            }
        } catch (e: Exception) {
            Log.e("UI", "hideSystemUI Fehler: ${e.message}")
        }
    }

    private fun showSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            enableNavigationBarViaRoot()
        } catch (e: Exception) {
            Log.e("UI", "showSystemUI Fehler: ${e.message}")
        }
    }

    // === ROOT ===
    private fun checkRootAccess() {
        hasRootAccess = try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).waitFor() == 0
        } catch (e: Exception) {
            false
        }
        Log.d("Root", "Root verfügbar: $hasRootAccess")
    }

    private fun executeRootCommands() {
        thread {
            val commands = listOf(
                // Navigation Bar
                "settings put global policy_control immersive.full=*",
                "settings put secure navigation_bar_show 0",
                "settings put system navigation_bar_show 0",
                "wm overscan 0,0,0,-100",
                // Screenshots
                "pm disable com.android.systemui/.screenshot.TakeScreenshotService",
                "settings put system screenshot_sound 0",
                "chmod 000 /sdcard/Pictures/Screenshots",
                "chmod 000 /sdcard/DCIM/Screenshots",
                "chmod 000 /sdcard/Screenshots"
            )
            commands.forEach { 
                executeRootCommand(it)
                Thread.sleep(30)
            }
            Log.d("Root", "Root-Befehle ausgeführt")
        }
    }

    private fun executeRootCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).apply {
                writeBytes("$command\nexit\n")
                flush()
                close()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun enableNavigationBarViaRoot() {
        if (!hasRootAccess) return
        thread {
            listOf(
                "settings delete global policy_control",
                "settings put secure navigation_bar_show 1",
                "wm overscan 0,0,0,0"
            ).forEach { executeRootCommand(it) }
        }
    }

    private fun cleanScreenshotFolders() {
        thread {
            listOf(
                "rm -rf /sdcard/Pictures/Screenshots/*",
                "rm -rf /sdcard/DCIM/Screenshots/*",
                "rm -rf /sdcard/Screenshots/*"
            ).forEach { executeRootCommand(it) }
        }
    }

    // === DEVICE OWNER ===
    private fun setupDeviceOwner() {
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val component = ComponentName(this, MyDeviceAdminReceiver::class.java)
            
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(component, arrayOf(packageName))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(component, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
                
                // Tastatur auf Standard setzen (verhindert Sprachwechsel-Bugs)
                setDefaultKeyboard(dpm, component)
            }
        } catch (e: Exception) {
            Log.e("DeviceOwner", "Fehler: ${e.message}")
        }
    }
    
    private fun setDefaultKeyboard(dpm: DevicePolicyManager, component: ComponentName) {
        try {
            // Hole verfügbare Tastaturen
            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val enabledInputs = inputManager.enabledInputMethodList
            
            if (enabledInputs.isNotEmpty()) {
                // Setze erste verfügbare Tastatur als Standard
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    dpm.setSecureSetting(component, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD, enabledInputs[0].id)
                }
                Log.d("Keyboard", "Standard-Tastatur gesetzt: ${enabledInputs[0].id}")
            }
        } catch (e: Exception) {
            Log.e("Keyboard", "Tastatur-Setup Fehler: ${e.message}")
        }
    }

    private fun startLockTaskIfNeeded() {
        if (lockTaskStarted || isOpeningSettings) return
        
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            if (dpm.isDeviceOwnerApp(packageName)) {
                handler.postDelayed({
                    if (!lockTaskStarted && !isOpeningSettings) {
                        try {
                            startLockTask()
                            lockTaskStarted = true
                        } catch (e: Exception) {
                            Log.e("LockTask", "Fehler: ${e.message}")
                        }
                    }
                }, 500)
            }
        } catch (e: Exception) {
            Log.e("LockTask", "Fehler: ${e.message}")
        }
    }

    // === SCREENSHOT OBSERVER ===
    private fun setupScreenshotObserver() {
        screenshotObserver = ScreenshotObserver { file ->
            try {
                if (file.delete()) {
                    runOnUiThread {
                        Toast.makeText(this, "⛔ Screenshots deaktiviert", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Screenshot", "Löschen fehlgeschlagen: ${e.message}")
            }
        }
        screenshotObserver?.startWatching()
    }

    // === WEBVIEW ===
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            savePassword = false
            @Suppress("DEPRECATION")
            saveFormData = false
        }
        webView.clearCache(true)
        
        // WICHTIG: Downloads/Uploads komplett blockieren
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            Log.w("Download", "Download blockiert: $url")
            runOnUiThread {
                Toast.makeText(this, "⛔ Downloads sind deaktiviert", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupHomeButton() {
        homeButton.setOnClickListener {
            Log.d("HomeButton", "Clicked! Navigating to: $homeUrl")
            webView.loadUrl(homeUrl)
            resetInactivityTimer()
        }
        homeButton.visibility = View.GONE
    }

    private fun setupWebViewClient() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val isAllowed = url.startsWith(startUrl) || url.startsWith(homeUrl) || allowedUrls.any { url.startsWith(it) }
                
                if (isAllowed) {
                    resetInactivityTimer()
                }
                
                return !isAllowed
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                Log.d("WebView", "Page finished: $url, homeUrl: $homeUrl")
                
                // Home-Button nur zeigen wenn nicht auf Grid (homeUrl)
                if (url != null && url != homeUrl && !url.startsWith(homeUrl.substringBeforeLast('?'))) {
                    homeButton.visibility = View.VISIBLE
                    Log.d("HomeButton", "Showing button")
                } else {
                    homeButton.visibility = View.GONE
                    Log.d("HomeButton", "Hiding button")
                }
                
                resetInactivityTimer()
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                (resultMsg?.obj as? WebView.WebViewTransport)?.let {
                    it.webView = webView
                    resultMsg.sendToTarget()
                }
                return true
            }
            
            // File-Upload komplett blockieren
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                Log.w("Upload", "File-Upload blockiert")
                filePathCallback?.onReceiveValue(null)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "⛔ Uploads sind deaktiviert", Toast.LENGTH_SHORT).show()
                }
                return true // true = handled (blockiert)
            }
        }
    }

    // === INACTIVITY TIMER ===
    private fun resetInactivityTimer() {
        // Stoppe beide Timer
        inactivityTimer?.let { handler.removeCallbacks(it) }
        refreshTimer?.let { handler.removeCallbacks(it) }
        
        // Inaktivitäts-Timer: Zurück zu startUrl nach 2 Min (nur wenn nicht bereits dort)
        inactivityTimer = Runnable {
            if (webView.url != null && webView.url != startUrl) {
                Log.d("InactivityTimer", "Timeout - returning to: $startUrl")
                webView.loadUrl(startUrl)
                runOnUiThread {
                    Toast.makeText(this, "Zurück zur Startseite", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Refresh-Timer: Aktualisiere startUrl nach 10 Min (auch wenn bereits dort)
        refreshTimer = Runnable {
            // Nur reload wenn tatsächlich auf startUrl, sonst könnte inactivityTimer gerade navigieren
            val currentUrl = webView.url
            if (currentUrl != null && (currentUrl == startUrl || currentUrl.startsWith(startUrl.substringBeforeLast('?')))) {
                Log.d("RefreshTimer", "Aktualisiere Seite für aktuelle Inhalte: $startUrl")
                webView.reload()
            } else {
                Log.d("RefreshTimer", "Nicht auf startUrl, überspringe Refresh")
            }
        }
        
        handler.postDelayed(inactivityTimer!!, INACTIVITY_TIMEOUT)
        handler.postDelayed(refreshTimer!!, REFRESH_TIMEOUT)
    }

    // === SETTINGS ===
    private fun loadSettingsAndStart() {
        // Prüfe ob Server-Laden aktiviert ist
        if (prefs.getBoolean("load_from_server", false)) {
            val serverUrl = prefs.getString("server_url", "").orEmpty()
            if (serverUrl.isNotEmpty()) {
                // Lade erst vom Server, dann starte WebView
                loadSettingsFromServerIfEnabled {
                    loadLocalSettingsAndStartWebView()
                }
                return
            }
        }
        
        // Kein Server-Laden: Direkt lokale Settings laden
        loadLocalSettingsAndStartWebView()
    }
    
    private fun loadLocalSettingsAndStartWebView() {
        savedPin = prefs.getString("admin_pin", null)
        startUrl = prefs.getString("start_url", "").orEmpty()
        homeUrl = prefs.getString("home_url", "").orEmpty()
        allowedUrls = prefs.getStringSet("allowed_urls", emptySet())?.toList().orEmpty()
        
        // Falls homeUrl nicht gesetzt, nutze startUrl als Fallback
        if (homeUrl.isEmpty() && startUrl.isNotEmpty()) {
            homeUrl = startUrl
        }

        if (startUrl.isNotEmpty()) {
            setupWebViewClient()
            webView.loadUrl(startUrl)
        } else {
            openSettings()
            finish()
        }
    }

    private fun checkForSettingsUpdate() {
        if (!prefs.getBoolean("settings_changed", false)) return
        
        prefs.edit().putBoolean("settings_changed", false).apply()
        
        // Wenn Server-Laden aktiviert ist, erst vom Server laden
        if (prefs.getBoolean("load_from_server", false)) {
            loadSettingsFromServerIfEnabled {
                // Nach Server-Laden, lokale Settings aktualisieren
                updateLocalSettings()
            }
        } else {
            // Kein Server-Laden: Direkt lokale Settings aktualisieren
            updateLocalSettings()
        }
    }
    
    private fun updateLocalSettings() {
        val newStartUrl = prefs.getString("start_url", "").orEmpty()
        val urlChanged = newStartUrl != startUrl
        
        startUrl = newStartUrl
        homeUrl = prefs.getString("home_url", "").orEmpty()
        allowedUrls = prefs.getStringSet("allowed_urls", emptySet())?.toList().orEmpty()
        savedPin = prefs.getString("admin_pin", null)
        
        // Fallback wenn homeUrl leer
        if (homeUrl.isEmpty() && startUrl.isNotEmpty()) {
            homeUrl = startUrl
        }
        
        if (startUrl.isNotEmpty()) {
            setupWebViewClient()
            // Immer neu laden nach Settings-Update (auch wenn URL gleich)
            webView.loadUrl(startUrl)
            Toast.makeText(this, "✓ Einstellungen aktualisiert", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettingsFromServerIfEnabled(onFinish: (() -> Unit)? = null) {
        val serverUrl = prefs.getString("server_url", "").orEmpty()
        
        if (!prefs.getBoolean("load_from_server", false) || serverUrl.isEmpty()) {
            Log.d("ServerSync", "Server-Laden deaktiviert oder keine URL")
            onFinish?.invoke()
            return
        }

        Log.d("ServerSync", "Lade Settings von: $serverUrl")
        
        thread {
            try {
                // Robuste URL-Konstruktion: Falls bereits settings.json angegeben, nicht anhängen
                val jsonUrl = if (serverUrl.endsWith(".json", ignoreCase = true)) {
                    serverUrl
                } else {
                    serverUrl.trimEnd('/') + "/settings.json"
                }
                
                Log.d("ServerSync", "URL: $jsonUrl")
                
                val conn = (URL(jsonUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().readText()
                    Log.d("ServerSync", "Erfolgreich geladen: $jsonText")
                    
                    val json = JSONObject(jsonText)
                    val start = json.optString("start_url", "")
                    val home = json.optString("home_url", "")
                    val allowed = (0 until (json.optJSONArray("allowed_urls")?.length() ?: 0))
                        .map { json.optJSONArray("allowed_urls")!!.getString(it) }

                    Log.d("ServerSync", "Start: $start, Home: $home, Allowed: ${allowed.size}")

                    prefs.edit()
                        .putString("start_url", start)
                        .putString("home_url", home)
                        .putStringSet("allowed_urls", allowed.toSet())
                        .apply()

                    runOnUiThread {
                        startUrl = start
                        homeUrl = home.ifEmpty { start }
                        allowedUrls = allowed
                        Toast.makeText(this, "✓ Server-Einstellungen geladen", Toast.LENGTH_SHORT).show()
                        onFinish?.invoke()
                    }
                } else {
                    Log.e("ServerSync", "HTTP Error: ${conn.responseCode}")
                    runOnUiThread { 
                        Toast.makeText(this, "⚠ Server nicht erreichbar (${conn.responseCode})", Toast.LENGTH_SHORT).show()
                        onFinish?.invoke() 
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerSync", "Fehler beim Laden: ${e.message}", e)
                runOnUiThread { 
                    Toast.makeText(this, "⚠ Server-Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
                    onFinish?.invoke() 
                }
            }
        }
    }

    // === INPUT BLOCKING ===
    override fun onKeyDown(keyCode: Int, event: KeyEvent?) = isBlockedKey(keyCode) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent?) = isBlockedKey(keyCode) || super.onKeyUp(keyCode, event)
    override fun dispatchKeyEvent(event: KeyEvent) = isBlockedKey(event.keyCode) || super.dispatchKeyEvent(event)
    
    private fun isBlockedKey(keyCode: Int) = keyCode in listOf(
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SYSRQ,
        KeyEvent.KEYCODE_LANGUAGE_SWITCH, // Tastaturwechsel
        KeyEvent.KEYCODE_SWITCH_CHARSET // Zeichensatz-Wechsel
    )

    private fun setupBackButtonBlock() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
    }

    // === ADMIN ACCESS ===
    private fun setupAdminGesture() {
        var tapCount = 0
        var lastTapTime = 0L

        webView.setOnTouchListener { v, event ->
            // Reset Inaktivitäts-Timer bei jeder Touch-Interaktion
            if (event.action == MotionEvent.ACTION_DOWN) {
                resetInactivityTimer()
            }
            
            // Admin-Geste prüfen bei Touch-Release
            if (event.action == MotionEvent.ACTION_UP) {
                val currentTime = System.currentTimeMillis()
                val isInCorner = event.x < CORNER_SIZE && event.y > v.height - CORNER_SIZE

                tapCount = if (currentTime - lastTapTime < TAP_TIMEOUT && isInCorner) tapCount + 1 else 1
                lastTapTime = currentTime

                if (tapCount >= TAP_COUNT_THRESHOLD) {
                    tapCount = 0
                    savedPin?.let { showAdminDialog(it) }
                }
                v.performClick()
            }
            false
        }
    }

    private fun showAdminDialog(correctPin: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Admin-Zugang")
            .setMessage("PIN eingeben:")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == correctPin) {
                    openSettings()
                } else {
                    Toast.makeText(this, "Falscher PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun openSettings() {
        try {
            stopLockTask()
            lockTaskStarted = false
        } catch (e: Exception) {}
        
        showSystemUI()
        isOpeningSettings = true
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}