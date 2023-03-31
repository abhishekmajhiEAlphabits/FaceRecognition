package com.example.facerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Range
import android.util.Size
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    private lateinit var processCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var emotion: TextView
    private lateinit var cameraView: ImageView
    private lateinit var imageProcessor: ImageProcess
    var frameCounter = 0
    var lastFpsTimestamp = System.currentTimeMillis()
    val frameCount = 30

    //    private lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emotion = findViewById(R.id.mEmotion)
        cameraView = findViewById(R.id.cameraImage)
        imageProcessor = ImageProcess()

        if (allPermissionsGranted()) {
            init()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    @SuppressLint("RestrictedApi")
    private fun init() {
//        CameraX.initialize(this, Camera2Config.defaultConfig())
        processCameraProviderFuture = ProcessCameraProvider.getInstance(this)
        processCameraProviderFuture.addListener(Runnable {
            processCameraProvider = processCameraProviderFuture.get()
            viewFinder.post { setupCamera() }
        }, ContextCompat.getMainExecutor(this))

    }

    private fun takePhoto() {}

    private fun captureVideo() {}


    @SuppressLint("UnsafeOptInUsageError")
    private fun setupCamera() {
        processCameraProvider.unbindAll()
        val camera = processCameraProvider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_FRONT_CAMERA,
//            buildPreviewUseCase(),
//            buildImageCaptureUseCase()
            buildImageAnalysisUseCase()
        )


//        var camera2Extender = Camera2Config.
        val cameraControl = camera.cameraControl
        val camera2CameraControl = Camera2CameraControl.from(cameraControl)

        val captureRequestOptions = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF
            )
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(60, 60))
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO
            )
//            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
//            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF)
//            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 1)
//            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
////            .setCaptureRequestOption(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
//            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, 100)
//            .setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION,  40000000,)
//            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, 20400000)
            /*.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(_config.stream.value.fps, _config.stream.value.fps)
            )*/
            .build()
        camera2CameraControl.captureRequestOptions = captureRequestOptions
//        setupTapForFocus(camera.cameraControl)
    }


//    private fun buildPreviewUseCase(): Preview {
//        val display = viewFinder.display
//        val metrics = DisplayMetrics().also { display.getMetrics(it) }
//        val preview = Preview.Builder()
//            .setTargetRotation(display.rotation)
//            .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
//            .build()
//            .apply {
//                previewSurfaceProvider = viewFinder.previewSurfaceProvider
//            }
////        preview.previewSurfaceProvider = viewFinder.previewSurfaceProvider
//        return preview
//    }

    @SuppressLint("UnsafeExperimentalUsageError", "RestrictedApi")
    private fun buildImageAnalysisUseCase(): ImageAnalysis {
        val display = viewFinder.display
        val metrics = DisplayMetrics().also { display.getMetrics(it) }
        val analysis = ImageAnalysis.Builder()
//            .setTargetRotation(display.rotation)
//            .setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
//            .setImageQueueDepth(10)
//            .setTargetResolution(Size(1920, 1080))
            .setDefaultResolution(Size(1920, 1080))
//            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(
            Executors.newSingleThreadExecutor(),
            ImageAnalysis.Analyzer { imageProxy ->
//                Log.d("CameraFragment", "Image analysis result $imageProxy")
//                detect(imageProxy)
//                val imageBytes = imageProxyToByteArray(imageProxy)
                getResolution(imageProxy)
//                Log.d(TAG, "byte array : $imageBytes")
                imageProxy.use {
//                     Compute the FPS of the entire pipeline
                    if (++frameCounter % frameCount == 0) {
                        frameCounter = 0
                        val now = System.currentTimeMillis()
                        val delta = now - lastFpsTimestamp
                        val fps = 1000 * frameCount.toFloat() / delta
                        Log.d(TAG, "FPSS: ${"%.02f".format(fps)}")
                        lastFpsTimestamp = now
                    } else {

                    }
//                        Log.d("CameraFragment", "Image Bitmap ${image.toString()}")
                    try {
                        val img = imageProxy.toJpeg()
                        val imageBytes = ByteArray(img!!.remaining())
                        img.get(imageBytes)
                        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        Log.d(TAG, "bitmap : $img")
                        detectFaces(bmp)
//                        runOnUiThread(Runnable {
//                            kotlin.run {
//                                cameraView.setImageBitmap(bmp)
//                                cameraView.rotation = -90f
//                            }
//                        })
                    } catch (t: Throwable) {
                        Log.d("CameraFragment", "Error in getting img $t")
                    }
                }
//                imageProxy.close()
            })
        return analysis
    }

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    private fun getResolution(imageProxy: ImageProxy) {
        val image =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        Log.d(TAG, "${image.width} :: ${image.height}")
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    //returns image byte array
    fun imageProxyToByteArray(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

//    private fun setupTapForFocus(cameraControl: CameraControl) {
//        viewFinder.setOnTouchListener { _, event ->
//            if (event.action != MotionEvent.ACTION_UP) {
//                return@setOnTouchListener true
//            }
//
//            val textureView = viewFinder.getChildAt(0) as? TextureView
//                ?: return@setOnTouchListener true
////            val factory = TextureViewMeteringPointFactory(textureView)
////
////            val point = factory.createPoint(event.x, event.y)
////            val action = FocusMeteringAction.Builder.from(point).build()
////            cameraControl.startFocusAndMetering(action)
//            return@setOnTouchListener true
//        }
//    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                init()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
                //Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    private fun detect(imageProxy: ImageProxy) {
        Log.d("CameraFragment", "inside detect")
        val faceDetector = FaceDetection.getClient()
        val image =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
//        val image: InputImage = InputImage.fromBitmap(bitmap,0)
        Log.d(TAG, "${image.width} :: ${image.height}")
        val result: Task<List<Face>> = faceDetector.process(image)
        result.addOnSuccessListener { faces ->
            // Do something with the detected faces
            for (face in faces) {
                val smileProb = face.smilingProbability
                val leftEyeProb = face.leftEyeOpenProbability
                val rightEyeProb = face.rightEyeOpenProbability
                // Do something with the emotions
                Log.d(TAG, "smile : %$face $smileProb $leftEyeProb $rightEyeProb")
                emotion.text = face.toString()
            }
        }.addOnFailureListener { exception: Exception ->
            // Handle the error
            Log.d(TAG, exception.toString())
        }

    }

    fun detectFaces(imageBitmap: Bitmap) {
        val faceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.1f)
            .build()
        val faceDetector = FaceDetection.getClient(faceDetectorOptions)
        val firebaseImage = InputImage.fromBitmap(imageBitmap, 0)
        val result = faceDetector.process(firebaseImage)
            .addOnSuccessListener { faces ->

                // When the search for faces was successfully completed
                val imageBitmap = firebaseImage.bitmapInternal
                // Temporary Bitmap for drawing
                val tmpBitmap = Bitmap.createBitmap(
                    imageBitmap!!.width,
                    imageBitmap.height,
                    imageBitmap.config
                )

                // Create an image-based canvas
                val tmpCanvas = Canvas(tmpBitmap)
                tmpCanvas.drawBitmap(
                    imageBitmap, 0f, 0f,
                    null
                )
                val paint = Paint()
                paint.color = Color.GREEN
                paint.strokeWidth = 2f
                paint.textSize = 48f

                // Coefficient for indentation of face number
                val textIndentFactor = 0.1f

                // If at least one face was found
                if (!faces.isEmpty()) {
                    // faceId ~ face text number
                    var faceId = 1
                    for (face in faces) {
                        val faceRect = getInnerRect(
                            face.boundingBox,
                            imageBitmap.width,
                            imageBitmap.height
                        )

                        // Draw a rectangle around a face
                        paint.setStyle(Paint.Style.STROKE);
                        tmpCanvas.drawRect(faceRect, paint);

                        // Draw a face number in a rectangle
                        paint.setStyle(Paint.Style.FILL);
                        tmpCanvas.drawText(
                            Integer.toString(faceId),
                            faceRect.left +
                                    faceRect.width() * textIndentFactor,
                            faceRect.bottom -
                                    faceRect.height() * textIndentFactor,
                            paint
                        );

                        // Get subarea with a face
                        val faceBitmap = Bitmap.createBitmap(
                            imageBitmap,
                            faceRect.left,
                            faceRect.top,
                            faceRect.width(),
                            faceRect.height()
                        )
//                        classifyEmotions(faceBitmap, faceId)
                        faceId++
                    }

                    // Set the image with the face designations

                    // Set the image with the face designations
                    cameraView.setImageBitmap(tmpBitmap)
                    cameraView.rotation = -90f

                    // If single face, then immediately open the list
                    if (faces.size == 1) {
                        Log.d("abhishekmajhi", "faces = 1")
                    }
                    // If no faces are found
                } else {
                    Log.d("abhishekmajhi", "No faces")
                }
            }
            .addOnFailureListener { e -> e.printStackTrace() }

    }

    private fun getInnerRect(rect: Rect, areaWidth: Int, areaHeight: Int): Rect {
        val innerRect = Rect(rect)
        if (innerRect.top < 0) {
            innerRect.top = 0
        }
        if (innerRect.left < 0) {
            innerRect.left = 0
        }
        if (rect.bottom > areaHeight) {
            innerRect.bottom = areaHeight
        }
        if (rect.right > areaWidth) {
            innerRect.right = areaWidth
        }
        return innerRect
    }



}
