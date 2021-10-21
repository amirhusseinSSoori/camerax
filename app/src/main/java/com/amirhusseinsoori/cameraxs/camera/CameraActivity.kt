package com.amirhusseinsoori.cameraxs.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsCompat
import com.amirhusseinsoori.cameraxs.R
import com.amirhusseinsoori.cameraxs.databinding.ActivityCameraBinding
import com.amirhusseinsoori.cameraxs.databinding.ActivityMainBinding
import com.amirhusseinsoori.cameraxs.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

class CameraActivity : AppCompatActivity() {
    private val displayManager by lazy { getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    // An instance of a helper function to work with Shared Preferences
    private val prefs by lazy { SharedPrefsManager.newInstance(applicationContext) }

    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // A lazy instance of the current fragment's view binding


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

    // Selector showing is hdr enabled or not (will work, only if device's camera supports hdr on hardware level)
    private var hasHdr = false

    // Selector showing is there any selected timer and it's value (3s or 10s)
    private var selectedTimer = CameraTimer.OFF

    /**
     * A display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
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
    lateinit var binding: ActivityCameraBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        flashMode = prefs.getInt(KEY_FLASH, ImageCapture.FLASH_MODE_OFF)
        hasGrid = prefs.getBoolean(KEY_GRID, false)
        hasHdr = prefs.getBoolean(KEY_HDR, false)
        initViews()
        startCamera()
        displayManager.registerDisplayListener(displayListener, null)

        binding.run {
            viewFinder.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(v: View) =
                    displayManager.registerDisplayListener(displayListener, null)

                override fun onViewAttachedToWindow(v: View) =
                    displayManager.unregisterDisplayListener(displayListener)
            })
            btnTakePicture.setOnClickListener { takePicture() }

            btnSwitchCamera.setOnClickListener { toggleCamera() }
            btnTimer.setOnClickListener { selectTimer() }
            btnGrid.setOnClickListener { toggleGrid() }
            btnFlash.setOnClickListener { selectFlash() }
            btnCancelCamera.setOnClickListener { finish() }
            btnTimerOff.setOnClickListener { closeTimerAndSelect(CameraTimer.OFF) }
            btnTimer3.setOnClickListener { closeTimerAndSelect(CameraTimer.S3) }
            btnTimer10.setOnClickListener { closeTimerAndSelect(CameraTimer.S10) }
            btnFlashOff.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_OFF) }
            btnFlashOn.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_ON) }
            btnFlashAuto.setOnClickListener { closeFlashAndSelect(ImageCapture.FLASH_MODE_AUTO) }

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
    @Suppress("NON_EXHAUSTIVE_WHEN")
    private fun takePicture() = CoroutineScope(Dispatchers.Main).launch {
        // Show a timer based on user selection
        when (selectedTimer) {
            CameraTimer.S3  -> for (i in 3 downTo 1) {
                binding.tvCountDown.text = i.toString()
                delay(1000)
            }
            CameraTimer.S10 -> for (i in 10 downTo 1) {
                binding.tvCountDown.text = i.toString()
                delay(1000)
            }
        }
        binding.tvCountDown.text = ""
        captureImage()
    }
    private fun captureImage() {
        val localImageCapture = imageCapture ?: throw IllegalStateException("Camera initialization failed.")
        // Create output file to hold the image
        val photoFile = createFile(applicationContext)
        // Setup image capture metadata

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .build()



        localImageCapture.takePicture(
            outputOptions, // the options needed for the final image
            applicationContext.mainExecutor(), // the executor, on which the task will run
            object : ImageCapture.OnImageSavedCallback { // the callback, about the result of capture process
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    setResult(Activity.RESULT_OK)
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    // This function is called if there is an errors during capture process
                    val msg = "Photo capture failed: ${exception.message}"
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, msg)
                    exception.printStackTrace()
                }
            }
        )
    }
    private fun startCamera() {
        // This is the CameraX PreviewView where the camera will be rendered
        val viewFinder = binding.viewFinder

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: InterruptedException) {
                Toast.makeText(applicationContext, "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            } catch (e: ExecutionException) {
                Toast.makeText(applicationContext, "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            }

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

            // The Configuration of image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) // setting to have pictures with highest quality possible (may be slow)
                .setFlashMode(flashMode) // set capture flash
                .setTargetAspectRatio(aspectRatio) // set the capture aspect ratio
                .setTargetRotation(rotation) // set the capture rotation
                .build()

            // The Configuration of image analyzing
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio) // set the analyzer aspect ratio
                .setTargetRotation(rotation) // set the analyzer rotation
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // in our analysis, we care about the latest image
                .build()
                .also { setLuminosityAnalyzer(it) }

            // Unbind the use-cases before rebinding them
            localCameraProvider.unbindAll()
            // Bind all use cases to the camera with lifecycle
            bindToLifecycle(localCameraProvider, viewFinder)
        }, ContextCompat.getMainExecutor(applicationContext))
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
    private fun selectTimer() = binding.llTimerOptions.circularReveal(binding.btnTimer)

    /**
     * Create some initial states
     * */
    private fun initViews() {
        binding.btnGrid.setImageResource(if (hasGrid) R.drawable.ic_grid_on else R.drawable.ic_grid_off)
        binding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE
        adjustInsets()
    }

    /**
     * This methods adds all necessary margins to some views based on window insets and screen orientation
     * */
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
    @SuppressLint("RestrictedApi")
    fun toggleCamera() = binding.btnSwitchCamera.toggleButton(
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
    /**
     * This function is called from XML view via Data Binding to select a timer
     *  possible values are OFF, S3 or S10
     *  circularClose() function is an Extension function which is adding circular close
     * */
    private fun closeTimerAndSelect(timer: CameraTimer) =
        binding.llTimerOptions.circularClose(binding.btnTimer) {
            selectedTimer = timer
            binding.btnTimer.setImageResource(
                when (timer) {
                    CameraTimer.S3  -> R.drawable.ic_timer_3
                    CameraTimer.S10 -> R.drawable.ic_timer_10
                    CameraTimer.OFF -> R.drawable.ic_timer_off
                }
            )
        }
    private fun closeFlashAndSelect(@ImageCapture.FlashMode flash: Int) =
        binding.llFlashOptions.circularClose(binding.btnFlash) {
            flashMode = flash
            binding.btnFlash.setImageResource(
                when (flash) {
                    ImageCapture.FLASH_MODE_ON  -> R.drawable.ic_flash_on
                    ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_flash_off
                    else                        -> R.drawable.ic_flash_auto
                }
            )
            imageCapture?.flashMode = flashMode
            prefs.putInt(KEY_FLASH, flashMode)
        }

    private fun setLuminosityAnalyzer(imageAnalysis: ImageAnalysis) {
        // Use a worker thread for image analysis to prevent glitches
        val analyzerThread = HandlerThread("LuminosityAnalysis").apply { start() }
        imageAnalysis.setAnalyzer(
            ThreadExecutor(Handler(analyzerThread.looper)),
            LuminosityAnalyzer()
        )
    }
    private fun bindToLifecycle(localCameraProvider: ProcessCameraProvider, viewFinder: PreviewView) {
        try {
            localCameraProvider.bindToLifecycle(
                this, // current lifecycle owner
                lensFacing, // either front or back facing
                preview, // camera preview use case
                imageCapture, // image capture use case
                imageAnalyzer, // image analyzer use case
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind use cases", e)
        }
    }
    companion object {
        private const val TAG = "CameraXDemo"

        const val KEY_FLASH = "sPrefFlashCamera"
        const val KEY_GRID = "sPrefGridCamera"
        const val KEY_HDR = "sPrefHDR"
        fun createFile(context: Context): File {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                Date()
            )
            val storageDir: File = context.filesDir
            return File.createTempFile(
                "${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
            )
        }
        private const val RATIO_4_3_VALUE = 4.0 / 3.0 // aspect ratio 4x3
        private const val RATIO_16_9_VALUE = 16.0 / 9.0 // aspect ratio 16x9
    }
}