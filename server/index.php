<?php
// Einstellungen laden
$settingsFile = __DIR__ . '/settings.json';
$settings = json_decode(file_get_contents($settingsFile), true);
$tiles = $settings['tiles'] ?? [];
$header = $settings['header'] ?? ['title' => '', 'subtitle' => ''];
$backgroundImage = $settings['background_image'] ?? '';
?>
<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kirschendorf Gellersen</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html { line-height: 1.25; }
        body {
            font-family: 'Segoe UI', sans-serif;
            <?php if ($backgroundImage): ?>
            background: url('<?= htmlspecialchars($backgroundImage) ?>') center/cover no-repeat fixed;
            <?php else: ?>
            background: #D0D4E8;
            <?php endif; ?>
            min-height: 100vh;
        }
        
        .header {
            text-align: center;
            padding: clamp(30px, 4.5vh, 100px) 20px;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: clamp(15px, 3vh, 40px);
        }
        .header h1 { 
            font-size: clamp(24px, 4vw, 56px); 
            color: #000;
            background: white;
            padding: clamp(15px, 2vw, 25px) clamp(30px, 4vw, 50px);
            display: inline-block;
            font-weight: 700;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .header p { 
            font-size: clamp(14px, 2vw, 32px); 
            color: #333;
            background: white;
            padding: clamp(12px, 2vw, 20px) clamp(25px, 3vw, 45px);
            display: inline-block;
            max-width: 70vw;
            line-height: 1.25;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            font-weight: 700;
        }
        .container { 
            padding-top: clamp(10px, 1.5vh, 25px);
            padding-left: clamp(40px, 5vw, 100px);
            padding-right: clamp(40px, 5vw, 100px);
            padding-bottom: clamp(60px, 10vh, 150px);
        }
        .grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: clamp(20px, 3vw, 50px);
            max-width: clamp(800px, 70vw, 1400px);
            margin: 0 auto;
        }
        .tile {
            text-decoration: none;
            display: flex;
            flex-direction: column;
            box-shadow: 0 4px 15px rgba(0,0,0,0.2);
            transition: transform 0.2s, box-shadow 0.2s;
            overflow: hidden;
        }
        .tile:hover { 
            transform: scale(1.02);
            box-shadow: 0 8px 25px rgba(0,0,0,0.3);
        }
        .tile-image {
            aspect-ratio: 1 / 1;
            background-size: cover;
            background-position: center;
        }
        .tile-title {
            background: rgba(0,0,0,0.85);
            color: white;
            padding: clamp(12px, 2vh, 25px);
            font-size: clamp(14px, 2.2vw, 28px);
            font-weight: 600;
            text-align: center;
            line-height: 1.3;
        }
        
        @media (max-width: 1024px) {
            .grid { gap: 20px; max-width: 90vw; }
            .container { padding-bottom: 30px; }
        }
        
        @media (max-width: 600px) { 
            .grid { grid-template-columns: 1fr; gap: 15px; }
            .header { padding: 15px 10px; gap: 10px; }
            .container { padding: 20px 15px 30px; }
        }
    </style>
</head>
<body>
    <div class="header">
        <h1><?= htmlspecialchars($header['title']) ?></h1>
        <p><?= htmlspecialchars($header['subtitle']) ?></p>
    </div>
    <div class="container">
        <div class="grid">
            <?php foreach ($tiles as $tile): ?>
            <a class="tile" href="<?= htmlspecialchars($tile['url']) ?>">
                <div class="tile-image" style="background-image: url('<?= htmlspecialchars($tile['background']) ?>');"></div>
                <div class="tile-title"><?= htmlspecialchars($tile['title']) ?></div>
            </a>
            <?php endforeach; ?>
        </div>
    </div>
</body>
</html>
