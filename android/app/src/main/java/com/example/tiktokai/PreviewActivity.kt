package com.example.tiktokai

import android.os.Bundle
import android.widget.Button
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class PreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        val path=intent.getStringExtra("video_path")
        val view=findViewById<VideoView>(R.id.videoView)
        val close=findViewById<Button>(R.id.closePreview)
        close.setOnClickListener { finish() }
        if(path.isNullOrBlank() || !File(path).exists()) {
            finish()
            return
        }
        val controls=MediaController(this)
        controls.setAnchorView(view)
        view.setMediaController(controls)
        view.setVideoPath(path)
        view.setOnPreparedListener { it.isLooping=false; view.start() }
    }
}
