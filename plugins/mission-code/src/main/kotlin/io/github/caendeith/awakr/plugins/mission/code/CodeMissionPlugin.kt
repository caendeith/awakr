package io.github.caendeith.awakr.plugins.mission.code

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.caendeith.awakr.sdk.Capability
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.sdk.PluginManifest
import io.github.caendeith.awakr.sdk.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Stop the alarm by showing it a code.
 *
 * Pointing the camera at the code is the normal path: the analysis stream decodes it and the
 * mission is solved without taking a picture. The shutter is an optional fallback for small or
 * glaring codes, and it is also the only way a frame gets saved (to `Pictures/Awakr`).
 *
 * With an empty payload any supported code counts. A generated code is a printable token.
 */
class CodeMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-code",
        name = StringRes(R.string.plugin_code_name),
        version = "1.2.0",
        capabilities = setOf(Capability.MISSION),
        requiredPermissions = listOf(Manifest.permission.CAMERA),
    )

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val format = CodePayload.format(config)
        val payload = CodePayload.payload(config)
        var message by remember { mutableStateOf(0) }
        var scanning by remember { mutableStateOf(false) }
        var cameraGranted by remember { mutableStateOf(hasCamera(context)) }
        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            cameraGranted = granted
            if (granted) scanning = true
        }
        fun startScan() {
            if (cameraGranted) scanning = true else cameraLauncher.launch(Manifest.permission.CAMERA)
        }
        // Resolved up front through the compose environment: lint forbids reading resources from a
        // LocalContext captured in a callback.
        val shareTitle = stringResource(R.string.plugin_code_name)
        val shareLabel = stringResource(R.string.code_share)

        val savePng = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("image/png"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val bitmap = CodeImage.encode(payload, format, 960, if (format.numeric) 320 else 960)
                ?: return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                val written = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } ?: false
                }.getOrDefault(false)
                withContext(Dispatchers.Main) {
                    message = if (written) R.string.code_saved else 0
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.code_editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CodeFormat.all.forEach { candidate ->
                    FilterChip(
                        selected = candidate == format,
                        onClick = { onChange(pickFormat(config, candidate)) },
                        label = { Text(stringResource(candidate.nameRes)) },
                    )
                }
            }
            if (payload.isBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = {
                            onChange(
                                config
                                    .with(CodePayload.KEY_FORMAT, format.id)
                                    .with(CodePayload.KEY_PAYLOAD, CodePayload.generate(format)),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.code_generate))
                    }
                    TextButton(onClick = { startScan() }) {
                        Text(stringResource(R.string.code_scan))
                    }
                }
            } else {
                CodePreview(payload = payload, format = format, size = 180.dp)
                Text(
                    text = payload,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = stringResource(R.string.code_print_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TextButton(
                        onClick = { startScan() },
                    ) {
                        Text(stringResource(R.string.code_scan_again))
                    }
                    TextButton(
                        onClick = {
                            onChange(
                                config.with(
                                    CodePayload.KEY_PAYLOAD,
                                    CodePayload.generate(format),
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.code_regenerate))
                    }
                    TextButton(onClick = { savePng.launch("awakr-" + format.id + ".png") }) {
                        Text(stringResource(R.string.code_save_png))
                    }
                    TextButton(
                        onClick = {
                            val bitmap = CodeImage.encode(payload, format, 960, if (format.numeric) 320 else 960)
                                ?: return@TextButton
                            scope.launch(Dispatchers.IO) {
                                val uri = CodeStorage.savePng(context, bitmap)
                                withContext(Dispatchers.Main) {
                                    if (uri == null) {
                                        message = 0
                                    } else {
                                        runCatching {
                                            context.startActivity(
                                                Intent.createChooser(
                                                    CodeStorage.shareIntent(
                                                        uri,
                                                        "image/png",
                                                        shareTitle,
                                                    ),
                                                    shareLabel,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.code_share))
                    }
                }
            }
            if (message != 0) {
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (scanning) {
            // A full-screen dialog rather than a bottom sheet: the camera preview needs an
            // undisturbed window of its own, and the goal is to fill the frame with the code.
            Dialog(
                onDismissRequest = { scanning = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.code_scan_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = { scanning = false }) {
                                Text(stringResource(R.string.code_close))
                            }
                        }
                        Text(
                            text = stringResource(R.string.code_scan_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (cameraGranted) {
                            CodeScanner(expected = null, saveShot = false) { text, scannedFormat ->
                                onChange(
                                    config
                                        .with(CodePayload.KEY_FORMAT, scannedFormat.id)
                                        .with(CodePayload.KEY_PAYLOAD, text),
                                )
                                message = R.string.code_scanned
                                scanning = false
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.code_scan_permission),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(stringResource(R.string.code_grant))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val context = LocalContext.current
        val expected = CodePayload.expected(config)
        var granted by remember {
            mutableStateOf(hasCamera(context))
        }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { result -> granted = result }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.code_hint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (granted) {
                CodeScanner(expected = expected, saveShot = true) { _, _ -> onSolved() }
            } else {
                Text(
                    text = stringResource(R.string.code_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.code_grant))
                }
            }
        }
    }

    private companion object {
        const val KIND = "code"
    }
}

private fun hasCamera(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun CodePreview(payload: String, format: CodeFormat, size: Dp) {
    val bitmap = remember(payload, format) {
        runCatching { CodeImage.encode(payload, format, 960, if (format.numeric) 320 else 960) }.getOrNull()
    } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = payload,
        modifier = Modifier
            .then(
                if (format.numeric) {
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                } else {
                    Modifier.size(size)
                },
            )
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(8.dp),
    )
}

@Composable
private fun CodeScanner(
    expected: String?,
    saveShot: Boolean,
    onCode: (String, CodeFormat) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()
    var solved by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf(0) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }
    Box(modifier = Modifier.fillMaxWidth().height(320.dp).clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                // A SurfaceView is composited by the system and ignores the Compose clip, so the
                // preview has to go through a TextureView to stay inside its 320.dp box.
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener(
                    {
                        val provider = runCatching { providerFuture.get() }.getOrNull()
                            ?: return@addListener
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        val reader = CodeDecoder.reader()
                        analysis.setAnalyzer(executor) { image ->
                            try {
                                val plane = image.planes[0]
                                val buffer = plane.buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val stripped = CodeFrame.stripRowPadding(
                                    bytes,
                                    plane.rowStride,
                                    image.width,
                                    image.height,
                                )
                                val (data, width, height) = CodeFrame.rotate(
                                    stripped,
                                    image.width,
                                    image.height,
                                    image.imageInfo.rotationDegrees,
                                )
                                val result = CodeImage.decodeLuminance(data, width, height, reader)
                                if (result != null && !solved) {
                                    val codeFormat = CodeDecoder.formatOf(result)
                                    if (codeFormat != null &&
                                        CodePayload.accepts(expected, result.text, codeFormat)
                                    ) {
                                        // The analyzer runs on its own executor; report from the main thread.
                                        val scannedText = result.text
                                        ContextCompat.getMainExecutor(context).execute {
                                            if (!solved) {
                                                solved = true
                                                onCode(scannedText, codeFormat)
                                            }
                                        }
                                    }
                                }
                            } catch (error: Throwable) {
                                Log.w(TAG, "frame analysis failed", error)
                            } finally {
                                image.close()
                            }
                        }
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                                imageCapture,
                            )
                        }.onFailure { Log.w(TAG, "could not bind the camera", it) }
                        capture = imageCapture
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )
    }

    Text(
        text = stringResource(if (message == 0) R.string.code_scanning else message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Button(
        onClick = {
            val imageCapture = capture
            if (imageCapture == null) {
                message = R.string.code_shot_failed
                return@Button
            }
            val file = File(context.cacheDir, "capture-" + System.currentTimeMillis() + ".jpg")
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            message = 0
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        scope.launch(Dispatchers.Default) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            val result = bitmap?.let { CodeImage.decodeResult(it) }
                            val codeFormat = result?.let { CodeDecoder.formatOf(it) }
                            val accepted = result != null && codeFormat != null &&
                                CodePayload.accepts(expected, result.text, codeFormat)
                            val scannedText = result?.text
                            val scannedFormat = codeFormat
                            if (
                                accepted && bitmap != null && scannedText != null &&
                                scannedFormat != null
                            ) {
                                if (saveShot) {
                                    CodeStorage.compress(bitmap)?.let { jpeg ->
                                        CodeStorage.saveShot(context, jpeg)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    message = if (saveShot) R.string.code_shot_saved else 0
                                    solved = true
                                    onCode(scannedText, scannedFormat)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    message = R.string.code_shot_failed
                                }
                            }
                            file.delete()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "capture failed", exception)
                        message = R.string.code_shot_failed
                    }
                },
            )
        },
    ) {
        Text(stringResource(R.string.code_shoot))
    }
}

private const val TAG = "AwakrCode"

/**
 * Applies a format choice, regenerating the code when the stored payload cannot be encoded in the
 * new format: an alphanumeric token cannot become an EAN-13 barcode, and keeping it would leave the
 * preview blank.
 */
private fun pickFormat(config: JsonObject, candidate: CodeFormat): JsonObject {
    val payload = CodePayload.ensureEncodable(CodePayload.payload(config), candidate)
    return config
        .with(CodePayload.KEY_FORMAT, candidate.id)
        .with(CodePayload.KEY_PAYLOAD, payload)
}
