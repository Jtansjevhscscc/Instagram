package com.example.overlayscreencapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground to avoid being killed
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle("OverlayCapture")
            .setContentText("Service actif")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notification)

        if (floatingView == null) {
            showFloatingWidget()
        }

        // Obtain MediaProjection from intent extras if provided
        intent?.let {
            val resultCode = it.getIntExtra("resultCode", -1)
            val resultData = it.getParcelableExtra<Intent>("resultData")
            if (resultData != null && resultCode != -1) {
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mgr.getMediaProjection(resultCode, resultData)
            }
        }

        return START_STICKY
    }

    private fun showFloatingWidget() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_widget, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        windowManager.addView(floatingView, params)

        val image = floatingView?.findViewById<ImageView>(R.id.fabImage)
        image?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Short click -> capture screen
                        performCapture()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun performCapture() {
        if (mediaProjection == null) {
            Toast.makeText(this, "Donne d'abord la permission de capture depuis l'app principale", Toast.LENGTH_LONG).show()
            return
        }

        val metrics = resources.displayMetrics
        val density = metrics.densityDpi
        val screenSize = Point()
        windowManager.defaultDisplay.getRealSize(screenSize)
        val width = screenSize.x
        val height = screenSize.y

        imageReader = ImageReader.newInstance(width, height, 0x1, 2)

        val virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "screencap",
            width,
            height,
            density,
            0,
            imageReader!!.surface,
            null,
            null
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = android.graphics.Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // Crop to actual width
            val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height)

            // Do something with the bitmap
            analyzeImage(cropped)

            // cleanup
            imageReader?.close()
            mediaProjection?.stop()

        }, null)
    }

    private fun analyzeImage(bitmap: android.graphics.Bitmap) {
        // Placeholder: ici tu branches ton modèle TFLite ou ta requête serveur.
        // Pour le moment, on sauvegarde l'image et affiche un toast.
        try {
            val file = java.io.File(getExternalFilesDir(null), "last_capture.png")
            val fos = java.io.FileOutputStream(file)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fos)
            fos.flush()
            fos.close()
            Toast.makeText(this, "Capture enregistrée: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur sauvegarde: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("overlay_channel", "Overlay Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(chan)
        }
    }
}
