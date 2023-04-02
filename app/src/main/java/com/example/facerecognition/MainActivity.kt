package com.example.facerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import com.google.firebase.FirebaseApp
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.otaliastudios.cameraview.Facing


class MainActivity : AppCompatActivity() {

    private lateinit var processCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var processCameraProvider: ProcessCameraProvider
    private lateinit var emotion: TextView
    private lateinit var cameraView: ImageView
    private lateinit var bmp: Bitmap
    var frameCounter = 0
    var lastFpsTimestamp = System.currentTimeMillis()
    val frameCount = 30

    private var cameraFacing: Facing = Facing.FRONT
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emotion = findViewById(R.id.mEmotion)
        cameraView = findViewById(R.id.cameraImage)
//        imageProcessor = ImageProcess()

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

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList[1];
        val characteristics = manager.getCameraCharacteristics(cameraId);
        val fpsRanges =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        Log.d(TAG, "ranges :$fpsRanges")

//        var camera2Extender = Camera2Config.
        val cameraControl = camera.cameraControl
        val camera2CameraControl = Camera2CameraControl.from(cameraControl)

        val captureRequestOptions = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF
            )
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range.create(50, 60)
            )
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
//            .setDefaultResolution(Size(1920, 1080))
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
                        bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        Log.d(TAG, "bitmap : $img")
//                        process(bmp)
                        analyzeImage(bmp)
//                        detect(bmp)
//                        runOnUiThread(Runnable {
//                            kotlin.run {
////                                cameraView.setImageBitmap(bmp)
////                                cameraView.rotation = -90f
//                                analyzeImage(bmp)
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
//        val image =
//            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
//        Log.d(TAG, "${image.width} :: ${image.height}")
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


    //    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
//    private fun detect(imageProxy: ImageProxy) {
//        Log.d("CameraFragment", "inside detect")
//        val faceDetector = FaceDetection.getClient()
//        val image =
//            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
////        val image: InputImage = InputImage.fromBitmap(bitmap,0)
//        Log.d(TAG, "${image.width} :: ${image.height}")
//        val result: Task<List<Face>> = faceDetector.process(image)
//        result.addOnSuccessListener { faces ->
//            // Do something with the detected faces
//            for (face in faces) {
//                val smileProb = face.smilingProbability
//                val leftEyeProb = face.leftEyeOpenProbability
//                val rightEyeProb = face.rightEyeOpenProbability
//                // Do something with the emotions
//                Log.d(TAG, "smile : %$face $smileProb $leftEyeProb $rightEyeProb")
//                emotion.text = face.toString()
//            }
//        }.addOnFailureListener { exception: Exception ->
//            // Handle the error
//            Log.d(TAG, exception.toString())
//        }
//
//    }
//
//    fun detect(imageBitmap: Bitmap) {
//        val faceDetectorOptions = FaceDetectorOptions.Builder()
//            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
//            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
//            .setMinFaceSize(0.1f)
//            .build()
//        val faceDetector = FaceDetection.getClient(faceDetectorOptions)
//        val firebaseImage = InputImage.fromBitmap(imageBitmap, 0)
//        val result = faceDetector.process(firebaseImage)
//            .addOnSuccessListener { faces ->
//
//                // When the search for faces was successfully completed
//                val imageBitmap = firebaseImage.bitmapInternal
//                // Temporary Bitmap for drawing
//                val tmpBitmap = Bitmap.createBitmap(
//                    imageBitmap!!.width,
//                    imageBitmap.height,
//                    imageBitmap.config
//                )
//
//                // Create an image-based canvas
//                val tmpCanvas = Canvas(tmpBitmap)
//                tmpCanvas.drawBitmap(
//                    imageBitmap, 0f, 0f,
//                    null
//                )
//                val paint = Paint()
//                paint.color = Color.GREEN
//                paint.strokeWidth = 2f
//                paint.textSize = 48f
//
//                // Coefficient for indentation of face number
//                val textIndentFactor = 0.1f
//
//                // If at least one face was found
//                if (!faces.isEmpty()) {
//                    // faceId ~ face text number
//                    var faceId = 1
//                    for (face in faces) {
//                        val faceRect = getInnerRect(
//                            face.boundingBox,
//                            imageBitmap.width,
//                            imageBitmap.height
//                        )
//
//                        // Draw a rectangle around a face
//                        paint.setStyle(Paint.Style.STROKE);
//                        tmpCanvas.drawRect(faceRect, paint);
//
//                        // Draw a face number in a rectangle
//                        paint.setStyle(Paint.Style.FILL);
//                        tmpCanvas.drawText(
//                            Integer.toString(faceId),
//                            faceRect.left +
//                                    faceRect.width() * textIndentFactor,
//                            faceRect.bottom -
//                                    faceRect.height() * textIndentFactor,
//                            paint
//                        );
//
//                        // Get subarea with a face
//                        val faceBitmap = Bitmap.createBitmap(
//                            imageBitmap,
//                            faceRect.left,
//                            faceRect.top,
//                            faceRect.width(),
//                            faceRect.height()
//                        )
////                        classifyEmotions(faceBitmap, faceId)
//                        faceId++
//                    }
//
//                    // Set the image with the face designations
//
//                    // Set the image with the face designations
//                    cameraView.setImageBitmap(tmpBitmap)
//                    cameraView.rotation = -90f
//
//                    // If single face, then immediately open the list
//                    if (faces.size == 1) {
//                        Log.d("abhishekmajhi", "faces = 1")
//                    }
//                    // If no faces are found
//                } else {
//                    Log.d("abhishekmajhi", "No faces")
//                }
//            }
//            .addOnFailureListener { e -> e.printStackTrace() }
//
//    }

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

    private fun process(imageBitmap: Bitmap) {
//        val width = imageBitmap.width
//        val height = imageBitmap.height

//        val metadata = FirebaseVisionImageMetadata.Builder()
//            .setWidth(width)
//            .setHeight(height)
//            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
//            .setRotation(if (cameraFacing == Facing.FRONT) FirebaseVisionImageMetadata.ROTATION_270 else FirebaseVisionImageMetadata.ROTATION_90)
//            .build()

//        val firebaseVisionImage = FirebaseVisionImage.fromByteArray(imagebyteArray, metadata)
        val firebaseVisionImage = FirebaseVisionImage.fromBitmap(imageBitmap)
        val options = FirebaseVisionFaceDetectorOptions.Builder()
            .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .build()
        val faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options)
        faceDetector.detectInImage(firebaseVisionImage)
            .addOnSuccessListener {
                cameraView.setImageBitmap(null)
                it.forEach {
                    val contour = it.getContour(FirebaseVisionFaceContour.FACE)
                    contour.points.forEach {
                        Log.d(TAG, "Point at ${it.x}, ${it.y}")
                    }
                    // More code here
                }

                val bitmap = imageBitmap.copy(Bitmap.Config.ARGB_8888, true)
//                val bitmap = Bitmap.createBitmap(
//                    imageBitmap.height,
//                    imageBitmap.width,
//                    Bitmap.Config.ARGB_8888
//                )
                val canvas = Canvas(bitmap)
                val dotPaint = Paint()
                dotPaint.color = Color.RED
                dotPaint.style = Paint.Style.FILL
                dotPaint.strokeWidth = 4F
                val linePaint = Paint()
                linePaint.color = Color.GREEN
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = 2F

                for (face in it) {

                    val faceContours = face.getContour(FirebaseVisionFaceContour.FACE).points
                    for ((i, contour) in faceContours.withIndex()) {
                        if (i != faceContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                faceContours[i + 1].x,
                                faceContours[i + 1].y,
                                linePaint
                            )
                        else
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                faceContours[0].x,
                                faceContours[0].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val leftEyebrowTopContours =
                        face.getContour(FirebaseVisionFaceContour.LEFT_EYEBROW_TOP).points
                    for ((i, contour) in leftEyebrowTopContours.withIndex()) {
                        if (i != leftEyebrowTopContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                leftEyebrowTopContours[i + 1].x,
                                leftEyebrowTopContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val leftEyebrowBottomContours =
                        face.getContour(FirebaseVisionFaceContour.LEFT_EYEBROW_BOTTOM).points
                    for ((i, contour) in leftEyebrowBottomContours.withIndex()) {
                        if (i != leftEyebrowBottomContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                leftEyebrowBottomContours[i + 1].x,
                                leftEyebrowBottomContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val rightEyebrowTopContours =
                        face.getContour(FirebaseVisionFaceContour.RIGHT_EYEBROW_TOP).points
                    for ((i, contour) in rightEyebrowTopContours.withIndex()) {
                        if (i != rightEyebrowTopContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                rightEyebrowTopContours[i + 1].x,
                                rightEyebrowTopContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val rightEyebrowBottomContours =
                        face.getContour(FirebaseVisionFaceContour.RIGHT_EYEBROW_BOTTOM).points
                    for ((i, contour) in rightEyebrowBottomContours.withIndex()) {
                        if (i != rightEyebrowBottomContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                rightEyebrowBottomContours[i + 1].x,
                                rightEyebrowBottomContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val leftEyeContours = face.getContour(FirebaseVisionFaceContour.LEFT_EYE).points
                    for ((i, contour) in leftEyeContours.withIndex()) {
                        if (i != leftEyeContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                leftEyeContours[i + 1].x,
                                leftEyeContours[i + 1].y,
                                linePaint
                            )
                        else
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                leftEyeContours[0].x,
                                leftEyeContours[0].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val rightEyeContours =
                        face.getContour(FirebaseVisionFaceContour.RIGHT_EYE).points
                    for ((i, contour) in rightEyeContours.withIndex()) {
                        if (i != rightEyeContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                rightEyeContours[i + 1].x,
                                rightEyeContours[i + 1].y,
                                linePaint
                            )
                        else
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                rightEyeContours[0].x,
                                rightEyeContours[0].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val upperLipTopContours =
                        face.getContour(FirebaseVisionFaceContour.UPPER_LIP_TOP).points
                    for ((i, contour) in upperLipTopContours.withIndex()) {
                        if (i != upperLipTopContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                upperLipTopContours[i + 1].x,
                                upperLipTopContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val upperLipBottomContours =
                        face.getContour(FirebaseVisionFaceContour.UPPER_LIP_BOTTOM).points
                    for ((i, contour) in upperLipBottomContours.withIndex()) {
                        if (i != upperLipBottomContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                upperLipBottomContours[i + 1].x,
                                upperLipBottomContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val lowerLipTopContours =
                        face.getContour(FirebaseVisionFaceContour.LOWER_LIP_TOP).points
                    for ((i, contour) in lowerLipTopContours.withIndex()) {
                        if (i != lowerLipTopContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                lowerLipTopContours[i + 1].x,
                                lowerLipTopContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val lowerLipBottomContours =
                        face.getContour(FirebaseVisionFaceContour.LOWER_LIP_BOTTOM).points
                    for ((i, contour) in lowerLipBottomContours.withIndex()) {
                        if (i != lowerLipBottomContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                lowerLipBottomContours[i + 1].x,
                                lowerLipBottomContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val noseBridgeContours =
                        face.getContour(FirebaseVisionFaceContour.NOSE_BRIDGE).points
                    for ((i, contour) in noseBridgeContours.withIndex()) {
                        if (i != noseBridgeContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                noseBridgeContours[i + 1].x,
                                noseBridgeContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }

                    val noseBottomContours =
                        face.getContour(FirebaseVisionFaceContour.NOSE_BOTTOM).points
                    for ((i, contour) in noseBottomContours.withIndex()) {
                        if (i != noseBottomContours.lastIndex)
                            canvas.drawLine(
                                contour.x,
                                contour.y,
                                noseBottomContours[i + 1].x,
                                noseBottomContours[i + 1].y,
                                linePaint
                            )
                        canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
                    }


                    if (cameraFacing == Facing.FRONT) {
                        val matrix = Matrix()
                        matrix.preScale(-1F, 1F)
                        val flippedBitmap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            true
                        )
                        cameraView.setImageBitmap(flippedBitmap)
                    } else {
                        cameraView.setImageBitmap(bitmap)
                    }
                }

            }
            .addOnFailureListener {
                cameraView.setImageBitmap(null)
                Log.d(TAG, "abhish : $it")
            }
    }

    private fun detectFaces(faces: List<FirebaseVisionFace>?, image: Bitmap?) {
        if (faces == null || image == null) {
            Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
            return
        }

        val canvas = Canvas(image)
        val facePaint = Paint()
        facePaint.color = Color.RED
        facePaint.style = Paint.Style.STROKE
        facePaint.strokeWidth = 8F
        val faceTextPaint = Paint()
        faceTextPaint.color = Color.RED
        faceTextPaint.textSize = 40F
        faceTextPaint.typeface = Typeface.DEFAULT_BOLD
        val landmarkPaint = Paint()
        landmarkPaint.color = Color.RED
        landmarkPaint.style = Paint.Style.FILL
        landmarkPaint.strokeWidth = 8F

        for ((index, face) in faces.withIndex()) {

            canvas.drawRect(face.boundingBox, facePaint)
            canvas.drawText(
                "Face$index",
                (face.boundingBox.centerX() - face.boundingBox.width() / 2) + 8F,
                (face.boundingBox.centerY() + face.boundingBox.height() / 2) - 8F,
                faceTextPaint
            )

            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE) != null) {
                val leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE)!!
                canvas.drawCircle(leftEye.position.x, leftEye.position.y, 8F, landmarkPaint)
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE) != null) {
                val rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE)!!
                canvas.drawCircle(rightEye.position.x, rightEye.position.y, 8F, landmarkPaint)
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE) != null) {
                val nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE)!!
                canvas.drawCircle(nose.position.x, nose.position.y, 8F, landmarkPaint)
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR) != null) {
                val leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR)!!
                canvas.drawCircle(leftEar.position.x, leftEar.position.y, 8F, landmarkPaint)
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR) != null) {
                val rightEar = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR)!!
                canvas.drawCircle(rightEar.position.x, rightEar.position.y, 8F, landmarkPaint)
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT) != null && face.getLandmark(
                    FirebaseVisionFaceLandmark.MOUTH_BOTTOM
                ) != null && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT) != null
            ) {
                val leftMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT)!!
                val bottomMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM)!!
                val rightMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT)!!
                canvas.drawLine(
                    leftMouth.position.x,
                    leftMouth.position.y,
                    bottomMouth.position.x,
                    bottomMouth.position.y,
                    landmarkPaint
                )
                canvas.drawLine(
                    bottomMouth.position.x,
                    bottomMouth.position.y,
                    rightMouth.position.x,
                    rightMouth.position.y,
                    landmarkPaint
                )
            }

//            faceDetectionModels.add(FaceDetectionModel(index, "Smiling Probability  ${face.smilingProbability}"))
//            faceDetectionModels.add(FaceDetectionModel(index, "Left Eye Open Probability  ${face.leftEyeOpenProbability}"))
//            faceDetectionModels.add(FaceDetectionModel(index, "Right Eye Open Probability  ${face.rightEyeOpenProbability}"))
        }
    }

    private fun analyzeImage(image: Bitmap?) {
        if (image == null) {
            Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
            return
        }

//        cameraView.setImageBitmap(null)
//        faceDetectionModels.clear()
//        bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
//        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
//        showProgress()

        val firebaseVisionImage = FirebaseVisionImage.fromBitmap(image)
        val options = FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
            .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
            .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
            .build()
        val faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options)
        faceDetector.detectInImage(firebaseVisionImage)
            .addOnSuccessListener {
                val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)

                detectFaces(it, mutableImage)
                runOnUiThread(Runnable {
                    kotlin.run {
                        cameraView.setImageBitmap(mutableImage)
                        cameraView.rotation = -90f

                    }
                })
//                hideProgress()
//                bottomSheetRecyclerView.adapter?.notifyDataSetChanged()
//                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            .addOnFailureListener {
                Toast.makeText(this, "There was some error", Toast.LENGTH_SHORT).show()
//                hideProgress()
            }
    }


}
