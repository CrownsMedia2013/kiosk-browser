<?php
// Router für PHP Development Server
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$file = __DIR__ . $path;

// Wenn Datei existiert und kein Verzeichnis ist
if (file_exists($file) && !is_dir($file)) {
    // PHP-Datei ausführen
    if (pathinfo($file, PATHINFO_EXTENSION) === 'php') {
        require $file;
        return true;
    }
    // Andere Dateien direkt ausliefern
    return false;
}

// Fallback auf index.php
if (is_dir($file) && file_exists($file . '/index.php')) {
    require $file . '/index.php';
    return true;
}

// 404
http_response_code(404);
echo '404 Not Found';
return true;
