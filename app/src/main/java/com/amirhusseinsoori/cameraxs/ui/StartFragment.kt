package com.amirhusseinsoori.cameraxs.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.amirhusseinsoori.cameraxs.R
import com.amirhusseinsoori.cameraxs.camera.VideoActivity
import com.amirhusseinsoori.cameraxs.databinding.FragmentStartBinding

class StartFragment : Fragment(R.layout.fragment_start) {
    lateinit var bind:FragmentStartBinding
    private val requestPermissionLauncherCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
               //startActivity(Intent(activity, CameraActivity::class.java))
                findNavController().navigate(R.id.action_startFragment_to_cameraFragment)
            } else {
                checkForPermissions()
            }
        }
//
    private val requestPermissionLauncherVideo =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startActivity(Intent(activity, VideoActivity::class.java))

            } else {
                checkForPermissions()
            }
        }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bind=FragmentStartBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
        bind.btnCamera.setOnClickListener {
            requestPermissionLauncherCamera.launch(android.Manifest.permission.CAMERA)
        }
        bind.btnVideo.setOnClickListener {
            requestPermissionLauncherVideo.launch(android.Manifest.permission.CAMERA)
        }
    }



    private fun checkForPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED

        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
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