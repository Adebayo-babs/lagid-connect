package com.lagidconnect.wallet.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Rotates, scales and crops a face so the eyes land on fixed positions in a 112x112 image.
 * The model was trained on faces aligned this way, so skipping this step costs a lot of accuracy.
 */
object FaceAligner {

    const val SIZE = 112

    // Standard eye positions for 112x112 aligned faces (image-left eye, image-right eye).
    private val TEMPLATE_LEFT_EYE = PointF(38.2946f, 51.6963f)
    private val TEMPLATE_RIGHT_EYE = PointF(73.5318f, 51.5014f)

    /** Smallest eye distance (pixels) we accept in the source image. */
    private const val MIN_EYE_DISTANCE_PX = 20f

    /**
     * [face] must come from ML Kit run on [source] in the same orientation
     * (or on an image rotated the same way as [source]).
     */
    fun align(source: Bitmap, face: Face): Bitmap {
        val a = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val b = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        return if (a != null && b != null) {
            alignByEyes(source, a, b)
        } else {
            cropByBox(source, face)
        }
    }

    private fun alignByEyes(source: Bitmap, p1: PointF, p2: PointF): Bitmap {
        // ML Kit's LEFT_EYE is the person's left eye, which can be either side of the image
        // depending on mirroring. Sorting by x makes the result independent of that.
        val (l, r) = if (p1.x <= p2.x) p1 to p2 else p2 to p1

        val srcDx = r.x - l.x
        val srcDy = r.y - l.y
        val srcDist = hypot(srcDx, srcDy)
        if (srcDist < MIN_EYE_DISTANCE_PX) {
            throw FaceException(FaceError.FACE_TOO_SMALL, "The face is too small to compare. Move closer.")
        }

        val dstDx = TEMPLATE_RIGHT_EYE.x - TEMPLATE_LEFT_EYE.x
        val dstDy = TEMPLATE_RIGHT_EYE.y - TEMPLATE_LEFT_EYE.y
        val scale = hypot(dstDx, dstDy) / srcDist
        val angleDeg = Math.toDegrees((atan2(dstDy, dstDx) - atan2(srcDy, srcDx)).toDouble()).toFloat()

        val m = Matrix().apply {
            postTranslate(-l.x, -l.y)
            postRotate(angleDeg)
            postScale(scale, scale)
            postTranslate(TEMPLATE_LEFT_EYE.x, TEMPLATE_LEFT_EYE.y)
        }
        return draw(source, m)
    }

    /** Fallback when eye landmarks are missing: square crop around the detected box. */
    private fun cropByBox(source: Bitmap, face: Face): Bitmap {
        val box = face.boundingBox
        val side = max(box.width(), box.height()).toFloat()
        if (side < MIN_EYE_DISTANCE_PX * 2.5f) {
            throw FaceException(FaceError.FACE_TOO_SMALL, "The face is too small to compare. Move closer.")
        }
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val scale = SIZE / side
        val m = Matrix().apply {
            postTranslate(-(cx - side / 2f), -(cy - side / 2f))
            postScale(scale, scale)
        }
        return draw(source, m)
    }

    private fun draw(source: Bitmap, m: Matrix): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(source, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }
}
