package com.crownsmedia.kioskbrowser

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsActivity : AppCompatActivity() {

    private lateinit var startUrlInput: EditText
    private lateinit var homeUrlInput: EditText
    private lateinit var allowedUrlsInput: EditText
    private lateinit var adminPinInput: EditText
    private lateinit var adminPinConfirmInput: EditText
    private lateinit var loadFromServerSwitch: Switch
    private lateinit var serverUrlInput: EditText
    private lateinit var saveButton: Button
    private lateinit var exitAppButton: Button
    private lateinit var removeDeviceOwnerButton: Button

    // === DEFAULT-WERTE ===
    companion object {
        private const val DEFAULT_PIN = "12345"
        private const val DEFAULT_URL = "https://www.crowns-media.com"
        private const val DEFAULT_LOAD_FROM_SERVER = false
        private const val PREFS_NAME = "kiosk_settings"
    }
    
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
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        setupSaveButton()
        setupExitAppButton()
        setupRemoveDeviceOwnerButton()
    }

    private fun initViews() {
        startUrlInput = findViewById(R.id.startUrlInput)
        homeUrlInput = findViewById(R.id.homeUrlInput)
        allowedUrlsInput = findViewById(R.id.allowedUrlsInput)
        adminPinInput = findViewById(R.id.adminPinInput)
        adminPinConfirmInput = findViewById(R.id.adminPinConfirmInput)
        loadFromServerSwitch = findViewById(R.id.loadFromServerSwitch)
        serverUrlInput = findViewById(R.id.serverUrlInput)
        saveButton = findViewById(R.id.saveButton)
        exitAppButton = findViewById(R.id.exitAppButton)
        removeDeviceOwnerButton = findViewById(R.id.removeDeviceOwnerButton)
    }

    private fun loadSettings() {
        val prefs = getEncryptedSharedPreferences()
        
        val savedStartUrl = prefs.getString("start_url", "") ?: ""
        val savedHomeUrl = prefs.getString("home_url", "") ?: ""
        val savedAllowedUrls = prefs.getStringSet("allowed_urls", emptySet()) ?: emptySet()
        val savedPin = prefs.getString("admin_pin", "") ?: ""
        val savedLoadFromServer = prefs.getBoolean("load_from_server", DEFAULT_LOAD_FROM_SERVER)
        val savedServerUrl = prefs.getString("server_url", "") ?: ""

        startUrlInput.setText(savedStartUrl.ifEmpty { DEFAULT_URL })
        homeUrlInput.setText(savedHomeUrl.ifEmpty { savedStartUrl.ifEmpty { DEFAULT_URL } })
        allowedUrlsInput.setText(
            if (savedAllowedUrls.isEmpty()) DEFAULT_URL 
            else savedAllowedUrls.joinToString("\n")
        )
        adminPinInput.setText(savedPin.ifEmpty { DEFAULT_PIN })
        adminPinConfirmInput.setText(savedPin.ifEmpty { DEFAULT_PIN })
        loadFromServerSwitch.isChecked = savedLoadFromServer
        serverUrlInput.setText(savedServerUrl)

        // URL-Felder deaktivieren wenn Server-Laden aktiv ist
        updateFieldsEnabledState(savedLoadFromServer)
        
        loadFromServerSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateFieldsEnabledState(isChecked)
        }

        // Device Owner Button Status
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            removeDeviceOwnerButton.isEnabled = true
            removeDeviceOwnerButton.text = "Device Owner entfernen"
        } else {
            removeDeviceOwnerButton.isEnabled = false
            removeDeviceOwnerButton.text = "Kein Device Owner"
        }
    }

    private fun updateFieldsEnabledState(loadFromServer: Boolean) {
        serverUrlInput.isEnabled = loadFromServer
        startUrlInput.isEnabled = !loadFromServer
        homeUrlInput.isEnabled = !loadFromServer
        allowedUrlsInput.isEnabled = !loadFromServer
        
        // Visueller Hinweis bei deaktivierten Feldern
        val alpha = if (loadFromServer) 0.5f else 1.0f
        startUrlInput.alpha = if (loadFromServer) alpha else 1.0f
        homeUrlInput.alpha = if (loadFromServer) alpha else 1.0f
        allowedUrlsInput.alpha = if (loadFromServer) alpha else 1.0f
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    // === APP VERLASSEN BUTTON ===
    private fun setupExitAppButton() {
        exitAppButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("App verlassen")
                .setMessage("Möchten Sie den Kiosk-Modus beenden und zur Android-Startseite zurückkehren?")
                .setPositiveButton("Verlassen") { _, _ ->
                    exitKioskMode()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    // === KIOSK-MODUS BEENDEN UND APP VERLASSEN ===
    private fun exitKioskMode() {
        try {
            // Lock Task Mode beenden
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(packageName)) {
                stopLockTask()
            }
        } catch (e: Exception) {
            // Ignorieren wenn Lock Task nicht aktiv
        }

        // Zur Android-Startseite
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        
        // App beenden
        finishAffinity()
    }

    private fun setupRemoveDeviceOwnerButton() {
        removeDeviceOwnerButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Device Owner entfernen")
                .setMessage("Möchten Sie den Device Owner wirklich entfernen?\n\nDanach kann die App normal deinstalliert werden, aber der Kiosk-Modus funktioniert nicht mehr vollständig.")
                .setPositiveButton("Entfernen") { _, _ ->
                    if (MyDeviceAdminReceiver.clearDeviceOwner(this)) {
                        Toast.makeText(this, "✓ Device Owner entfernt", Toast.LENGTH_SHORT).show()
                        removeDeviceOwnerButton.isEnabled = false
                        removeDeviceOwnerButton.text = "Kein Device Owner"
                    } else {
                        Toast.makeText(this, "Fehler beim Entfernen", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private fun saveSettings() {
        val pin = adminPinInput.text.toString()
        val pinConfirm = adminPinConfirmInput.text.toString()

        if (pin != pinConfirm) {
            Toast.makeText(this, "PINs stimmen nicht überein!", Toast.LENGTH_SHORT).show()
            return
        }

        if (pin.length < 4) {
            Toast.makeText(this, "PIN muss mindestens 4 Zeichen haben!", Toast.LENGTH_SHORT).show()
            return
        }

        val startUrl = normalizeUrl(startUrlInput.text.toString().trim())
        val homeUrl = normalizeUrl(homeUrlInput.text.toString().trim())
        
        // Erlaubte URLs sammeln und Start-URL automatisch hinzufügen
        val allowedUrlsRaw = allowedUrlsInput.text.toString()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { normalizeUrl(it) }
            .toMutableSet()
        
        // Start-URL automatisch zu erlaubten URLs hinzufügen
        if (startUrl.isNotEmpty()) {
            allowedUrlsRaw.add(startUrl)
        }
        if (homeUrl.isNotEmpty()) {
            allowedUrlsRaw.add(homeUrl)
        }

        if (startUrl.isEmpty()) {
            Toast.makeText(this, "Start-URL darf nicht leer sein!", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getEncryptedSharedPreferences()
        prefs.edit().apply {
            putString("start_url", startUrl)
            putString("home_url", homeUrl)
            putStringSet("allowed_urls", allowedUrlsRaw)
            putString("admin_pin", pin)
            putBoolean("load_from_server", loadFromServerSwitch.isChecked)
            putString("server_url", normalizeUrl(serverUrlInput.text.toString().trim()))
            putBoolean("settings_changed", true)
            apply()
        }

        Toast.makeText(this, "✓ Einstellungen gespeichert", Toast.LENGTH_SHORT).show()
        
        // Wenn Server-Laden aktiviert ist, sofort neu laden
        if (loadFromServerSwitch.isChecked) {
            Toast.makeText(this, "Lade Einstellungen vom Server...", Toast.LENGTH_SHORT).show()
        }
        
        // Zurück zur MainActivity mit korrekten Flags
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    // === URL NORMALISIERUNG ===
    private fun normalizeUrl(url: String): String {
        if (url.isEmpty()) return ""
        
        var normalized = url.trim().trimEnd('/')
        
        // Prüfe ob bereits ein Protokoll vorhanden ist
        return when {
            normalized.startsWith("https://") -> normalized
            normalized.startsWith("http://") -> normalized.replaceFirst("http://", "https://")
            normalized.startsWith("www.") -> "https://$normalized"
            normalized.contains(".") -> "https://www.$normalized"
            else -> normalized // Ungültige URL, nicht verändern
        }
    }
}
