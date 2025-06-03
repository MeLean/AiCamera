package com.to.me.aicamera

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class TFLiteCardAnalyzer(context: Context) {

    private val interpreter: Interpreter

    init {
        val afd = context.assets.openFd("mobilenet_quant.tflite")
        require(afd.declaredLength > 0) { "Model file is empty!" }

        val inputStream = FileInputStream(afd.fileDescriptor)
        val fileChannel = inputStream.channel
        val modelBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )

        interpreter = Interpreter(modelBuffer) // This will crash if invalid model
    }

    fun analyze(bitmap: Bitmap): Boolean {
        val input = preprocess(bitmap)
        val output = Array(1) { FloatArray(1) }

        interpreter.run(input, output)

        return output[0][0] > 0.5f // you may adjust this threshold
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = bitmap.scale(224, 224, filter = true)
        val buffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3).order(ByteOrder.nativeOrder())
        val intValues = IntArray(224 * 224)
        resized.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        for (pixel in intValues) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        return buffer
    }
}
