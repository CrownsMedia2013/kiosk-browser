# ADB Befehle für Kiosk-App Device Owner Management

## Voraussetzungen
```bash
# ADB Verbindung herstellen (bereits erledigt)
adb connect <IP-ADRESSE>:5555

# Verbindung prüfen
adb devices
```

## 1. App Installieren/Aktualisieren
```bash
# APK installieren
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Oder bei Release-Build
adb install -r app/build/outputs/apk/release/app-release.apk

# Force install (überschreibt)
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

## 2. Device Owner Registrierung

### WICHTIG: Vor der Registrierung
```bash
# Alle Benutzerkonten vom Gerät entfernen (Factory Reset oder Settings)
# Device Owner kann nur auf "frischem" Gerät gesetzt werden
```

### Device Owner setzen
```bash
# Standard-Methode
adb shell dpm set-device-owner com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver

# Alternative Methode (bei manchen Geräten nötig)
adb shell dpm set-device-owner com.crownsmedia.kioskbrowser/com.crownsmedia.kioskbrowser.MyDeviceAdminReceiver
```

### Device Owner Status prüfen
```bash
# Aktuellen Device Owner anzeigen
adb shell dumpsys device_policy | grep "Device Owner"

# Detaillierte Infos
adb shell dpm list-owners
```

## 3. Device Owner Entfernen

```bash
# Device Owner entfernen (vor Deinstallation!)
adb shell dpm remove-active-admin com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver

# Force removal
adb shell pm clear com.android.devicepolicy
```

## 4. App Deinstallieren

```bash
# Standard-Deinstallation
adb uninstall com.crownsmedia.kioskbrowser

# Falls Device Owner gesetzt ist, erst entfernen (siehe #3)
# Dann deinstallieren:
adb uninstall com.crownsmedia.kioskbrowser
```

## 5. Lock Task Mode testen

```bash
# Lock Task Mode Status prüfen
adb shell dumpsys activity | grep "mLockTaskModeState"

# App stoppen (zum Testen)
adb shell am force-stop com.crownsmedia.kioskbrowser

# App neu starten
adb shell am start -n com.crownsmedia.kioskbrowser/.MainActivity
```

## 6. Root-Befehle testen

```bash
# Root Shell öffnen
adb shell
su

# Screenshot-Services prüfen
pm list packages | grep screenshot

# Navigation Bar Status
settings get global policy_control
settings get secure navigation_bar_show
```

## 7. Debugging

```bash
# Logcat live anzeigen
adb logcat | grep -i "com.crownsmedia.kioskbrowser"

# Nur Root-Logs
adb logcat | grep -i "RootCommand\|RootCheck"

# Nur Navigation Bar Logs
adb logcat | grep -i "NavBar"

# Nur Screenshot-Logs
adb logcat | grep -i "Security\|Screenshot"

# Alle Logs in Datei speichern
adb logcat > kiosk_logs.txt
```

## 8. Factory Reset Vorbereitung

```bash
# Vor Factory Reset: Device Owner entfernen
adb shell dpm remove-active-admin com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver

# Factory Reset durchführen (ADB)
adb shell recovery --wipe_data

# Oder im Recovery Mode (Hardware-Tasten)
# Volume Up + Power (gerätespezifisch)
```

## 9. Schnell-Workflow (Entwicklung)

```bash
# 1. Alte Version deinstallieren
adb shell dpm remove-active-admin com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver
adb uninstall com.crownsmedia.kioskbrowser

# 2. Neue Version installieren
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Device Owner setzen
adb shell dpm set-device-owner com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver

# 4. App starten
adb shell am start -n com.crownsmedia.kioskbrowser/.MainActivity

# 5. Logs verfolgen
adb logcat | grep -i "com.crownsmedia.kioskbrowser"
```

## 10. Troubleshooting

### "Not allowed to set the device owner" Fehler
```bash
# Lösung 1: Alle Accounts entfernen
adb shell pm list users
# Settings → Accounts → Alle entfernen

# Lösung 2: Factory Reset
adb shell recovery --wipe_data

# Lösung 3: Device Owner XML manuell setzen (Root nötig)
adb root
adb shell
echo '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<device-owner package="com.crownsmedia.kioskbrowser" name="" component="com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver" />' > /data/system/device_owner_2.xml
reboot
```

### App stürzt beim Start ab
```bash
# Permissions prüfen
adb shell dumpsys package com.crownsmedia.kioskbrowser | grep permission

# Cache leeren
adb shell pm clear com.crownsmedia.kioskbrowser

# Neu installieren
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```

### Root-Befehle funktionieren nicht
```bash
# Root-Zugriff prüfen
adb shell
su
id
# Sollte "uid=0(root)" zeigen

# Wenn nicht: SuperSU/Magisk App prüfen
```

## Nützliche Aliases (Optional)

Füge zu deiner Shell-Konfiguration hinzu (`.bashrc`, `.zshrc`):

```bash
alias kiosk-install='adb install -r app/build/outputs/apk/debug/app-debug.apk'
alias kiosk-uninstall='adb shell dpm remove-active-admin com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver && adb uninstall com.crownsmedia.kioskbrowser'
alias kiosk-owner='adb shell dpm set-device-owner com.crownsmedia.kioskbrowser/.MyDeviceAdminReceiver'
alias kiosk-start='adb shell am start -n com.crownsmedia.kioskbrowser/.MainActivity'
alias kiosk-logs='adb logcat | grep -i "com.crownsmedia.kioskbrowser"'
alias kiosk-reset='kiosk-uninstall && kiosk-install && kiosk-owner && kiosk-start'
```
