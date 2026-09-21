package com.vuvuv.usernamelab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var root: FrameLayout
    private lateinit var bubble: TextView
    private lateinit var panel: LinearLayout
    private lateinit var candidateView: TextView
    private lateinit var searchButton: Button

    private lateinit var repo: CandidateRepository
    private lateinit var store: FilterStore
    private var filters = CandidateFilters()
    private var candidate = "—"
    private var running = false

    private val handler = Handler(Looper.getMainLooper())
    private val searchLoop = object : Runnable {
        override fun run() {
            if (!running) return
            filters = store.get()
            candidate = repo.next(filters) ?: "—"
            candidateView.text = candidate
            if (candidate == "—") {
                running = false
                searchButton.text = "Поиск"
                return
            }
            handler.postDelayed(this, 2400L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        repo = CandidateRepository(this)
        store = FilterStore(this)
        filters = store.get()
        startForeground(44, buildNotification())
        createOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(searchLoop)
        if (::root.isInitialized) runCatching { wm.removeView(root) }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "vuvuv_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "Vuvuv overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Vuvuv Username Lab")
            .setContentText("Плавающее окно запущено")
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun createOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            dp(68),
            dp(68),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(120)
        }

        root = FrameLayout(this)

        bubble = TextView(this).apply {
            text = "V"
            gravity = Gravity.CENTER
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(typeface, 1)
            background = rounded(0xF01872C9.toInt(), 999f, 0x667AC3FF)
            setOnClickListener { expand() }
        }
        attachDrag(bubble)

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = rounded(0xF010151D.toInt(), 26f, 0x554C627D)
            visibility = View.GONE
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Vuvuv · Username Lab"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, 1)
        }
        val collapse = Button(this).apply {
            text = "—"
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { collapse() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(collapse, LinearLayout.LayoutParams(dp(52), dp(44)))
        panel.addView(header)
        attachDrag(header)

        candidateView = TextView(this).apply {
            text = candidate
            textSize = 29f
            setTextColor(Color.WHITE)
            setTypeface(typeface, 1)
            setPadding(dp(2), dp(8), dp(2), dp(10))
        }
        panel.addView(candidateView)

        searchButton = Button(this).apply {
            text = "Поиск"
            setOnClickListener {
                running = !running
                text = if (running) "Стоп" else "Поиск"
                handler.removeCallbacks(searchLoop)
                if (running) handler.post(searchLoop)
            }
        }
        panel.addView(searchButton)

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val copy = Button(this).apply {
            text = "Копировать"
            setOnClickListener {
                if (candidate != "—") {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("username", candidate))
                }
            }
        }

        val telegram = Button(this).apply {
            text = "Telegram"
            setOnClickListener {
                val launch = packageManager.getLaunchIntentForPackage("org.telegram.messenger")
                    ?: packageManager.getLaunchIntentForPackage("org.telegram.messenger.web")
                launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (launch != null) startActivity(launch)
            }
        }

        row1.addView(copy, LinearLayout.LayoutParams(0, dp(52), 1f))
        row1.addView(telegram, LinearLayout.LayoutParams(0, dp(52), 1f))
        panel.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val taken = Button(this).apply {
            text = "Занят"
            setOnClickListener {
                if (candidate != "—") {
                    repo.mark(candidate, CandidateStatus.TAKEN)
                    candidate = repo.next(store.get()) ?: "—"
                    candidateView.text = candidate
                }
            }
        }

        val free = Button(this).apply {
            text = "Свободен"
            setOnClickListener {
                if (candidate != "—") {
                    repo.mark(candidate, CandidateStatus.FREE)
                    running = false
                    handler.removeCallbacks(searchLoop)
                    searchButton.text = "Поиск"
                }
            }
        }

        row2.addView(taken, LinearLayout.LayoutParams(0, dp(52), 1f))
        row2.addView(free, LinearLayout.LayoutParams(0, dp(52), 1f))
        panel.addView(row2)

        val hint = TextView(this).apply {
            text = "«Поиск» автоматически листает кандидатов внутри Vuvuv. Для Telegram остаются кнопки «Копировать» и «Telegram»."
            textSize = 11f
            setTextColor(Color.rgb(126, 137, 153))
            setPadding(dp(2), dp(8), dp(2), dp(8))
        }
        panel.addView(hint)

        val close = Button(this).apply {
            text = "Закрыть окно"
            setOnClickListener { stopSelf() }
        }
        panel.addView(close)

        val resize = TextView(this).apply {
            text = "◢  удерживай для размера"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            textSize = 11f
            setTextColor(Color.rgb(111, 169, 224))
            setPadding(dp(8), 0, dp(4), 0)
        }
        panel.addView(resize, LinearLayout.LayoutParams(-1, dp(34)))
        attachResize(resize)

        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        root.addView(bubble, FrameLayout.LayoutParams(-1, -1))
        wm.addView(root, params)
    }

    private fun expand() {
        bubble.visibility = View.GONE
        panel.visibility = View.VISIBLE
        params.width = dp(340)
        params.height = dp(500)
        wm.updateViewLayout(root, params)
    }

    private fun collapse() {
        running = false
        handler.removeCallbacks(searchLoop)
        searchButton.text = "Поиск"
        panel.visibility = View.GONE
        bubble.visibility = View.VISIBLE
        params.width = dp(68)
        params.height = dp(68)
        wm.updateViewLayout(root, params)
    }

    private fun attachDrag(view: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                        params.x = startX + dx
                        params.y = max(0, startY + dy)
                        wm.updateViewLayout(root, params)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> moved
                else -> false
            }
        }
    }

    private fun attachResize(view: View) {
        var downX = 0f
        var downY = 0f
        var startW = 0
        var startH = 0
        var downAt = 0L

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startW = params.width
                    startH = params.height
                    downAt = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (System.currentTimeMillis() - downAt < 450L) return@setOnTouchListener true
                    params.width = max(dp(280), startW + (event.rawX - downX).toInt())
                    params.height = max(dp(330), startH + (event.rawY - downY).toInt())
                    wm.updateViewLayout(root, params)
                    true
                }
                else -> true
            }
        }
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
