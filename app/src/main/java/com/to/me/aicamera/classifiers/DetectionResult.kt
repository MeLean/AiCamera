package com.to.me.aicamera.classifiers

data class DetectionResult(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int,
    val label: String? = null,
    val imageWidth: Float = 640f,
    val imageHeight: Float = 640f,
)