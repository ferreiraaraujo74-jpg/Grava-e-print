package com.tracomagico.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private enum class TraceMode { CAMERA, LIGHTBOX }

/**
 * Modo traçar:
 *  - Câmera: o desenho fica semitransparente sobre a câmera; o celular fica num apoio
 *    acima do papel e você copia os traços olhando a tela.
 *  - Mesa de luz: o desenho em tela cheia com brilho máximo; coloque uma folha fina
 *    sobre a tela e trace.
 */
@Composable
fun TraceScreen(image: Bitmap, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(TraceMode.CAMERA) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasCamera = ok
        if (!ok) {
            Toast.makeText(ctx, "Sem a câmera, use a Mesa de luz", Toast.LENGTH_LONG).show()
            mode = TraceMode.LIGHTBOX
        }
    }
    LaunchedEffect(mode) {
        if (mode == TraceMode.CAMERA && !hasCamera) camPerm.launch(Manifest.permission.CAMERA)
    }

    var alpha by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var mirrored by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // tela sempre ligada; brilho máximo na mesa de luz
    val view = LocalView.current
    DisposableEffect(mode) {
        view.keepScreenOn = true
        val win = (ctx as? Activity)?.window
        val old = win?.attributes?.screenBrightness
        if (mode == TraceMode.LIGHTBOX) win?.attributes = win?.attributes?.apply { screenBrightness = 1f }
        onDispose {
            view.keepScreenOn = false
            if (win != null && old != null) win.attributes = win.attributes.apply { screenBrightness = old }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (mode == TraceMode.LIGHTBOX) Color.White else Color.Black),
    ) {
        if (mode == TraceMode.CAMERA && hasCamera) {
            CameraPreview(onCamera = { camera = it })
        }

        // desenho sobreposto, com arrastar / pinça / girar (desligado quando travado)
        Image(
            image.asImageBitmap(),
            contentDescription = "Desenho para traçar",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    if (!locked) detectTransformGestures { _, pan, zoom, rot ->
                        scale = (scale * zoom).coerceIn(0.2f, 8f)
                        rotation += rot
                        offset += pan
                    }
                }
                .graphicsLayer {
                    this.alpha = if (mode == TraceMode.LIGHTBOX) maxOf(alpha, 0.15f) else alpha
                    scaleX = scale * (if (mirrored) -1f else 1f)
                    scaleY = scale
                    rotationZ = rotation
                    translationX = offset.x
                    translationY = offset.y
                },
        )

        if (locked) {
            // trava: só um cadeado; segure para destravar
            Box(
                Modifier
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .size(52.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { Toast.makeText(ctx, "Segure o cadeado para destravar", Toast.LENGTH_SHORT).show() },
                            onLongPress = { locked = false },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lock, "Travado", tint = Color.White) }
        } else {
            Controls(
                mode = mode,
                onMode = { mode = it },
                alpha = alpha, onAlpha = { alpha = it },
                torch = torch,
                torchAvailable = mode == TraceMode.CAMERA && camera?.cameraInfo?.hasFlashUnit() == true,
                onTorch = { torch = !torch; camera?.cameraControl?.enableTorch(torch) },
                onMirror = { mirrored = !mirrored },
                onReset = { scale = 1f; rotation = 0f; offset = Offset.Zero },
                onLock = {
                    locked = true
                    Toast.makeText(ctx, "Travado. Segure o cadeado para destravar.", Toast.LENGTH_SHORT).show()
                },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun Controls(
    mode: TraceMode, onMode: (TraceMode) -> Unit,
    alpha: Float, onAlpha: (Float) -> Unit,
    torch: Boolean, torchAvailable: Boolean, onTorch: () -> Unit,
    onMirror: () -> Unit, onReset: () -> Unit, onLock: () -> Unit, onBack: () -> Unit,
) {
    val btn = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White)
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, colors = btn) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Spacer(Modifier.weight(1f))
            if (torchAvailable) IconButton(onClick = onTorch, colors = btn) {
                Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff, "Lanterna")
            }
            IconButton(onClick = onMirror, colors = btn) { Icon(Icons.Filled.Flip, "Espelhar") }
            IconButton(onClick = onReset, colors = btn) { Icon(Icons.Filled.CenterFocusStrong, "Centralizar") }
            IconButton(onClick = onLock, colors = btn) { Icon(Icons.Filled.LockOpen, "Travar") }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .padding(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == TraceMode.CAMERA, onClick = { onMode(TraceMode.CAMERA) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2), label = { Text("Câmera") },
                )
                SegmentedButton(
                    selected = mode == TraceMode.LIGHTBOX, onClick = { onMode(TraceMode.LIGHTBOX) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2), label = { Text("Mesa de luz") },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacidade", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
                Slider(value = alpha, onValueChange = onAlpha, modifier = Modifier.weight(1f))
            }
            Text(
                if (mode == TraceMode.CAMERA)
                    "Apoie o celular acima do papel e trace olhando a tela. Arraste, use a pinça e gire o desenho para encaixar."
                else "Brilho máximo: coloque uma folha fina sobre a tela e trace. Trave para não mexer o desenho.",
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun CameraPreview(onCamera: (Camera?) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycle) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                onCamera(p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview))
            } catch (t: Throwable) {
                Toast.makeText(ctx, "Câmera indisponível", Toast.LENGTH_SHORT).show()
                onCamera(null)
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            provider?.unbindAll()
            onCamera(null)
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

