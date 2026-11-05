package com.gravaeprint.app.service

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gravaeprint.app.R
import com.gravaeprint.app.RecorderState
import com.gravaeprint.app.data.AudioMode
import com.gravaeprint.app.data.MediaSaver
import com.gravaeprint.app.data.RecordConfig
import com.gravaeprint.app.gl.ScreenRenderer
import com.gravaeprint.app.overlay.OverlayController
import java.util.concurrent.Executors

/**
 * Serviço em primeiro plano (tipo mediaProjection) que mantém a gravação viva.
 *
 * Um único MediaProjection -> um único VirtualDisplay -> ScreenRenderer (GL) -> MediaCodec.
 * Os prints são lidos do mesmo contexto GL, sem interromper o vídeo.
 */
class RecorderService : Service(), OverlayController.Listener {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var renderer: ScreenRenderer? = null
    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private var muxer: SafeMuxer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var videoUri: Uri? = null
    private val clock = RecordingClock()

    private lateinit var overlay: OverlayController
    private var recording = false
    private var stopping = false
    private var capturing = false
    private var lastShownSecond = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this, this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PRINT -> onBubbleTap()
            ACTION_PAUSE -> onTogglePause()
            ACTION_STOP -> onStopRequested()
            ACTION_SHOW_BUBBLE -> if (recording) overlay.show()
        }
        // Ação vinda de uma notificação antiga, sem gravação ativa: não fica rodando à toa.
        if (intent?.action != ACTION_START && !recording && !stopping) stopSelf()
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ início

    private fun handleStart(intent: Intent) {
        if (recording || stopping) return
        var config = RecordConfig.from(intent)
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else intent.getParcelableExtra(EXTRA_RESULT_DATA)

        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (config.audio != AudioMode.NONE && !micGranted) config = config.copy(audio = AudioMode.NONE)

        // O Android 14 exige startForeground(mediaProjection) ANTES de getMediaProjection.
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        // No Android 11+ o acesso ao microfone em segundo plano exige também o tipo "microphone".
        if (config.audio != AudioMode.NONE && Build.VERSION.SDK_INT >= 30) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        try {
            startForeground(NOTIF_ID, buildNotification(), type)
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground", t)
            toast("Não foi possível iniciar a gravação")
            stopSelf(); return
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            toast("Permissão de captura de tela negada")
            stopSelfClean(); return
        }

        try {
            startRecording(resultCode, data, config)
        } catch (t: Throwable) {
            Log.e(TAG, "startRecording", t)
            toast("Erro ao iniciar: ${t.message ?: t.javaClass.simpleName}")
            releaseAll(keepVideo = false)
            stopSelfClean()
        }
    }

    private fun startRecording(resultCode: Int, data: Intent, config: RecordConfig) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(resultCode, data) ?: error("MediaProjection indisponível")
        projection = mp
        // Obrigatório no Android 14: registrar o callback antes de criar o VirtualDisplay.
        mp.registerCallback(projectionCallback, main)

        // Tamanho real da tela (o VirtualDisplay fica em resolução nativa → prints nítidos)
        val (screenW, screenH, dpi) = screenSize()
        val (encW, encH) = VideoEncoder.pickSize(screenW, screenH, config.quality, config.fps)

        // Áudio (opcional)
        val audioRec = AudioEncoder.createRecord(config.audio, mp)
        if (config.audio != AudioMode.NONE && audioRec == null) toast("Áudio indisponível — gravando sem som")

        val uri = MediaSaver.createVideo(this)
        videoUri = uri
        val fd = contentResolver.openFileDescriptor(uri, "rw") ?: error("Não abriu o arquivo de vídeo")
        pfd = fd
        val mux = SafeMuxer(fd.fileDescriptor, if (audioRec != null) 2 else 1)
        muxer = mux

        val venc = VideoEncoder(encW, encH, config.fps, VideoEncoder.bitrateFor(encW, encH, config.fps), mux)
        videoEncoder = venc
        val r = ScreenRenderer(screenW, screenH, encW, encH, venc.inputSurface, clock, config.fps)
        renderer = r
        val input = r.start()

        clock.start()
        // UM ÚNICO VirtualDisplay para vídeo e prints.
        virtualDisplay = mp.createVirtualDisplay(
            "GravaEPrint", screenW, screenH, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            input, null, main,
        )

        audioRec?.let { (rec, ch) ->
            audioEncoder = AudioEncoder.build(rec, ch, mux, clock).also { it.start() }
        }

        recording = true
        overlay.show()
        publishState()
        main.post(ticker)
        Log.i(TAG, "Gravando: tela ${screenW}x$screenH, vídeo ${encW}x$encH @${config.fps}fps, áudio=${audioRec != null}")
    }

    private fun screenSize(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dpi = resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Triple(b.width(), b.height(), dpi)
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            Triple(dm.widthPixels, dm.heightPixels, dpi)
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // Usuário encerrou pelo sistema (ou outro app pegou a captura): fecha e salva.
            main.post { stopRecording() }
        }
    }

    // ------------------------------------------------------------------ ações

    override fun onBubbleTap() {
        val r = renderer ?: return
        if (!recording || capturing) return
        capturing = true
        val hadBubble = overlay.isShowing
        overlay.setCaptureHidden(true)
        // espera a bolha sumir do próximo frame antes de capturar
        main.postDelayed({
            r.capture { bmp ->
                main.post {
                    if (hadBubble) overlay.setCaptureHidden(false)
                    if (bmp != null) {
                        // retorno imediato: o frame já foi capturado; o PNG é salvo em seguida
                        vibrate()
                        overlay.flash()
                    }
                }
                if (bmp == null) {
                    main.post { capturing = false; toast("Ainda sem imagem — tente de novo") }
                    return@capture
                }
                io.execute {
                    val uri = MediaSaver.savePrint(this, bmp)
                    bmp.recycle()
                    main.post {
                        capturing = false
                        toast(if (uri != null) "Print salvo" else "Falha ao salvar o print")
                    }
                }
            }
        }, if (hadBubble) 180L else 0L)
    }

    override fun onTogglePause() {
        if (!recording) return
        if (clock.isPaused) clock.resume() else clock.pause()
        overlay.updatePaused(clock.isPaused)
        lastShownSecond = -1
        publishState()
        updateNotification()
    }

    override fun onStopRequested() {
        stopRecording()
    }

    private fun stopRecording() {
        if (!recording || stopping) return
        stopping = true
        recording = false
        main.removeCallbacks(ticker)
        overlay.hide()
        RecorderState.set(RecorderState.Status())
        io.execute {
            val saved = releaseAll(keepVideo = true)
            main.post {
                toast(if (saved) "Vídeo salvo em Movies/GravaEPrint" else "Gravação vazia descartada")
                stopping = false
                stopSelfClean()
            }
        }
    }

    /** Fecha tudo na ordem certa. Retorna true se o vídeo foi salvo. */
    private fun releaseAll(keepVideo: Boolean): Boolean {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { audioEncoder?.stop() }
        audioEncoder = null
        runCatching { renderer?.release() }
        renderer = null
        runCatching { videoEncoder?.stop() }
        videoEncoder = null
        val mux = muxer
        runCatching { mux?.stop() }
        muxer = null
        runCatching { pfd?.close() }
        pfd = null
        val hasData = (mux?.samplesWritten ?: 0) > 0
        val keep = keepVideo && hasData
        videoUri?.let { runCatching { MediaSaver.finishVideo(this, it, keep) } }
        videoUri = null
        projection?.let {
            runCatching { it.unregisterCallback(projectionCallback) }
            runCatching { it.stop() }
        }
        projection = null
        return keep
    }

    private fun stopSelfClean() {
        RecorderState.set(RecorderState.Status())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(ticker)
        overlay.hide()
        if (recording) {
            recording = false
            releaseAll(keepVideo = true)
        }
        RecorderState.set(RecorderState.Status())
        io.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ cronômetro / notificação

    private val ticker = object : Runnable {
        override fun run() {
            if (!recording) return
            val ms = clock.elapsedMs()
            overlay.updateTime(RecorderState.format(ms))
            publishState()
            val sec = ms / 1000
            if (sec != lastShownSecond) {
                lastShownSecond = sec
                updateNotification()
            }
            main.postDelayed(this, 250)
        }
    }

    private fun publishState() {
        RecorderState.set(RecorderState.Status(recording, clock.isPaused, clock.elapsedMs()))
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Gravação de tela", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostra o tempo e os controles da gravação"
                setShowBadge(false)
            }
        )
    }

    private fun pi(action: String, req: Int): PendingIntent = PendingIntent.getService(
        this, req, Intent(this, RecorderService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(): Notification {
        val time = RecorderState.format(if (recording) clock.elapsedMs() else 0)
        val paused = recording && clock.isPaused
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(if (paused) "Pausado $time" else "Gravando $time")
            .setContentText("Toque para mostrar a bolha flutuante")
            .setColor(0xFFFF7A00.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pi(ACTION_SHOW_BUBBLE, 4))
            .addAction(0, "Print", pi(ACTION_PRINT, 1))
            .addAction(0, if (paused) "Retomar" else "Pausar", pi(ACTION_PAUSE, 2))
            .addAction(0, "Parar", pi(ACTION_STOP, 3))
            .build()
    }

    private fun updateNotification() {
        if (!recording) return
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification()) }
    }

    // ------------------------------------------------------------------ utilidades

    private fun vibrate() {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        runCatching { v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun toast(msg: String) = main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }

    companion object {
        private const val TAG = "RecorderService"
        private const val CHANNEL = "gravacao"
        private const val NOTIF_ID = 42

        const val ACTION_START = "com.gravaeprint.START"
        const val ACTION_STOP = "com.gravaeprint.STOP"
        const val ACTION_PAUSE = "com.gravaeprint.PAUSE"
        const val ACTION_PRINT = "com.gravaeprint.PRINT"
        const val ACTION_SHOW_BUBBLE = "com.gravaeprint.SHOW_BUBBLE"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"

        fun start(ctx: Context, resultCode: Int, data: Intent, config: RecordConfig) {
            val i = Intent(ctx, RecorderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            config.putInto(i)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, RecorderService::class.java).setAction(action))
        }
    }
}
