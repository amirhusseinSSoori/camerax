package com.amirhusseinsoori.cameraxs.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import com.amirhusseinsoori.cameraxs.R

import com.amirhusseinsoori.cameraxs.databinding.ActivityCameraBinding
import com.amirhusseinsoori.cameraxs.util.*
import im.vector.app.multipicker.camera.util.CameraConfiguration

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.properties.Delegates
import kotlin.random.Random




class CameraActivity : AppCompatActivity() {

    lateinit var binding: ActivityCameraBinding

    // Selector showing is grid enabled or not
    private var hasGrid = false

    private val displayManager by lazy { getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    // Selector showing is there any selected timer and it's value (3s or 10s)
    private var selectedTimer = CameraTimer.OFF

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private val prefs by lazy { SharedPrefsManager.newInstance(applicationContext) }
    private val cameraProvider by lazy {
        ProcessCameraProvider.getInstance(this)
    }

    // Selector showing which flash mode is selected (on, off or auto)
    private var flashMode by Delegates.observable(ImageCapture.FLASH_MODE_OFF) { _, _, new ->
        binding.btnFlash.setImageResource(
            when (new) {
                ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                else -> R.drawable.ic_flash_off
            }
        )
    }

    private fun initViews() {
        binding.btnGrid.setImageResource(if (hasGrid) R.drawable.ic_grid_on else R.drawable.ic_grid_off)
        binding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE
        adjustInsets()
    }

    private fun adjustInsets() {
        window?.fitSystemWindows()
        binding.btnTakePicture.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                view.bottomMargin =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            else view.endMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right
        }
        binding.btnTimer.onWindowInsets { view, windowInsets ->
            view.topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
    }

    private val executor by lazy {
        ContextCompat.getMainExecutor(this)
    }

    private val metadata by lazy {
        packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA).metaData
    }

    private val overlay by lazy {
        getConfigurationValue(CameraConfiguration.VIEW_FINDER_OVERLAY) as Int?
    }

    private val permissions by lazy {
        listOf(Manifest.permission.CAMERA)
    }

    private val permissionsRequestCode by lazy {
        Random.nextInt(0, 10000)
    }
    private var displayId = -1

    private fun getConfigurationValue(key: String): Any? = when {
        intent.extras?.containsKey(key) == true -> intent.extras?.get(key)
        metadata?.containsKey(key) == true -> metadata.get(key)
        else -> null
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
        override fun onDisplayChanged(displayId: Int) = let { _ ->
            if (displayId == this@CameraActivity.displayId) {
//                preview?.targetRotation = view.display.rotation
//                imageCapture?.targetRotation = view.display.rotation
//                imageAnalyzer?.targetRotation = view.display.rotation
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewFinder.addOnAttachStateChangeListener(object :
            View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(v: View) =
                displayManager.registerDisplayListener(displayListener, null)

            override fun onViewAttachedToWindow(v: View) =
                displayManager.unregisterDisplayListener(displayListener)
        })

        // Try to provide a seamless rotation for devices that support it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.attributes.rotationAnimation =
                WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
        }


        binding.btnSwitchCamera.setOnClickListener { toggleCamera() }
        binding.btnTimer.setOnClickListener { selectTimer() }
        binding.btnGrid.setOnClickListener { toggleGrid() }
        binding.btnFlash.setOnClickListener { selectFlash() }
        binding.btnCancelCamera.setOnClickListener { finish() }
        binding.btnTimerOff.setOnClickListener { closeTimerAndSelect(CameraTimer.OFF) }
        binding.btnTimer3.setOnClickListener { closeTimerAndSelect(CameraTimer.S3) }
        binding.btnTimer10.setOnClickListener { closeTimerAndSelect(CameraTimer.S10) }
        binding.btnFlashOff.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_OFF) }
        binding.btnFlashOn.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_ON) }
        binding.btnFlashAuto.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_AUTO) }
        // Operate on the viewfinder's thread to make sure it's ready
        binding.viewFinder.post { drawCameraControls() }
    }

    private fun closeFlashAndSelect(@ImageCapture.FlashMode flash: Int) =
        binding.llFlashOptions.circularClose(binding.btnFlash) {
            flashMode = flash
            binding.btnFlash.setImageResource(
                when (flash) {
                    ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                    ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
                    else -> R.drawable.ic_flash_auto
                }
            )
            imageCapture?.flashMode = flashMode
            prefs.putInt(KEY_FLASH, flashMode)
        }

    private fun selectTimer() = binding.llTimerOptions.circularReveal(binding.btnTimer)

    /** Reads and applies all custom configuration provided by the user of this activity */
    private fun applyUserConfiguration() {


        // If the user requested a specific lens facing, select it
        getConfigurationValue(CameraConfiguration.CAMERA_LENS_FACING)?.let {
            lensFacing = it as Int
        }

    }

    private fun selectFlash() = binding.llFlashOptions.circularReveal(binding.btnFlash)
    private fun toggleGrid() {
        binding.btnGrid.toggleButton(
            flag = hasGrid,
            rotationAngle = 180f,
            firstIcon = R.drawable.ic_grid_off,
            secondIcon = R.drawable.ic_grid_on,
        ) { flag ->
            hasGrid = flag
            prefs.putBoolean(KEY_GRID, flag)
            binding.groupGridLines.visibility = if (flag) View.VISIBLE else View.GONE
        }
    }

    private fun closeTimerAndSelect(timer: CameraTimer) =
        binding.llTimerOptions.circularClose(binding.btnTimer) {
            selectedTimer = timer
            binding.btnTimer.setImageResource(
                when (timer) {
                    CameraTimer.S3 -> R.drawable.ic_timer_3
                    CameraTimer.S10 -> R.drawable.ic_timer_10
                    CameraTimer.OFF -> R.drawable.ic_timer_off
                }
            )
        }


    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawCameraControls()
    }

    /** Volume down button receiver used to trigger shutter */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            // When the volume down button is pressed, simulate a shutter button click
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val shutter = findViewById<ImageButton>(R.id.btnTakePicture)
                shutter.simulateClick()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Sets cancel result code and exits the activity */
    private fun cancelAndFinish() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    /**
     * Method used to re-draw the camera UI controls, called every time configuration changes.
     */
    private fun drawCameraControls() {


        binding.btnTakePicture.setOnClickListener {

            // Disable all camera controls
            binding.btnTakePicture.isEnabled = false
            binding.btnSwitchCamera.isEnabled = false

            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->

                // Create output file to hold the image
                val photoFile = createFile(filesDir)

                // Setup image capture metadata
                val metadata = ImageCapture.Metadata().apply {
                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }

                // Create output options object which contains file + metadata
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                    .setMetadata(metadata)
                    .build()

                // Setup image capture listener which is triggered after photo has been taken
                imageCapture.takePicture(
                    outputOptions, executor, object : ImageCapture.OnImageSavedCallback {

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                            Log.d(TAG, "Image captured successfully: $savedUri")
                            setResult(Activity.RESULT_OK, Intent().apply {
                                putExtra(CameraConfiguration.IMAGE_URI, savedUri)
                            })
                            finish()
                        }

                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Error capturing image", exc)
                            cancelAndFinish()
                        }
                    })
            }
        }

        // Listener for button used to switch cameras
        binding.btnSwitchCamera.setOnClickListener {

            // Flip-flop the required lens facing
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }

            // Re-bind all use cases
            bindCameraUseCases()
        }

        // Apply user configuration every time controls are drawn
        applyUserConfiguration()
    }

    @SuppressLint("RestrictedApi")
    fun toggleCamera() = binding.btnSwitchCamera.toggleButton(
        flag = lensFacing == CameraSelector.LENS_FACING_BACK,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_outline_camera_rear,
        secondIcon = R.drawable.ic_outline_camera_front,
    ) {
        lensFacing = if (it) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        binding.viewFinder.post { drawCameraControls() }
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() = binding.viewFinder.post {

        cameraProvider.addListener(Runnable {
            // Camera provider is now guaranteed to be available
            val cameraProvider = cameraProvider.get()

            // Set up the view finder use case to display camera preview
            preview = Preview.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()
                .apply {
                    setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Set up the capture use case to allow users to take photos
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()

            // Create a new camera selector each time, enforcing lens facing
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            // Apply declared configs to CameraX using the same lifecycle owner
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner, cameraSelector, preview, imageCapture
            )

            // TODO: Use camera controls to implement touch-to-focus once PreviewView metering
            //  point factory is ready
        }, executor)
    }

    override fun onResume() {
        super.onResume()

        // Request permissions each time the app resumes, since they can be revoked at any time
        if (!hasPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), permissionsRequestCode
            )
        } else {
            drawCameraControls()
            bindCameraUseCases()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionsRequestCode && hasPermissions(this)) {
            bindCameraUseCases()
        } else {
            // Indicate that the user cancelled the action and exit if no permissions are granted
            cancelAndFinish()
        }
    }




    /** Override back-navigation to add a cancelled result extra */
    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }

    /** Convenience method used to check if all permissions required by this app are granted */
    private fun hasPermissions(context: Context) = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val TAG = CameraActivity::class.java.simpleName

        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        const val KEY_FLASH = "sPrefFlashCamera"
        const val KEY_GRID = "sPrefGridCamera"
        const val KEY_HDR = "sPrefHDR"
        /** Helper function used to create a timestamped file */
        private fun createFile(
            baseFolder: File,
            format: String = FILENAME,
            extension: String = PHOTO_EXTENSION
        ) = File(baseFolder, SimpleDateFormat(format, Locale.US)
            .format(System.currentTimeMillis()) + extension)
    }


}
