package com.typetype.droid

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import kotlin.math.abs

class FloatingImeSwitcherService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureBubble()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }

    private fun ensureBubble() {
        if (!canDrawOverlays(this) || isTypeTypeCurrentInputMethod(this)) {
            stopSelf()
            return
        }
        showBubble()
    }

    private fun showBubble() {
        if (bubbleView != null) return
        windowManager = getSystemService(WindowManager::class.java)
        val size = dp(40)
        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            x = dp(14)
            y = dp(78)
        }

        val bubble = ImageButton(this).apply {
            contentDescription = getString(R.string.choose_input_method)
            setImageResource(R.drawable.ic_switch_line)
            setColorFilter(Color.WHITE)
            scaleType = android.widget.ImageView.ScaleType.CENTER
            alpha = 0.88f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(15, 194, 147))
                setStroke(dp(1), Color.argb(120, 255, 255, 255))
            }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setOnTouchListener(FloatingTouchHandler(params))
        }

        bubbleView = bubble
        bubbleParams = params
        runCatching {
            windowManager?.addView(bubble, params)
        }.onFailure {
            bubbleView = null
            bubbleParams = null
            stopSelf()
        }
    }

    private fun removeBubble() {
        val view = bubbleView ?: return
        runCatching { windowManager?.removeView(view) }
        bubbleView = null
        bubbleParams = null
    }

    private fun showInputMethodPicker() {
        val intent = Intent(this, ImePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        runCatching {
            PendingIntent.getActivity(this, 2001, intent, flags).send()
        }.onFailure {
            startActivity(intent)
        }
    }

    private fun scheduleRestart() {
        if (!canDrawOverlays(this) || isTypeTypeCurrentInputMethod(this)) return
        val intent = Intent(this, FloatingImeSwitcherService::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getService(this, 2002, intent, flags)
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000L,
            pendingIntent,
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class FloatingTouchHandler(
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    view.alpha = 1f
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > dp(10) || abs(dy) > dp(10)) {
                        moved = true
                    }
                    params.x = (startX + dx).toInt().coerceAtLeast(0)
                    params.y = (startY - dy).toInt().coerceAtLeast(0)
                    windowManager?.updateViewLayout(view, params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    view.alpha = 0.88f
                    if (!moved) {
                        view.performClick()
                        showInputMethodPicker()
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.alpha = 0.88f
                    return true
                }
            }
            return true
        }
    }

    companion object {
        fun startIfAllowed(context: Context) {
            if (!canDrawOverlays(context) || isTypeTypeCurrentInputMethod(context)) return
            runCatching {
                context.startService(Intent(context, FloatingImeSwitcherService::class.java))
            }.onFailure { error ->
                Log.w(TAG, "Unable to start floating IME switcher", error)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, FloatingImeSwitcherService::class.java))
            }
        }

        fun isTypeTypeCurrentInputMethod(context: Context): Boolean {
            val current = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD,
            ) ?: return false
            return current.contains(context.packageName) && current.contains("VoiceImeService")
        }

        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        fun requestOverlayPermission(context: Context) {
            if (canDrawOverlays(context)) return
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }

        private const val TAG = "TypeTypeFloatingIme"
    }
}
