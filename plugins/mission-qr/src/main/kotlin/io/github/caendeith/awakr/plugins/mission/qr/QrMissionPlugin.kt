package io.github.caendeith.awakr.plugins.mission.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import io.github.caendeith.awakr.sdk.Capability
import io.github.caendeith.awakr.sdk.MissionPlugin
import io.github.caendeith.awakr.sdk.PluginManifest
import io.github.caendeith.awakr.sdk.StringRes
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stop the alarm by scanning a QR code or barcode.
 *
 * With an empty payload any decodable code counts. With a payload the editor shows a printable
 * code and only that exact payload dismisses the alarm.
 */
class QrMissionPlugin : MissionPlugin {

    override val kind: String = KIND

    override val manifest: PluginManifest = PluginManifest(
        id = "mission-qr",
        name = StringRes(R.string.plugin_qr_name),
        version = "1.0.0",
        capabilities = setOf(Capability.MISSION),
        requiredPermissions = listOf(Manifest.permission.CAMERA),
    )

    @Composable
    override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) {
        val payload = QrPayload.expected(config)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.qr_editor_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (payload.isEmpty()) {
                Button(
                    onClick = {
                        onChange(config.with(QrPayload.KEY_PAYLOAD, QrPayload.generate()))
                    },
                ) {
                    Text(stringResource(R.string.qr_generate))
                }
            } else {
                QrCodeImage(payload = payload, size = 180.dp)
                Text(
                    text = payload,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.qr_print_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = {
                        onChange(config.with(QrPayload.KEY_PAYLOAD, QrPayload.generate()))
                    },
                ) {
                    Text(stringResource(R.string.qr_regenerate))
                }
            }
        }
    }

    @Composable
    override fun Mission(config: JsonObject, onSolved: () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var granted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            granted = result
        }

        LaunchedEffect(granted) {
            if (!granted) launcher.launch(Manifest.permission.CAMERA)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.qr_hint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (granted) {
                QrScanner(expected = QrPayload.expected(config), onSolved = onSolved)
            } else {
                Text(
                    text = stringResource(R.string.qr_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.qr_grant))
                }
            }
        }
    }

    private companion object {
        const val KIND = "qr"
    }
}

@Composable
private fun QrScanner(expected: String, onSolved: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var solved by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(solved) {
        if (solved) onSolved()
    }

    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener(
                    {
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build()
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor, QrAnalyzer(expected) { solved = true })
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        } catch (t: Throwable) {
                            Log.e(TAG, "camera start failed", t)
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )
        Text(
            text = stringResource(R.string.qr_scanning),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}


internal fun JsonObject.with(key: String, value: String): JsonObject =
    JsonObject(this + (key to kotlinx.serialization.json.JsonPrimitive(value)))

/** The printed code, drawn on a white card so it stays scannable on the dark theme. */
@Composable
private fun QrCodeImage(payload: String, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(payload) { runCatching { encodeQr(payload, 480) }.getOrNull() } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = payload,
        modifier = Modifier
            .size(size)
            .background(Color.White, MaterialTheme.shapes.medium)
            .padding(8.dp),
    )
}

private fun encodeQr(payload: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

private const val TAG = "QrMission"

private class QrAnalyzer(
    private val expected: String,
    private val onDecoded: () -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply { setHints(HINTS) }
    private val done = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        try {
            if (done.get()) return
            val width = image.width
            val height = image.height
            val rotated = QrFrame.rotate(luminance(image), width, height, image.imageInfo.rotationDegrees)
            val source = PlanarYUVLuminanceSource(
                rotated.first,
                rotated.second,
                rotated.third,
                0,
                0,
                rotated.second,
                rotated.third,
                false,
            )
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            if (result != null && QrPayload.matches(expected, result.text) && done.compareAndSet(false, true)) {
                onDecoded()
            }
        } catch (_: ReaderException) {
            // No code in this frame; the next one will be analysed.
        } catch (t: Throwable) {
            Log.w(TAG, "frame analysis failed", t)
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun luminance(proxy: ImageProxy): ByteArray {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = proxy.width
        val height = proxy.height
        val raw = ByteArray(buffer.remaining())
        buffer.get(raw)
        return QrFrame.stripRowPadding(raw, rowStride, width, height)
    }

    private companion object {
        val HINTS: Map<DecodeHintType, Any> = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF_417,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_128,
                BarcodeFormat.EAN_8,
                BarcodeFormat.EAN_13,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
            ),
        )
    }
}
