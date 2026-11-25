package com.lagidconnect.verifier.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Turns an aligned face into an L2-normalised embedding using a TFLite model.
 * Input and output sizes are read from the model, so a different MobileFaceNet-style model
 * (NHWC float input, [batch, dim] output) can be dropped in without code changes.
 */
class FaceEmbedder(context: Context, assetName: String) : Closeable {

    private val interpreter: Interpreter
    private val batch: Int
    private val inputH: Int
    private val inputW: Int
    private val outputDim: Int
    private val inputBuffer: ByteBuffer
    private val pixels: IntArray

    init {
        val model = try {
            loadModel(context, assetName)
        } catch (e: Exception) {
            throw FaceException(FaceError.MODEL, "Face model '$assetName' is missing from assets.")
        }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        val inShape = interpreter.getInputTensor(0).shape()   // [batch, h, w, 3]
        val outShape = interpreter.getOutputTensor(0).shape() // [batch, dim]
        batch = inShape[0]
        inputH = inShape[1]
        inputW = inShape[2]
        outputDim = outShape[1]
        inputBuffer = ByteBuffer.allocateDirect(4 * batch * inputH * inputW * 3).order(ByteOrder.nativeOrder())
        pixels = IntArray(inputH * inputW)
    }

    /**
     * Embeds the face and its mirror image and averages them.
     * This "flip test" is free accuracy: it smooths out left/right lighting and pose differences.
     */
    @Synchronized
    fun embed(alignedFace: Bitmap): FloatArray {
        val a = run(alignedFace)
        val b = run(flipHorizontally(alignedFace))
        val sum = FloatArray(outputDim) { a[it] + b[it] }
        return l2Normalize(sum)
    }

    private fun run(face: Bitmap): FloatArray {
        val input = if (face.width != inputW || face.height != inputH) {
            Bitmap.createScaledBitmap(face, inputW, inputH, true)
        } else face

        input.getPixels(pixels, 0, inputW, 0, 0, inputW, inputH)
        inputBuffer.rewind()
        // Models with a fixed batch > 1 get the same face in every slot; we read slot 0.
        repeat(batch) {
            for (p in pixels) {
                inputBuffer.putFloat((((p shr 16) and 0xFF) - 127.5f) / 128f)
                inputBuffer.putFloat((((p shr 8) and 0xFF) - 127.5f) / 128f)
                inputBuffer.putFloat(((p and 0xFF) - 127.5f) / 128f)
            }
        }
        inputBuffer.rewind()

        val output = Array(batch) { FloatArray(outputDim) }
        interpreter.run(inputBuffer, output)
        return output[0]
    }

    private fun flipHorizontally(src: Bitmap): Bitmap {
        val m = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum).takeIf { it > 1e-10f } ?: 1f
        return FloatArray(v.size) { v[it] / norm }
    }

    private fun loadModel(context: Context, assetName: String): MappedByteBuffer {
        context.assets.openFd(assetName).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    override fun close() = interpreter.close()
}
