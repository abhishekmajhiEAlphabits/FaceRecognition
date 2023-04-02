package com.example.facerecognition

//import android.graphics.*
//import android.util.Log
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.face.FaceDetection
//import com.google.mlkit.vision.face.FaceDetectorOptions
//
//class ImageProcess {
//    private lateinit var bmp:Bitmap
//
//     fun detectFaces(imageBitmap:Bitmap):Bitmap {
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
//                                                paint.setStyle(Paint.Style.STROKE);
//                                                tmpCanvas.drawRect(faceRect, paint);
//
//                        // Draw a face number in a rectangle
//                                                paint.setStyle(Paint.Style.FILL);
//                                                tmpCanvas.drawText(
//                                                        Integer.toString(faceId),
//                                                        faceRect.left +
//                                                                faceRect.width() * textIndentFactor,
//                                                        faceRect.bottom -
//                                                                faceRect.height() * textIndentFactor,
//                                                        paint);
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
//                    bmp = tmpBitmap
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
//         return bmp
//    }
//
//    private fun getInnerRect(rect: Rect, areaWidth: Int, areaHeight: Int): Rect {
//        val innerRect = Rect(rect)
//        if (innerRect.top < 0) {
//            innerRect.top = 0
//        }
//        if (innerRect.left < 0) {
//            innerRect.left = 0
//        }
//        if (rect.bottom > areaHeight) {
//            innerRect.bottom = areaHeight
//        }
//        if (rect.right > areaWidth) {
//            innerRect.right = areaWidth
//        }
//        return innerRect
//    }
//}