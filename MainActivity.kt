package com.example.overlayscreencapture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val REQUEST_MEDIA_PROJECTION = 1001
    private val REQUEST_OVERLAY_PERMISSION = 1002

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val btnRequest = findViewById<Button>(R.id.btnRequestProjection)
        val btnStart = findViewById<Button>(R.id.btnStartOverlay)

        btnRequest.setOnClickListener {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
        }

        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
                    return@setOnClickListener
                }
            }
            startFloatingServiceIfReady(null, 0)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_MEDIA_PROJECTION -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // Start the overlay service and pass projection permission
                    startFloatingServiceIfReady(data, resultCode)
                    Toast.makeText(this, "Permission capture acceptée", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permission capture refusée", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_OVERLAY_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "Permission overlay accordée", Toast.LENGTH_SHORT).show()
                        // user still needs to grant projection via button
                    } else {
                        Toast.makeText(this, "Permission overlay refusée", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startFloatingServiceIfReady(data: Intent?, resultCode: Int) {
        val intent = Intent(this, FloatingWidgetService::class.java)
        if (data != null) {
            intent.putExtra("resultCode", resultCode)
            intent.putExtra("resultData", data)
        }
        startService(intent)
        finish()
    }
}
