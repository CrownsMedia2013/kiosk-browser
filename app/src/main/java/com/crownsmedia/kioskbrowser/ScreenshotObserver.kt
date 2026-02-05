package com.crownsmedia.kioskbrowser

import android.os.Environment
import android.os.FileObserver
import android.util.Log
import java.io.File

class ScreenshotObserver(
    private val onScreenshotDetected: (File) -> Unit
) {
    private val observers = mutableListOf<FileObserver>()
    
    private val screenshotPaths = listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots",
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Screenshots",
        Environment.getExternalStorageDirectory().absolutePath + "/Screenshots"
    )

    @Suppress("DEPRECATION")
    fun startWatching() {
        screenshotPaths.forEach { path ->
            try {
                val dir = File(path)
                if (!dir.exists()) dir.mkdirs()
                
                val observer = object : FileObserver(path, CREATE or MOVED_TO) {
                    override fun onEvent(event: Int, fileName: String?) {
                        if (fileName != null && (fileName.endsWith(".png") || fileName.endsWith(".jpg"))) {
                            val file = File(path, fileName)
                            Log.d("ScreenshotObserver", "Screenshot erkannt: ${file.absolutePath}")
                            onScreenshotDetected(file)
                        }
                    }
                }
                observer.startWatching()
                observers.add(observer)
                Log.d("ScreenshotObserver", "Überwache: $path")
            } catch (e: Exception) {
                Log.e("ScreenshotObserver", "Fehler bei Pfad $path: ${e.message}")
            }
        }
    }

    fun stopWatching() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}
