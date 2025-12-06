package org.videolan.vlc.gui.browser

import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.videolan.vlc.R

class ImageViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val imageUri = Uri.parse(intent.getStringExtra("image_uri"))
        val imageTitle = intent.getStringExtra("image_title")

        val imageView: ImageView = findViewById(R.id.image_view)
        val titleView: TextView = findViewById(R.id.title)
        val closeButton: ImageView = findViewById(R.id.close_button)

        titleView.text = imageTitle
        imageView.setImageURI(imageUri)

        closeButton.setOnClickListener {
            finish()
        }

        imageView.setOnClickListener {
            finish()
        }
    }
}