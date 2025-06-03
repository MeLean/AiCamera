package com.to.me.aicamera.classifiers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class MobileNetClassifier(context: Context) {

    companion object {
        private const val MODEL_NAME = "mobilenet_v1_1.0_224.tflite"
        private const val IMAGE_SIZE = 224
        private const val NUM_CLASSES = 1001
        private const val NUM_CHANNELS = 3
        private const val CONFIDENCE_THRESHOLD = 0.75f
        private const val SCALE_FACTOR = 0.00390625f // 1/255
        private const val TAG = "MobileNetClassifier"
    }

    private val interpreter: Interpreter = Interpreter(loadModelFile(context))

    fun classify(bitmap: Bitmap): Int? {
        val input = preprocess(bitmap)
        val output = Array(1) { ByteArray(NUM_CLASSES) }

        interpreter.run(input, output)

        val (index, rawConfidence) = output[0]
            .mapIndexed { i, byte -> i to (byte.toInt() and 0xFF) }
            .maxByOrNull { it.second } ?: return null

        val probability = rawConfidence * SCALE_FACTOR

        Log.d(TAG, "Prediction index: $index | Confidence: $probability")

        return if (probability >= CONFIDENCE_THRESHOLD) index else null
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = bitmap.scale(IMAGE_SIZE, IMAGE_SIZE)
        val buffer = ByteBuffer.allocateDirect(IMAGE_SIZE * IMAGE_SIZE * NUM_CHANNELS)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        scaled.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)

        for (pixel in pixels) {
            buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            buffer.put(((pixel shr 8) and 0xFF).toByte())  // G
            buffer.put((pixel and 0xFF).toByte())          // B
        }

        buffer.rewind()
        return buffer
    }

    private fun loadModelFile(context: Context): ByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_NAME)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
}