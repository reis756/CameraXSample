package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import androidx.viewbinding.ViewBinding
import androidx.window.WindowManager
import com.android.example.cameraxbasic.KEY_EVENT_ACTION
import com.android.example.cameraxbasic.MainActivity
import com.android.example.cameraxbasic.R
import com.android.example.cameraxbasic.databinding.FragmentCameraBinding
import com.android.example.cameraxbasic.utils.SwipeGestureDetector
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class BaseFragment<B : ViewBinding> : Fragment() {

    open var displayId: Int = -1
    open var preview: Preview? = null
    open var camera: Camera? = null
    open var cameraProvider: ProcessCameraProvider? = null
    open lateinit var windowManager: WindowManager

    open val outputDirectory: File by lazy {
        MainActivity.getOutputDirectory(requireContext())
    }
    open lateinit var broadcastManager: LocalBroadcastManager

    abstract val binding: B
    abstract val volumeDownReceiver: BroadcastReceiver
    abstract val displayListener: DisplayManager.DisplayListener

    open val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    open lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()

        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    fun setupView() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        view?.let {
            broadcastManager = LocalBroadcastManager.getInstance(it.context)
            windowManager = WindowManager(it.context)
        }

        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        displayManager.registerDisplayListener(displayListener, null)
    }

    fun postViewFinder(viewFinder: PreviewView) {
        viewFinder.post {

            displayId = viewFinder.display.displayId

            updateCameraUi()

            setUpCamera()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun gestureListener(viewFinder: PreviewView, callback: () -> Unit) {
        val swipeGestureDetector = SwipeGestureDetector().apply {
            setSwipeCallback(left = {
                callback.invoke()
            })
        }

        val gestureDetectorCompat = GestureDetector(requireContext(), swipeGestureDetector)
        viewFinder.setOnTouchListener { v, event ->
            if (gestureDetectorCompat.onTouchEvent(event)) return@setOnTouchListener false
            return@setOnTouchListener true
        }
    }

    abstract fun updateCameraUi()

    abstract fun setUpCamera()

    abstract fun bindCameraUseCases()

    fun setGalleryThumbnail(imageButton: ImageButton, uri: Uri) {
        imageButton.post {
            imageButton.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

            Glide.with(imageButton)
                .load(uri)
                .apply(RequestOptions.circleCropTransform())
                .into(imageButton)
        }
    }

    open fun updateCameraSwitchButton(cameraSwitchButton: ImageButton) {
        try {
            cameraSwitchButton.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            cameraSwitchButton.isEnabled = false
        }
    }

    open fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    open fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    open fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    companion object {

        const val TAG = "CameraXBasic"
        const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val PHOTO_EXTENSION = ".jpg"
        const val VIDEO_EXTENSION = ".mp4"
        const val RATIO_4_3_VALUE = 4.0 / 3.0
        const val RATIO_16_9_VALUE = 16.0 / 9.0

        fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)
    }
}