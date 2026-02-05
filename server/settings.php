<?php
session_start();

$settingsFile = __DIR__ . '/settings.json';
$settings = json_decode(file_get_contents($settingsFile), true);
$message = '';
$messageType = '';

// Admin-PIN aus settings.json
$adminPin = $settings['admin_pin'] ?? '1234';

// Login prüfen
if (!isset($_SESSION['admin_logged_in'])) {
    if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['pin'])) {
        if ($_POST['pin'] === $adminPin) {
            $_SESSION['admin_logged_in'] = true;
        } else {
            $message = 'Falscher PIN!';
            $messageType = 'danger';
        }
    }
    
    if (!isset($_SESSION['admin_logged_in'])) {
        // Login-Formular anzeigen
        ?>
        <!DOCTYPE html>
        <html lang="de">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Admin Login</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { 
                    font-family: 'Segoe UI', sans-serif; 
                    background: linear-gradient(180deg, #E8EAF6 0%, #C5CAE9 100%);
                    display: flex; 
                    align-items: center; 
                    justify-content: center; 
                    min-height: 100vh;
                }
                .login-card {
                    background: white;
                    padding: 40px;
                    border-radius: 16px;
                    box-shadow: 0 8px 24px rgba(0,0,0,0.15);
                    max-width: 400px;
                    width: 90%;
                }
                h2 { color: #1A237E; margin-bottom: 24px; text-align: center; }
                .alert { 
                    padding: 12px; 
                    margin-bottom: 20px; 
                    border-radius: 8px; 
                    background: #FFEBEE; 
                    color: #C62828; 
                    text-align: center;
                }
                input { 
                    width: 100%; 
                    padding: 16px; 
                    border: 2px solid #E0E0E0; 
                    border-radius: 8px; 
                    font-size: 18px; 
                    margin-bottom: 20px;
                    text-align: center;
                    letter-spacing: 2px;
                }
                button { 
                    width: 100%; 
                    padding: 16px; 
                    background: #1A237E; 
                    color: white; 
                    border: none; 
                    border-radius: 8px; 
                    font-size: 16px; 
                    font-weight: 600;
                    cursor: pointer;
                    transition: background 0.3s;
                }
                button:hover { background: #283593; }
                .back-link { text-align: center; margin-top: 20px; }
                .back-link a { color: #1A237E; text-decoration: none; }
            </style>
        </head>
        <body>
            <div class="login-card">
                <h2>Admin Login</h2>
                <?php if ($message): ?>
                    <div class="alert"><?= htmlspecialchars($message) ?></div>
                <?php endif; ?>
                <form method="POST">
                    <input type="password" name="pin" placeholder="Admin PIN" pattern="[0-9]*" inputmode="numeric" autofocus required>
                    <button type="submit">Anmelden</button>
                </form>
                <div class="back-link">
                    <a href="index.php">← Zurück zur Startseite</a>
                </div>
            </div>
        </body>
        </html>
        <?php
        exit;
    }
}

// Logout
if (isset($_GET['logout'])) {
    session_destroy();
    header('Location: settings.php');
    exit;
}

// Einstellungen speichern
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['save_settings'])) {
    $newSettings = [
        'start_url' => trim($_POST['start_url']),
        'allowed_urls' => array_filter(array_map('trim', explode("\n", $_POST['allowed_urls']))),
        'admin_pin' => trim($_POST['admin_pin']),
        'background_image' => trim($_POST['background_image']),
        'header' => [
            'title' => trim($_POST['header_title']),
            'subtitle' => trim($_POST['header_subtitle'])
        ],
        'tiles' => []
    ];
    
    if (isset($_POST['tile_title'])) {
        for ($i = 0; $i < count($_POST['tile_title']); $i++) {
            if (!empty($_POST['tile_title'][$i]) && !empty($_POST['tile_url'][$i])) {
                $newSettings['tiles'][] = [
                    'title' => trim($_POST['tile_title'][$i]),
                    'url' => trim($_POST['tile_url'][$i]),
                    'background' => trim($_POST['tile_background'][$i])
                ];
            }
        }
    }
    
    if (!empty($newSettings['start_url']) && !in_array($newSettings['start_url'], $newSettings['allowed_urls'])) {
        array_unshift($newSettings['allowed_urls'], $newSettings['start_url']);
    }
    
    if (file_put_contents($settingsFile, json_encode($newSettings, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE))) {
        $settings = $newSettings;
        $message = '✓ Einstellungen gespeichert!';
        $messageType = 'success';
    } else {
        $message = '✗ Fehler beim Speichern!';
        $messageType = 'danger';
    }
}
?>
<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kiosk Einstellungen</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Segoe UI', sans-serif; 
            background: #F5F5F5;
            padding: 20px;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        .header { 
            display: flex; 
            justify-content: space-between; 
            align-items: center; 
            margin-bottom: 30px;
            background: white;
            padding: 20px;
            border-radius: 12px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        h1 { color: #1A237E; font-size: 28px; }
        .buttons { display: flex; gap: 10px; }
        .btn { 
            padding: 12px 24px; 
            border: none; 
            border-radius: 8px; 
            font-size: 14px; 
            font-weight: 600;
            cursor: pointer;
            text-decoration: none;
            display: inline-block;
            transition: all 0.3s;
        }
        .btn-primary { background: #1A237E; color: white; }
        .btn-primary:hover { background: #283593; }
        .btn-default { background: #E0E0E0; color: #424242; }
        .btn-danger { background: #C62828; color: white; }
        .alert { 
            padding: 16px; 
            margin-bottom: 20px; 
            border-radius: 8px; 
            font-weight: 500;
        }
        .alert-success { background: #C8E6C9; color: #2E7D32; }
        .alert-danger { background: #FFCDD2; color: #C62828; }
        .card { 
            background: white; 
            padding: 24px; 
            border-radius: 12px; 
            margin-bottom: 24px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .card h2 { color: #1A237E; margin-bottom: 20px; font-size: 20px; }
        .form-group { margin-bottom: 20px; }
        label { 
            display: block; 
            margin-bottom: 8px; 
            color: #424242; 
            font-weight: 600;
            font-size: 14px;
        }
        input, textarea { 
            width: 100%; 
            padding: 12px; 
            border: 2px solid #E0E0E0; 
            border-radius: 8px; 
            font-size: 14px;
            font-family: inherit;
        }
        input:focus, textarea:focus { 
            outline: none; 
            border-color: #1A237E;
        }
        textarea { resize: vertical; }
        .tile-card {
            background: #F5F5F5;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 16px;
        }
        .tile-card h3 { color: #424242; margin-bottom: 16px; font-size: 16px; }
        .grid-2 { 
            display: grid; 
            grid-template-columns: repeat(2, 1fr); 
            gap: 16px;
        }
        @media (max-width: 768px) {
            .grid-2 { grid-template-columns: 1fr; }
            .header { flex-direction: column; gap: 15px; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>⚙️ Kiosk Einstellungen</h1>
            <div class="buttons">
                <a href="index.php" class="btn btn-default">← Startseite</a>
                <a href="?logout=1" class="btn btn-danger">Logout</a>
            </div>
        </div>
        
        <?php if ($message): ?>
            <div class="alert alert-<?= $messageType ?>"><?= htmlspecialchars($message) ?></div>
        <?php endif; ?>
        
        <form method="POST">
            <input type="hidden" name="save_settings" value="1">
            
            <div class="card">
                <h2>📱 Allgemeine Einstellungen</h2>
                
                <div class="form-group">
                    <label>Start-URL</label>
                    <input type="url" name="start_url" value="<?= htmlspecialchars($settings['start_url'] ?? '') ?>" required>
                </div>
                
                <div class="form-group">
                    <label>Admin PIN</label>
                    <input type="text" name="admin_pin" value="<?= htmlspecialchars($settings['admin_pin'] ?? '1234') ?>" pattern="[0-9]{4,}" required>
                </div>
                
                <div class="form-group">
                    <label>Hintergrundbild (Dateiname aus /images/)</label>
                    <input type="text" name="background_image" value="<?= htmlspecialchars($settings['background_image'] ?? '') ?>" placeholder="z.B. ./images/background.jpg">
                    <small style="display: block; margin-top: 5px; color: #757575;">Leer lassen für Standardfarbe</small>
                </div>
                
                <div class="form-group">
                    <label>Erlaubte URLs (eine pro Zeile)</label>
                    <textarea name="allowed_urls" rows="5"><?= htmlspecialchars(implode("\n", $settings['allowed_urls'] ?? [])) ?></textarea>
                </div>
            </div>
            
            <div class="card">
                <h2>📝 Kopfzeile</h2>
                
                <div class="form-group">
                    <label>Titel</label>
                    <input type="text" name="header_title" value="<?= htmlspecialchars($settings['header']['title'] ?? '') ?>">
                </div>
                
                <div class="form-group">
                    <label>Untertitel</label>
                    <textarea name="header_subtitle" rows="2"><?= htmlspecialchars($settings['header']['subtitle'] ?? '') ?></textarea>
                </div>
            </div>
            
            <div class="card">
                <h2>🎨 Kacheln (2x3 Grid)</h2>
                
                <?php 
                $tiles = $settings['tiles'] ?? [];
                for ($i = 0; $i < 6; $i++): 
                    $tile = $tiles[$i] ?? ['title' => '', 'url' => '', 'background' => ''];
                ?>
                <div class="tile-card">
                    <h3>Kachel <?= $i + 1 ?></h3>
                    <div class="grid-2">
                        <div class="form-group">
                            <label>Titel</label>
                            <input type="text" name="tile_title[]" value="<?= htmlspecialchars($tile['title']) ?>">
                        </div>
                        <div class="form-group">
                            <label>URL</label>
                            <input type="url" name="tile_url[]" value="<?= htmlspecialchars($tile['url']) ?>">
                        </div>
                        <div class="form-group" style="grid-column: 1 / -1;">
                            <label>Hintergrundbild (Dateiname aus /images/)</label>
                            <input type="text" name="tile_background[]" value="<?= htmlspecialchars($tile['background']) ?>" placeholder="z.B. tile1.jpg">
                        </div>
                    </div>
                </div>
                <?php endfor; ?>
                
                <p style="color: #757575; font-size: 14px; margin-top: 16px;">
                    💡 Lade quadratische Hintergrundbilder in den Ordner <code>/images/</code> hoch (z.B. tile1.jpg bis tile6.jpg).
                </p>
            </div>
            
            <button type="submit" class="btn btn-primary" style="width: 100%; padding: 16px; font-size: 16px;">
                💾 Speichern
            </button>
        </form>
    </div>
</body>
</html>
