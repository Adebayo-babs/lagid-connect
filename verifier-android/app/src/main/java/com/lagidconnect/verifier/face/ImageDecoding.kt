package com.lagidconnect.verifier.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

object ImageDecoding {

    /**
     * Decodes a base64 image into an upright Bitmap.
     * Accepts plain base64 or a data URI ("data:image/jpeg;base64,...") and ignores whitespace.
     */
    fun decodeBase64(base64: String): Bitmap {
        val clean = base64.substringAfter("base64,", base64).filterNot { it.isWhitespace() }
        val bytes = try {
            Base64.decode(clean, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw FaceException(FaceError.BAD_IMAGE, "The stored photo isn't valid base64.")
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw FaceException(FaceError.BAD_IMAGE, "The stored photo couldn't be read as an image.")
        return applyExifRotation(bitmap, bytes)
    }

    private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return rotate(bitmap, degrees)
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
