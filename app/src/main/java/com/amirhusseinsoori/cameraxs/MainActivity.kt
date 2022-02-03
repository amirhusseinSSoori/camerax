package com.amirhusseinsoori.cameraxs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amirhusseinsoori.cameraxs.camera.CameraActivity
import com.amirhusseinsoori.cameraxs.camera.VideoActivity
import com.amirhusseinsoori.cameraxs.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncherCamera =
        registerForActivityResult(RequestPermission()) { isGranted ->
            if (isGranted) {
                startActivity(Intent(this, CameraActivity::class.java))

            } else {
                checkForPermissions()
            }
        }

    private val requestPermissionLauncherVideo =
        registerForActivityResult(RequestPermission()) { isGranted ->
            if (isGranted) {
                startActivity(Intent(this, VideoActivity::class.java))

            } else {
                checkForPermissions()
            }
        }


    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCamera.setOnClickListener {
            requestPermissionLauncherCamera.launch(android.Manifest.permission.CAMERA)
        }
        binding.btnVideo.setOnClickListener {
            requestPermissionLauncherVideo.launch(android.Manifest.permission.CAMERA)
        }
    }


    private fun checkForPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED

        ) {
            ActivityCompat.requestPermissions(
                this,
                listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).toTypedArray(), 10005
            )
            return false
        }
        return true
    }

}