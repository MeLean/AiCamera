package com.to.me.aicamera.classifiers

data class DetectionResult(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val confidence: Float,
    val classId: Int,
    val label: String? = null
)