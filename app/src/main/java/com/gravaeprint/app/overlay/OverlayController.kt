package com.gravaeprint.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gravaeprint.app.R
import kotlin.math.abs

/**
 * Bolha flutuante (overlay) com cronômetro, mini menu e a "piscada" branca do print.
 * Feita com Views comuns para não depender do ciclo de vida de uma Activity.
 */
class OverlayController(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        fun onBubbleTap()
        fun onTogglePause()
        fun onStopRequested()
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val orange = 0xFFFF7A00.toInt()

    private var root: LinearLayout? = null
    private var timerView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var menu: View? = null
    private var paused = false

    val isShowing get() = root != null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)
    private fun dpi(v: Float) = dp(v).toInt()

    private fun baseParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    )

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (root != null || !Settings.canDrawOverlays(ctx)) return

        val bubbleSize = dpi(56f)
        val bubble = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC1E1E1E.toInt())
                setStroke(dpi(3f), orange)
            }
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_camera)
                setColorFilter(orange)
            }, FrameLayout.LayoutParams(dpi(26f), dpi(26f), Gravity.CENTER))
            contentDescription = "Tirar print"
        }
        val timer = TextView(ctx).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dpi(8f), dpi(3f), dpi(8f), dpi(3f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(0xB3000000.toInt())
            }
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            alpha = 0.85f
            addView(bubble, LinearLayout.LayoutParams(bubbleSize, bubbleSize))
            addView(timer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dpi(6f) })
        }

        val lp = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpi(8f)
            y = (ctx.resources.displayMetrics.heightPixels * 0.3f).toInt()
        }

        bubble.setOnTouchListener(DragTouch(layout, lp))
        wm.addView(layout, lp)
        root = layout; timerView = timer; params = lp
        updatePaused(paused)
    }

    fun hide() {
        dismissMenu()
        root?.let { runCatching { wm.removeView(it) } }
        root = null; timerView = null
    }

    fun updateTime(text: String) {
        timerView?.text = if (paused) "❚❚ $text" else text
    }

    fun updatePaused(p: Boolean) {
        paused = p
        timerView?.setTextColor(if (p) orange else Color.WHITE)
    }

    /** Esconde a bolha momentaneamente para ela não sair no print. */
    fun setCaptureHidden(hidden: Boolean) {
        root?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        menu?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** Piscada branca rápida na tela inteira. */
    fun flash() {
        if (!Settings.canDrawOverlays(ctx)) return
        val v = View(ctx).apply { setBackgroundColor(Color.WHITE); alpha = 0.85f }
        val lp = baseParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        runCatching { wm.addView(v, lp) }.onFailure { return }
        v.animate().alpha(0f).setDuration(220).withEndAction {
            runCatching { wm.removeView(v) }
        }.start()
    }

    // ---------- mini menu (toque longo) ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun showMenu() {
        dismissMenu()
        val anchor = params ?: return
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(6f), dpi(6f), dpi(6f), dpi(6f))
            background = GradientDrawable().apply {
                cornerRadius = dp(16f)
                setColor(0xF2202020.toInt())
                setStroke(dpi(1f), 0x66FF7A00)
            }
        }
        fun item(label: String, color: Int = Color.WHITE, action: () -> Unit) {
            box.addView(TextView(ctx).apply {
                text = label
                setTextColor(color)
                textSize = 16f
                setPadding(dpi(16f), dpi(12f), dpi(24f), dpi(12f))
                setOnClickListener { dismissMenu(); action() }
            })
        }
        item(if (paused) "▶  Retomar" else "❚❚  Pausar") { listener.onTogglePause() }
        item("■  Parar gravação", orange) { listener.onStopRequested() }
        item("✕  Esconder bolha") {
            hide()
            android.widget.Toast.makeText(
                ctx, "Bolha escondida. Toque na notificação para mostrá-la de novo.", android.widget.Toast.LENGTH_LONG
            ).show()
        }

        val lp = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val screenW = ctx.resources.displayMetrics.widthPixels
            x = if (anchor.x > screenW / 2) (anchor.x - dpi(150f)).coerceAtLeast(0) else anchor.x
            y = anchor.y + dpi(64f)
        }
        box.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) { dismissMenu(); true } else false
        }
        runCatching { wm.addView(box, lp); menu = box }
    }

    private fun dismissMenu() {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
    }

    /** Arrastar, toque simples e toque longo na bolha. */
    private inner class DragTouch(
        private val view: View,
        private val lp: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longFired = false
        private val longPress = Runnable {
            longFired = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showMenu()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false; longFired = false
                    main.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    view.alpha = 1f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop) && !longFired) {
                        dragging = true
                        main.removeCallbacks(longPress)
                        dismissMenu()
                    }
                    if (dragging) {
                        val dm = ctx.resources.displayMetrics
                        lp.x = (startX + dx.toInt()).coerceIn(0, dm.widthPixels - view.width.coerceAtLeast(1))
                        lp.y = (startY + dy.toInt()).coerceIn(0, dm.heightPixels - view.height.coerceAtLeast(1))
                        runCatching { wm.updateViewLayout(view, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    view.alpha = 0.85f
                    if (!dragging && !longFired) listener.onBubbleTap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    view.alpha = 0.85f
                }
            }
            return true
        }
    }
}
