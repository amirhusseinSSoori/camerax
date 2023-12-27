package com.amirhusseinsoori.cameraxs.camera

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.animation.doOnCancel
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.amirhusseinsoori.cameraxs.R
import com.amirhusseinsoori.cameraxs.databinding.ActivityVideoBinding
import com.amirhusseinsoori.cameraxs.util.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

class VideoActivity : AppCompatActivity() {
    lateinit var binding:ActivityVideoBinding
    private val displayManager by lazy { getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }


    // An instance of a helper function to work with Shared Preferences
    private val prefs by lazy { SharedPrefsManager.newInstance(applicationContext) }

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture? = null

    private var displayId = -1

    // Selector showing which camera is selected (front or back)
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA

    // Selector showing which flash mode is selected (on, off or auto)
    private var flashMode by Delegates.observable(ImageCapture.FLASH_MODE_OFF) { _, _, new ->
        binding.btnFlash.setImageResource(
            when (new) {
                ImageCapture.FLASH_MODE_ON   -> R.drawable.ic_flash_on
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                else                         -> R.drawable.ic_flash_off
            }
        )
    }

    // Selector showing is grid enabled or not
    private var hasGrid = false

    // Selector showing is flash enabled or not
    private var isTorchOn = false

    // Selector showing is recording currently active
    private var isRecording = false

    private val animateRecord by lazy {
        ObjectAnimator.ofFloat(binding.btnRecordVideo, View.ALPHA, 1f, 0.5f).apply {
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            doOnCancel { binding.btnRecordVideo.alpha = 1f }
        }
    }

    private fun initViews() {
        binding.btnGrid.setImageResource(if (hasGrid) R.drawable.ic_grid_on else R.drawable.ic_grid_off)
        binding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE

        adjustInsets()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
        override fun onDisplayChanged(displayId: Int) = let { _ ->
            if (displayId == this@VideoActivity.displayId) {
                preview?.targetRotation = display!!.rotation
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hasGrid = prefs.getBoolean(KEY_GRID, false)
        initViews()

        displayManager.registerDisplayListener(displayListener, null)
        startCamera()
        binding.run {
            viewFinder.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(v: View) =
                    displayManager.registerDisplayListener(displayListener, null)

                override fun onViewAttachedToWindow(v: View) =
                    displayManager.unregisterDisplayListener(displayListener)
            })
            binding.btnRecordVideo.setOnClickListener { recordVideo() }
            btnCancelVideoCamera.setOnClickListener { finish() }
            btnSwitchCamera.setOnClickListener { toggleCamera() }
            btnGrid.setOnClickListener { toggleGrid() }
            btnFlash.setOnClickListener { toggleFlash() }
        }
    }
    private fun toggleFlash() = binding.btnFlash.toggleButton(
        flag = flashMode == ImageCapture.FLASH_MODE_ON,
        rotationAngle = 360f,
        firstIcon = R.drawable.ic_flash_off,
        secondIcon = R.drawable.ic_flash_on
    ) { flag ->
        isTorchOn = flag
        flashMode = if (flag) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        camera?.cameraControl?.enableTorch(flag)
    }

    private fun toggleGrid() = binding.btnGrid.toggleButton(
        flag = hasGrid,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_grid_off,
        secondIcon = R.drawable.ic_grid_on
    ) { flag ->
        hasGrid = flag
        prefs.putBoolean(KEY_GRID, flag)
        binding.groupGridLines.visibility = if (flag) View.VISIBLE else View.GONE
    }

    private fun toggleCamera() = binding.btnSwitchCamera.toggleButton(
        flag = lensFacing == CameraSelector.DEFAULT_BACK_CAMERA,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_outline_camera_rear,
        secondIcon = R.drawable.ic_outline_camera_front,
    ) {
        lensFacing = if (it) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        startCamera()
    }

    @SuppressLint("MissingPermission", "RestrictedApi")
    private fun recordVideo() {
        val localVideoCapture = videoCapture ?: throw IllegalStateException("Camera initialization failed.")

        val videioFile = createVideoFile(applicationContext)
        val outputOptions = VideoCapture.OutputFileOptions.Builder(videioFile).build()

        if (!isRecording) {
            animateRecord.start()
            binding.countVideo.start()
            localVideoCapture.startRecording(
                outputOptions, // the options needed for the final video
                mainExecutor(), // the executor, on which the task will run
                object : VideoCapture.OnVideoSavedCallback { // the callback after recording a video
                    override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {

                        binding.countVideo.stop()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }

                    override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                        // This function is called if there is an error during recording process
                        animateRecord.cancel()
                        val msg = "Video capture failed: $message"
                        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        Log.e(TAG, msg)
                        cause?.printStackTrace()
                    }
                })
        } else {
            animateRecord.cancel()
            localVideoCapture.stopRecording()
        }
        isRecording = !isRecording
    }

    private fun startCamera() {
        // This is the Texture View where the camera will be rendered
        val viewFinder = binding.viewFinder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // The display information
            val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
            // The ratio for the output image and preview
            val aspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
            // The display rotation
            val rotation = viewFinder.display.rotation

            val localCameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

            // The Configuration of camera preview
            preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio) // set the camera aspect ratio
                .setTargetRotation(rotation) // set the camera rotation
                .build()

            val videoCaptureConfig = VideoCapture.DEFAULT_CONFIG.config // default config for video capture
            // The Configuration of video capture
            videoCapture = VideoCapture.Builder
                .fromConfig(videoCaptureConfig)
                .build()

            localCameraProvider.unbindAll() // unbind the use-cases before rebinding them

            try {
                // Bind all use cases to the camera with lifecycle
                camera = localCameraProvider.bindToLifecycle(
                    this, // current lifecycle owner
                    lensFacing, // either front or back facing
                    preview, // camera preview use case
                    videoCapture, // video capture use case
                )

                // Attach the viewfinder's surface provider to preview use case
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)
            } catch (e: Exception) {
                Log.e("TAG", "Failed to bind use cases", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    companion object {
        private const val TAG = "CameraXDemo"

        const val KEY_GRID = "sPrefGridVideo"
        private fun createVideoFile(context: Context): File {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                Date()
            )
            val storageDir: File = context.filesDir
            return File.createTempFile(
                "${timeStamp}_", /* prefix */
                ".mp4", /* suffix */
                storageDir /* directory */
            )
        }

        private const val RATIO_4_3_VALUE = 4.0 / 3.0 // aspect ratio 4x3
        private const val RATIO_16_9_VALUE = 16.0 / 9.0 // aspect ratio 16x9
    }

    private fun adjustInsets() {
        window?.fitSystemWindows()
        binding.btnRecordVideo.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.bottomMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                view.endMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right
            }
        }
        binding.btnFlash.onWindowInsets { view, windowInsets ->
            view.topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
    }
}