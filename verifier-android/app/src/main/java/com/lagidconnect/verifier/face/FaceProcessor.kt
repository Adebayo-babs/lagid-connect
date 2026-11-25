package com.lagidconnect.verifier.face

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Detect -> align -> embed, for still images and for frames already processed by the live detector. */
class FaceProcessor(private val embedder: FaceEmbedder) {

    private val stillDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    /** For the stored ID photo. If several faces are found, the largest is used. */
    suspend fun embedStill(photo: Bitmap): FloatArray {
        val faces = stillDetector.process(InputImage.fromBitmap(photo, 0)).await()
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: throw FaceException(FaceError.NO_FACE, "No face was found in the stored photo.")
        return embedAligned(FaceAligner.align(photo, face))
    }

    /** For a camera frame where the live detector already found [face]. Returns the aligned crop too. */
    suspend fun embedDetected(frame: Bitmap, face: Face): Pair<Bitmap, FloatArray> {
        val aligned = withContext(Dispatchers.Default) { FaceAligner.align(frame, face) }
        return aligned to embedAligned(aligned)
    }

    private suspend fun embedAligned(aligned: Bitmap): FloatArray =
        withContext(Dispatchers.Default) { embedder.embed(aligned) }

    companion object {
        /** Detector for live camera frames: fast, with eye-open probabilities and tracking IDs for liveness. */
        fun createLiveDetector(): FaceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.20f)
                .enableTracking()
                .build()
        )
    }
}
