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

class YoloObjectDetector(context: Context) {

    companion object {
        private const val MODEL_NAME = "yolo.tflite"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.75f
    }

    private val interpreter: Interpreter

    init {
        val assetFileDescriptor = context.assets.openFd(MODEL_NAME)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer =
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        interpreter = Interpreter(modelBuffer)
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val scaled = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
        val input = convertBitmapToBuffer(scaled)

        val output = Array(1) { Array(5) { FloatArray(8400) } }

        interpreter.run(input, output)

        return parseResults(output)
    }

    private fun convertBitmapToBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
            buffer.putFloat((pixel and 0xFF) / 255f)          // B
        }

        buffer.rewind()
        return buffer
    }

    private fun parseResults(output: Array<Array<FloatArray>>): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        val boxes = output[0][0].size

        for (i in 0 until boxes) {
            val x = output[0][0][i]
            val y = output[0][1][i]
            val w = output[0][2][i]
            val h = output[0][3][i]
            val confidence = output[0][4][i]

            val classId = try {
                output[0][5][i].toInt()
            } catch (e: Exception) {
                -1
            }

            if (confidence > 0.7f) {

                Log.d(
                    "TEST_IT",
                    "Detected object: Class $classId at ($x, $y) with size ($w, $h) and confidence $confidence"
                )

                results.add(
                    DetectionResult(
                        x,
                        y,
                        w,
                        h,
                        confidence,
                        classId = classId
                    )
                ) // Replace label if needed
            }
        }

        return results
    }
}