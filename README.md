# Kiosk Browser

**Version 1.0**

Eine professionelle Android Kiosk-Browser-Lösung für kommerzielle Displays, Informationsterminals und öffentliche Geräte. Die App ermöglicht es, Android-Tablets in sichere, eingeschränkte Kiosk-Systeme zu verwandeln.

## 🎯 Features

### Sicherheit
- **Kiosk-Modus (Lock Task Mode)**: Verhindert das Verlassen der App
- **Device Owner Integration**: Vollständige Gerätekontrolle für maximale Sicherheit
- **PIN-geschützter Admin-Zugang**: Verschlüsselte Speicherung mit EncryptedSharedPreferences
- **Screenshot-Blockierung**: Automatische Erkennung und Löschung von Screenshots
- **URL-Filterung**: Nur vordefinierte Webseiten sind zugänglich
- **Tastensperrung**: Volume, Power, Screenshot-Tasten werden blockiert
- **Download/Upload-Blockierung**: Verhindert Daten-Transfers

### Benutzerführung
- **Auto-Start nach Boot**: App startet automatisch nach Geräte-Neustart
- **Inaktivitäts-Timer**: Kehrt nach 2 Minuten Inaktivität zur Startseite zurück
- **Auto-Refresh**: Aktualisiert die Seite alle 10 Minuten für aktuelle Inhalte
- **Home-Button**: Schnelle Navigation zum Hauptmenü
- **Vollbild-Modus**: Versteckt System-UI und Navigationsleiste

### Administration
- **Remote-Konfiguration**: Einstellungen per JSON-Server zentral verwalten
- **Admin-Geste**: 5x Tippen in untere linke Ecke öffnet Einstellungen
- **Flexible URL-Verwaltung**: Start-URL, Home-URL und erlaubte URLs konfigurierbar
- **Root-Unterstützung**: Erweiterte Sicherheitsfunktionen bei Root-Zugriff

## 📋 Voraussetzungen

- **Android Version**: Minimum SDK 26 (Android 8.0), Target SDK 36
- **Device Owner**: **Erforderlich** für vollständigen Kiosk-Modus (Lock Task)
- **Berechtigungen**: 
  - `INTERNET` - Für WebView
  - `RECEIVE_BOOT_COMPLETED` - Für Auto-Start
- **Optional**: Root-Zugriff für erweiterte Sicherheitsfunktionen

⚠️ **Wichtig**: Ohne Device Owner funktionieren wichtige Kiosk-Features (Lock Task Mode, Tastensperre) nicht vollständig!

## 🚀 Installation

### 1. Projekt bauen

```bash
git clone <repository-url>
cd KioskBrowser_Clean
./gradlew assembleDebug
```

Die APK finden Sie unter: `app/build/outputs/apk/debug/app-debug.apk`

### 2. App installieren

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Device Owner einrichten (ERFORDERLICH)

**⚠️ WICHTIG**: Muss auf einem **Factory-Reset Gerät OHNE Google-Konto** erfolgen!

#### Schritt-für-Schritt:

1. **Gerät zurücksetzen** (falls bereits eingerichtet):
   - Einstellungen → System → Zurücksetzen → Werkseinstellungen
   
2. **Setup OHNE Google-Konto durchführen**:
   - WLAN verbinden
   - Alle Google-Anmeldungen überspringen
   - Setup abschließen

3. **App installieren** (siehe oben)

4. **Device Owner setzen**:
```bash
adb shell dpm set-device-owner com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver
```

5. **Überprüfen**:
```bash
adb shell dumpsys device_policy | grep "Device Owner"
# Sollte zeigen: mDeviceOwner=AdminInfo...
```

✅ **Fertig!** Die App hat jetzt volle Kiosk-Kontrolle.

## ⚙️ Konfiguration

### Erste Einrichtung

1. **Admin-Zugang**: 5x schnell in die untere linke Ecke tippen
2. **Standard-PIN**: `12345` (bitte sofort ändern!)
3. **Einstellungen vornehmen**:
   - **Start-URL**: Seite, die beim Start und nach Inaktivität geladen wird
   - **Home-URL**: Ziel des Home-Buttons (z.B. Hauptmenü/Grid)
   - **Erlaubte URLs**: Eine URL pro Zeile (Start- und Home-URL werden automatisch hinzugefügt)
   - **Admin-PIN**: Mindestens 4-stellige Zahl

### Server-basierte Konfiguration

Die App kann Einstellungen von einem Remote-Server laden:

#### Server-Setup

Erstellen Sie eine `settings.json` auf Ihrem Webserver:

```json
{
  "start_url": "https://www.example.com",
  "home_url": "https://www.example.com/menu",
  "allowed_urls": [
    "https://www.example.com",
    "https://www.example.com/menu",
    "https://api.example.com"
  ]
}
```

Beispiel PHP-Server (enthalten in `server/`):
```php
<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

$settings = [
    'start_url' => 'https://www.example.com',
    'home_url' => 'https://www.example.com/menu',
    'allowed_urls' => [
        'https://www.example.com',
        'https://www.example.com/menu'
    ]
];

echo json_encode($settings);
?>
```

#### App konfigurieren

1. Admin-Einstellungen öffnen
2. "Von Server laden" aktivieren
3. Server-URL eingeben: `https://your-server.com/settings.json`
4. Speichern

Die App synchronisiert die Einstellungen nun automatisch alle 60 Sekunden.

## 🔧 ADB-Befehle

Siehe [ADB_COMMANDS.md](ADB_COMMANDS.md) für detaillierte ADB-Befehle zur Administration.

Wichtige Befehle:

```bash
# App starten
adb shell am start -n com.crownsmedia.kioskbrowser/.MainActivity

# App stoppen
adb shell am force-stop com.crownsmedia.kioskbrowser

# Einstellungen öffnen
adb shell am start -n com.crownsmedia.kioskbrowser/.SettingsActivity

# Device Owner entfernen
adb shell dpm remove-active-admin com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver
```

## 📱 Verwendung

### Normaler Betrieb
- Die App startet automatisch und lädt die konfigurierte Start-URL
- Bei Inaktivität (2 Min) kehrt die App zur Start-URL zurück
- Der Home-Button erscheint automatisch, wenn Sie nicht auf der Home-URL sind

### Admin-Zugang
1. 5x schnell in die **untere linke Ecke** tippen
2. PIN eingeben
3. Einstellungen anpassen
4. Speichern (lädt automatisch neu)

### App verlassen
- In Admin-Einstellungen: "App verlassen" Button
- Beendet Lock Task Mode und kehrt zur Android-Startseite zurück

### Device Owner entfernen
- In Admin-Einstellungen: "Device Owner entfernen" Button
- Danach kann die App normal deinstalliert werden
- Kiosk-Funktionen sind dann eingeschränkt

## 🏗️ Projektstruktur

```
app/src/main/java/com/crownsmedia/kioskbrowser/
├── MainActivity.kt              # Haupt-Kiosk-Browser
├── SettingsActivity.kt          # Admin-Konfiguration
├── CustomWebView.kt             # Erweiterte WebView
├── BootReceiver.kt              # Auto-Start nach Boot
├── MyDeviceAdminReceiver.kt     # Device Owner/Admin
├── ScreenshotObserver.kt        # Screenshot-Blockierung
└── ui/theme/                    # Compose UI Theme

server/
├── settings.php                 # Beispiel-Server für Einstellungen
├── settings.json                # JSON-Konfigurationsdatei
└── index.php                    # Server-Frontend
```

## 🔐 Sicherheitshinweise

### Mit Device Owner (EMPFOHLEN):
✅ Vollständiger Kiosk-Modus (Lock Task)  
✅ App kann nicht verlassen werden  
✅ Zugriff auf andere Apps blockiert  
✅ System-Tasten blockiert  
✅ Unbefugte URL-Aufrufe verhindert  
✅ Downloads und Uploads deaktiviert  
✅ Auto-Start nach Boot  

### Ohne Device Owner (EINGESCHRÄNKT):
⚠️ Benutzer kann über Recents-Taste (⎕) wechseln  
⚠️ Lock Task Mode nicht verfügbar  
✅ URL-Filterung funktioniert  
✅ PIN-Schutz funktioniert  
✅ Screenshot-Blockierung (bei Root)  

### Zusätzlich mit Root:
✅ Screenshots werden komplett blockiert (nicht nur gelöscht)  
✅ Navigationsleiste versteckt  
✅ Screenshot-Ordner schreibgeschützt  

### Einschränkungen:
- **Factory Reset**: Setzt alle Einstellungen zurück (Device Owner geht verloren)
- **Physischer Zugriff**: Sollte zu Power/Volume-Buttons beschränkt werden

### Best Practices:
1. **IMMER** Device Owner auf frischem Gerät einrichten (Factory Reset)
2. Device Owner **VOR** Google-Konto setzen
3. PIN sofort nach Installation ändern (Standard: `12345`)
4. Physischen Zugriff zu Buttons beschränken (Kiosk-Gehäuse verwenden)
5. Regelmäßig Remote-Einstellungen überprüfen
6. Server-Konfiguration über HTTPS bereitstellen
7. Bei wichtigen Deployments: Root-Zugriff für maximale Sicherheit

## 🛠️ Entwicklung

### Requirements
- Android Studio Hedgehog oder neuer
- Kotlin 1.9+
- Gradle 8.x
- JDK 11

### Dependencies
- AndroidX Core KTX
- AndroidX AppCompat
- Jetpack Compose (Material3)
- Security Crypto (EncryptedSharedPreferences)

### Build-Varianten
```bash
# Debug Build
./gradlew assembleDebug

# Release Build (signiert)
./gradlew assembleRelease

# Tests ausführen
./gradlew test
./gradlew connectedAndroidTest
```

## 🐛 Troubleshooting

### App wird beim Neustart nicht gestartet
- Prüfen: Boot-Permission gewährt?
- Prüfen: App ist nicht im Batteriesparmodus?
- Lösung: `adb shell dumpsys package com.crownsmedia.kioskbrowser` prüfen

### Device Owner kann nicht gesetzt werden
- **Fehler**: "Not allowed on this device" oder "already has an owner"
- **Ursache**: 
  - ❌ Google-Konto bereits angemeldet
  - ❌ Anderes MDM/Device Owner aktiv
  - ❌ Gerät ist Work Profile
- **Lösung**: 
  1. **Factory Reset** durchführen
  2. Setup **OHNE** Google-Konto
  3. Device Owner **VOR** jeglicher Google-Anmeldung setzen
  4. Keine anderen Device Admin Apps installieren

### Lock Task Mode funktioniert nicht
- **Ursache**: Kein Device Owner gesetzt
- **Symptom**: Nutzer kann über Recents-Taste (⎕) die App verlassen
- **Lösung**: Device Owner wie oben beschrieben einrichten (siehe Schritt 3)

### Screenshots werden nicht blockiert
- **Ursache**: Kein Root-Zugriff
- **Info**: Screenshots werden erkannt und automatisch gelöscht (mit Delay)
- **Lösung**: Root-Zugriff gewähren für vollständige Blockierung

### Server-Einstellungen werden nicht geladen
- Prüfen: Server-URL erreichbar? (im Browser testen)
- Prüfen: JSON-Format korrekt?
- Prüfen: CORS-Header gesetzt? (`Access-Control-Allow-Origin: *`)
- Log anschauen: `adb logcat | grep ServerSync`

## 📄 Lizenz

Dieses Projekt ist unter der MIT-Lizenz lizenziert - siehe [LICENSE](LICENSE) Datei für Details.

```
Copyright (c) 2026 Crowns Media

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 👥 Autor

**Crowns Media**  
Kiosk Browser - Android Kiosk Solution v1.0

## 🙏 Hinweise

- Diese App ist für kommerzielle und private Nutzung frei verwendbar
- Beiträge (Pull Requests) sind willkommen
- Bei Problemen bitte Issues erstellen
- Für Support: Siehe Repository-Kontaktinformationen

---

**Version**: 1.0  
**Letzte Aktualisierung**: Februar 2026  
**Status**: Production Ready ✅
