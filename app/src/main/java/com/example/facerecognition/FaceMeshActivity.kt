package com.example.facerecognition

import android.content.ContentValues.TAG
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.solutions.facemesh.FaceMesh
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import com.google.mediapipe.solutioncore.VideoInput
import com.google.mediapipe.solutioncore.CameraInput
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutions.facemesh.FaceMeshResult
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import com.example.facerecognition.R
import com.example.facerecognition.FaceMeshActivity
import com.google.mediapipe.components.TextureFrameConsumer
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.framework.TextureFrame
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions
import java.lang.RuntimeException

class FaceMeshActivity : AppCompatActivity() {
    var frameCounter = 0
    var lastFpsTimestamp = System.currentTimeMillis()
    private var facemesh: FaceMesh? = null

    private enum class InputSource {
        UNKNOWN, IMAGE, VIDEO, CAMERA
    }

    private var inputSource = InputSource.UNKNOWN

    // Image demo UI and image loader components.
    private val imageGetter: ActivityResultLauncher<Intent>? = null
    private var imageView: FaceMeshResultImageView? = null

    // Video demo UI and video loader components.
    private var videoInput: VideoInput? = null
    private val videoGetter: ActivityResultLauncher<Intent>? = null

    // Live camera demo UI and camera components.
    private var cameraInput: CameraInput? = null
    private var glSurfaceView: SolutionGlSurfaceView<FaceMeshResult>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageView = FaceMeshResultImageView(applicationContext)
        setContentView(R.layout.activity_face_mesh)
        setupLiveDemoUiComponents()
    }

    /** Sets up the UI components for the live demo with camera input.  */
    private fun setupLiveDemoUiComponents() {
        val startCameraButton: Button = findViewById(R.id.button_start_camera)
        startCameraButton.setOnClickListener { v ->
            if (inputSource == InputSource.CAMERA) {
                return@setOnClickListener
            }
            stopCurrentPipeline()
            setupStreamingModePipeline(InputSource.CAMERA)
        }
    }

    /** Sets up core workflow for streaming mode.  */
    private fun setupStreamingModePipeline(inputSource: InputSource) {
        val frameCount = 30
        this.inputSource = inputSource
        // Initializes a new MediaPipe Face Mesh solution instance in the streaming mode.
        facemesh = FaceMesh(
            this,
            FaceMeshOptions.builder()
                .setStaticImageMode(false)
                .setRefineLandmarks(true)
                .setRunOnGpu(RUN_ON_GPU)
                .build()
        )
        facemesh!!.setErrorListener { message: String, e: RuntimeException? ->
            Log.e(
                TAG,
                "MediaPipe Face Mesh error:$message"
            )
        }
        if (inputSource == InputSource.CAMERA) {
            cameraInput = CameraInput(this)
            cameraInput!!.setNewFrameListener { textureFrame: TextureFrame? ->
                if (++frameCounter % frameCount == 0) {
                    frameCounter = 0
                    val now = System.currentTimeMillis()
                    val delta = now - lastFpsTimestamp
                    val fps = 1000 * frameCount.toFloat() / delta
                    Log.d(TAG, "FPS: ${"%.02f".format(fps)}")
                    lastFpsTimestamp = now
                }
                facemesh!!.send(
                    textureFrame
                )
            }
        } else if (inputSource == InputSource.VIDEO) {
            videoInput = VideoInput(this)
            videoInput!!.setNewFrameListener { textureFrame: TextureFrame? ->
                facemesh!!.send(
                    textureFrame
                )
            }
        }

        // Initializes a new Gl surface view with a user-defined FaceMeshResultGlRenderer.
        glSurfaceView = SolutionGlSurfaceView(this, facemesh!!.glContext, facemesh!!.glMajorVersion)
        glSurfaceView!!.setSolutionResultRenderer(FaceMeshResultGlRenderer())
        glSurfaceView!!.setRenderInputImage(true)
        facemesh!!.setResultListener { faceMeshResult: FaceMeshResult ->
            logNoseLandmark(faceMeshResult,  /*showPixelValues=*/false)
            glSurfaceView!!.setRenderData(faceMeshResult)
            glSurfaceView!!.requestRender()
        }

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView!!.post { startCamera() }
        }

        // Updates the preview layout.
        val frameLayout = findViewById<FrameLayout>(R.id.preview_display_layout)
        imageView!!.setVisibility(View.GONE)
        frameLayout.removeAllViewsInLayout()
        frameLayout.addView(glSurfaceView)
        glSurfaceView!!.visibility = View.VISIBLE
        frameLayout.requestLayout()
    }

    private fun stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput!!.setNewFrameListener(null)
            cameraInput!!.close()
        }
        if (videoInput != null) {
            videoInput!!.setNewFrameListener(null)
            videoInput!!.close()
        }
        if (glSurfaceView != null) {
            glSurfaceView!!.visibility = View.GONE
        }
        if (facemesh != null) {
            facemesh!!.close()
        }
    }

    private fun logNoseLandmark(result: FaceMeshResult?, showPixelValues: Boolean) {
        if (result == null || result.multiFaceLandmarks().isEmpty()) {
            return
        }
        val noseLandmark: LandmarkProto.NormalizedLandmark =
            result.multiFaceLandmarks()[0].landmarkList[1]
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            val width = result.inputBitmap().width
            val height = result.inputBitmap().height
            Log.i(
                TAG,
                java.lang.String.format(
                    "MediaPipe Face Mesh nose coordinates (pixel values): x=%f, y=%f",
                    noseLandmark.getX() * width, noseLandmark.getY() * height
                )
            )
        } else {
            Log.i(
                TAG,
                java.lang.String.format(
                    "MediaPipe Face Mesh nose normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                    noseLandmark.getX(), noseLandmark.getY()
                )
            )
        }
    }

    private fun startCamera() {
        cameraInput!!.start(
            this,
            facemesh!!.glContext,
            CameraInput.CameraFacing.FRONT,
            glSurfaceView!!.width,
            glSurfaceView!!.height
        )
    }

    override fun onResume() {
        super.onResume()
        if (inputSource == InputSource.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = CameraInput(this)
            cameraInput!!.setNewFrameListener { textureFrame: TextureFrame? ->
                Log.d("abhi", "abhishek")
                facemesh!!.send(
                    textureFrame
                )
            }
            glSurfaceView!!.post { startCamera() }
            glSurfaceView!!.setVisibility(View.VISIBLE)
        } else if (inputSource == InputSource.VIDEO) {
            videoInput!!.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView!!.setVisibility(View.GONE)
            cameraInput!!.close()
        } else if (inputSource == InputSource.VIDEO) {
            videoInput!!.pause()
        }
    }

    companion object {
        // Run the pipeline and the model inference on GPU or CPU.
        private const val RUN_ON_GPU = true
        private const val TAG = "FaceMesh"
    }
}