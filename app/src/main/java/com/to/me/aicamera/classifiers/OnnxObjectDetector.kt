package com.to.me.aicamera.classifiers

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import java.nio.FloatBuffer


class OnnxObjectDetector(context: Context) {

    companion object {
        private const val MODEL_NAME = "yolov8n.onnx"
        private const val LABELS_FILE = "yolov8n_labels.txt"
        private const val INPUT_SIZE = 640
        private const val CONFIDENCE_THRESHOLD = 0.7f
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private val ortEnv = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labels: List<String>

    init {
        val modelBytes = context.assets.open(MODEL_NAME).readBytes()
        session = ortEnv.createSession(modelBytes)
        labels = context.assets.open(LABELS_FILE).bufferedReader().readLines()
        Log.d("TEST_IT", "✅ Labels loaded: ${labels.size}")
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val scaledBitmap = bitmap.scale(INPUT_SIZE, INPUT_SIZE)
        Log.d("TEST_IT", "🔍 Bitmap scaled: ${scaledBitmap.width}×${scaledBitmap.height}")


        val floatBuffer = preprocess(scaledBitmap)

        val inputTensor = OnnxTensor.createTensor(
            ortEnv, floatBuffer, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        )

        val outputs = session.run(mapOf(session.inputNames.first() to inputTensor))
        inputTensor.close()

        @Suppress("UNCHECKED_CAST")
        val raw = (outputs[0].value as Array<Array<FloatArray>>)[0] // shape [300][6]
        outputs.close()

        val detections = parseOutput(raw)

        return detections
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val imgData = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val stride = INPUT_SIZE * INPUT_SIZE
        pixels.forEachIndexed { idx, px ->
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f

            imgData.put(idx, (r - MEAN[0]) / STD[0])
            imgData.put(idx + stride, (g - MEAN[1]) / STD[1])
            imgData.put(idx + 2 * stride, (b - MEAN[2]) / STD[2])
        }
        imgData.rewind()
        return imgData
    }

    private fun parseOutput(output: Array<FloatArray>): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()
        for (row in output) {
            if (row.size < 6) continue
            val (x1, y1, x2, y2, conf) = row.sliceArray(0..4)
            val classId = row[5].toInt()
            if (conf < CONFIDENCE_THRESHOLD) continue
            val label = if (classId in labels.indices) labels[classId] else "Unknown"
            results.add(DetectionResult(x1, y1, x2 - x1, y2 - y1, conf, classId, label))
        }
        return results
    }
}

