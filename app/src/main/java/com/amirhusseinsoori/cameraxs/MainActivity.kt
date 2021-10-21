package com.amirhusseinsoori.cameraxs

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.inputmethod.InputBinding
import com.amirhusseinsoori.cameraxs.camera.CameraActivity
import com.amirhusseinsoori.cameraxs.camera.VideoActivity
import com.amirhusseinsoori.cameraxs.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCamera.setOnClickListener {
           startActivity(Intent(this,CameraActivity::class.java))
        }
        binding.btnVideo.setOnClickListener {
            startActivity(Intent(this,VideoActivity::class.java))
        }
    }
}