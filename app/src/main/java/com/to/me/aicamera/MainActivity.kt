package com.to.me.aicamera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.to.me.aicamera.classifiers.DetectionResult
import com.to.me.aicamera.classifiers.OnnxObjectDetector
import com.to.me.aicamera.ui.theme.AiCameraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiCameraTheme {
                AppRoot()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val setupState = remember { mutableStateOf<SetupConfig?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }

        // Load setup after permission request
        if (cameraPermissionState.status.isGranted) {
            val config = SetupPrefs.get(context).firstOrNull()
            setupState.value = config
        }
    }

    if (!cameraPermissionState.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission is required to continue", color = Color.White)
        }
    } else {
        when (setupState.value) {
            null -> SetupScreen(onSetupComplete = {
                coroutineScope.launch {
                    setupState.value = SetupPrefs.get(context).firstOrNull()
                }
            })

            else -> CameraScreen(
                onReset = {
                    coroutineScope.launch {
                        SetupPrefs.clear(context)
                        setupState.value = null
                    }
                }
            )
        }
    }
}

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var apiEndpoint by remember { mutableStateOf("") }
    var modelEndpoint by remember { mutableStateOf("") }

    val defaultConfig = SetupConfig(
        ip = "192.168.0.100",
        port = "8080",
        apiEndpoint = "/api/process",
        modelEndpoint = "/model.tflite"
    )

    LaunchedEffect(Unit) {
        val saved = SetupPrefs.get(context).firstOrNull()
        ip = saved?.ip ?: defaultConfig.ip
        port = saved?.port ?: defaultConfig.port
        apiEndpoint = saved?.apiEndpoint ?: defaultConfig.apiEndpoint
        modelEndpoint = saved?.modelEndpoint ?: defaultConfig.modelEndpoint
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Text("Setup Configuration", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP Address") })
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
            OutlinedTextField(
                value = apiEndpoint,
                onValueChange = { apiEndpoint = it },
                label = { Text("API Endpoint") }
            )
            OutlinedTextField(
                value = modelEndpoint,
                onValueChange = { modelEndpoint = it },
                label = { Text("Model Endpoint") }
            )

            Spacer(modifier = Modifier.weight(1f)) // Push button to the bottom

            Button(
                onClick = {
                    val setup = SetupConfig(ip, port, apiEndpoint, modelEndpoint)
                    CoroutineScope(Dispatchers.IO).launch {
                        SetupPrefs.save(context, setup)
                        withContext(Dispatchers.Main) { onSetupComplete() }
                    }
                },
                enabled = ip.isNotBlank() && port.isNotBlank() && apiEndpoint.isNotBlank() && modelEndpoint.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Text("Save & Continue")
            }
        }
    }
}


@Composable
fun CameraScreen(
    onReset: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val apiHolder = remember { createApiFromSetup(context) }
    val sendToApiMutex = remember { Mutex() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            CameraPreview { pair ->

                val (label, bitmap) = pair ?: return@CameraPreview

                coroutineScope.launch {
                    sendToApi(context, label, bitmap, apiHolder, sendToApiMutex)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Reset Setup")
                }
            }
        }
    }
}


suspend fun sendToApi(
    context: Context,
    label: String,
    bitmap: Bitmap,
    apiHolder: DetectionApi?,
    mutex: Mutex
) {
    mutex.withLock {
        Log.d("TEST_IT", "API request for label: $label")
        val endpoint = SetupPrefs.get(context).firstOrNull()?.apiEndpoint ?: return
        val request = DetectionRequest(label, bitmap.toBase64())

        try {
            val response = apiHolder?.sendDetection(endpoint, request)
            if (response?.isSuccessful == true) {
                Log.d("TEST_IT", "✅ API success")
                Log.d("TEST_IT", "API Response: ${response.body()}")
            } else {
                Log.e("TEST_IT", "❌ API failed: ${response?.code()} ${response?.message()}")
            }
        } catch (e: Exception) {
            Log.e("TEST_IT", "⚠️ API call error: ${e.message}")
        }
    }
}

fun Bitmap.rotate(degrees: Int): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@Composable
fun CameraPreview(onDetection: (Pair<String, Bitmap>?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    val classifier = remember { OnnxObjectDetector(context) }
    val detectionLogs = remember { mutableStateListOf<String>() }
    val topDetection = remember { mutableStateOf<DetectionResult?>(null) }

    val cameraSelector = remember(lensFacing) {
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }

    val previewView = remember { PreviewView(context) }

    LaunchedEffect(cameraSelector) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val bitmap = imageProxy.toBitmap().rotate(rotation)

                    val detections = classifier.detect(bitmap)

                    if (detections.isNotEmpty()) {
                        val best = detections.maxByOrNull { it.confidence }
                        best?.let { res ->
                            val log = "\u2705 ${res.label ?: "No label"} " +
                                    "x1:${res.x1.format2f()}, y1:${res.y1.format2f()}, " +
                                    "x2:${res.x2.format2f()}, y2:${res.y2.format2f()}, " +
                                    "conf:${res.confidence.format2f()}"

                            if (detectionLogs.size >= 10) detectionLogs.removeAt(0)
                            detectionLogs.add(log)

                            topDetection.value = res
                            onDetection(log to bitmap)
                        }
                    } else {
                        topDetection.value = null
                    }

                    imageProxy.close()
                }
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e("TEST_IT", "Camera binding failed: ${e.message}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        topDetection.value?.let { result ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val modelInputSize = 640f
                val originalImageWidth = 480f
                val padding = (modelInputSize - originalImageWidth) / 2f // 80px

                val xScale = canvasWidth / originalImageWidth
                val yScale = canvasHeight / modelInputSize

                val x1 = (result.x1 - padding) * xScale
                val x2 = (result.x2 - padding) * xScale
                val y1 = result.y1 * yScale
                val y2 = result.y2 * yScale

                drawRect(
                    color = Color.Green,
                    topLeft = Offset(x1, y1),
                    size = Size(x2 - x1, y2 - y1),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT
                else
                    CameraSelector.LENS_FACING_BACK
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.8f), shape = CircleShape)
        ) {
            Icon(Icons.Filled.SwitchCamera, contentDescription = "Switch Camera")
        }

        DetectionLogViewer(
            detectionLogs = detectionLogs,
            onClear = {
                detectionLogs.clear()
                Log.d("TEST_IT", "Detection logs cleared")
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}

@Composable
fun DetectionLogViewer(
    detectionLogs: List<String>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xAA000000))
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Detection Logs",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Logs",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Column(
            modifier = Modifier
                .heightIn(min = 0.dp, max = 120.dp)
                .verticalScroll(rememberScrollState())
        ) {
            detectionLogs.takeLast(10).reversed().forEach {
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun Bitmap.toBase64(): String {
    val outputStream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

private fun Float.format2f(): String = String.format("%.2f", this)