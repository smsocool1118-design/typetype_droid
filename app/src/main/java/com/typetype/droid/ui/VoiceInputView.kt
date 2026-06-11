package com.typetype.droid.ui

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.typetype.droid.R
import com.typetype.droid.session.VoiceSessionState
import com.typetype.droid.settings.Android031Settings
import com.typetype.droid.settings.StreamingEnhancementMode

class VoiceInputView(context: Context) : LinearLayout(context) {
    var onMicClicked: (() -> Unit)? = null
    var onDeleteClicked: (() -> Unit)? = null
    var onDeleteAllClicked: (() -> Unit)? = null
    var onSwitchInputMethodClicked: (() -> Unit)? = null
    var onRewriteClicked: (() -> Unit)? = null
    private val baseBottomPadding = dp(12)
    private val handler = Handler(Looper.getMainLooper())
    private var deleteRepeating = false
    private var deleteAllPopup: PopupWindow? = null

    private var isRecording = false
    private var micPulseAnimator: AnimatorSet? = null
    private var dotPulseAnimator: AnimatorSet? = null
    private var currentStatusText = ""
    private var currentStatusDotColor = COLOR_IDLE
    private var currentMicButtonColor = COLOR_ACCENT

    private val statusDot = View(context).apply {
        background = ovalDrawable(COLOR_IDLE)
        scaleX = 1f
        scaleY = 1f
    }
    private val statusView = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        textSize = 12.5f
        setTextColor(COLOR_MUTED)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val errorDetailView = TextView(context).apply {
        textSize = 11.5f
        setTextColor(COLOR_ERROR_TEXT)
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        visibility = GONE
    }
    private val aiModeBadgeView = TextView(context).apply {
        textSize = 11.5f
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setTextColor(COLOR_TEXT)
        setPadding(dp(9), 0, dp(9), 0)
        background = roundedDrawable(Color.rgb(226, 244, 238), dp(12))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val aiTitleView = TextView(context).apply {
        textSize = 13f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(COLOR_TEXT)
        includeFontPadding = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val aiDescView = TextView(context).apply {
        textSize = 11.5f
        setTextColor(COLOR_MUTED)
        includeFontPadding = false
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setLineSpacing(dp(1).toFloat(), 1f)
    }
    private val aiDraftView = TextView(context).apply {
        textSize = 12.5f
        setTextColor(COLOR_MUTED)
        includeFontPadding = false
        maxLines = Int.MAX_VALUE
        ellipsize = null
        setLineSpacing(dp(2).toFloat(), 1f)
        setSingleLine(false)
        text = "临时润写草稿会显示在这里"
    }
    private val aiDraftScrollView = ScrollView(context).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = true
        scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        setPadding(dp(10), dp(8), dp(6), dp(8))
        background = roundedDrawable(Color.rgb(255, 255, 255), dp(12)).apply {
            setStroke(dp(1), Color.rgb(219, 229, 226))
        }
        addView(
            aiDraftView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
    private val aiActionButton = TextView(context).apply {
        textSize = 12.5f
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setTextColor(Color.WHITE)
        background = roundedDrawable(COLOR_TEXT, dp(14))
        setPadding(dp(12), 0, dp(12), 0)
        isClickable = true
        isFocusable = true
        minWidth = dp(82)
        setOnClickListener { onRewriteClicked?.invoke() }
    }
    private val aiPanelView = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        background = aiPanelBackground()
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(aiModeBadgeView, LayoutParams(0, dp(26), 1f))
                addView(aiActionButton, LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)).apply {
                    leftMargin = dp(10)
                })
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        addView(aiTitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        addView(aiDraftScrollView, LayoutParams(LayoutParams.MATCH_PARENT, dp(92)).apply {
            topMargin = dp(7)
        })
        addView(aiDescView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(5)
        })
    }
    private val deleteButton = ImageButton(context).apply {
        contentDescription = context.getString(R.string.delete_key)
        setImageResource(R.drawable.ic_backspace_24)
        setColorFilter(COLOR_DELETE_ICON)
        scaleType = ImageView.ScaleType.CENTER
        background = keyBackground(COLOR_DELETE_KEY)
        setPadding(dp(13), dp(10), dp(13), dp(10))
        setOnTouchListener { _, event -> handleDeleteTouch(event) }
    }
    private val micButton = ImageButton(context).apply {
        contentDescription = context.getString(R.string.mic_start)
        setImageResource(R.drawable.ic_mic_line)
        setColorFilter(Color.WHITE)
        scaleType = ImageView.ScaleType.CENTER
        background = ovalDrawable(COLOR_ACCENT)
        setPadding(dp(20), dp(20), dp(20), dp(20))
    }
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(10), dp(18), baseBottomPadding)
        minimumHeight = dp(112)
        background = panelBackground()

        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedDrawable(Color.rgb(231, 235, 242), dp(14))
                setPadding(dp(10), 0, dp(12), 0)
                addView(statusDot, LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(7) })
                addView(statusView, LayoutParams(LayoutParams.WRAP_CONTENT, dp(28)))
            },
            LayoutParams(0, dp(32), 1f),
        )
        topRow.addView(deleteButton, LayoutParams(dp(56), dp(40)).apply { leftMargin = dp(12) })

        val micRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(6), 0, 0)
        }
        micRow.addView(View(context), LayoutParams(dp(38), dp(38)))
        micRow.addView(View(context), LayoutParams(0, 1, 1f))
        micRow.addView(micButton, LayoutParams(dp(72), dp(72)))
        micRow.addView(View(context), LayoutParams(0, 1, 1f))
        micRow.addView(View(context), LayoutParams(dp(38), dp(38)))

        addView(topRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
        addView(
            errorDetailView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            },
        )
        addView(
            aiPanelView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            },
        )
        addView(micRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(80)))

        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(
                bottom = VoiceInputInsets.bottomPadding(
                    baseBottomPadding = baseBottomPadding,
                    navigationBarInset = navigationBarInset,
                ),
            )
            insets
        }
        ViewCompat.requestApplyInsets(this)

        // Entrance animation - slide up and fade in
        translationY = dp(60).toFloat()
        alpha = 0f
        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Add press feedback to mic button
        micButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    micButton.animate()
                        .scaleX(0.92f)
                        .scaleY(0.92f)
                        .setDuration(80)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    micButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                    if (event.action == MotionEvent.ACTION_UP) {
                        onMicClicked?.invoke()
                    }
                }
            }
            true
        }
    }

    fun configureAndroid031(settings: Android031Settings) {
        if (!settings.streamingAiPanelEnabled) {
            aiPanelView.visibility = GONE
            return
        }
        val aiConfigured = settings.llmRewrite.enabled &&
            settings.llmRewrite.apiKey.isNotBlank() &&
            settings.llmRewrite.baseUrl.isNotBlank() &&
            settings.llmRewrite.model.isNotBlank()
        val scenarioLabel = "${settings.rewriteScenario.group} · ${settings.rewriteScenario.label}"
        if (settings.streamingEnhancementMode == StreamingEnhancementMode.ONLINE_ENHANCED) {
            aiModeBadgeView.text = if (aiConfigured) "AI 联网增强" else "AI 联网增强待配置"
            aiTitleView.text = "模板：$scenarioLabel"
            aiDescView.text = if (aiConfigured) {
                "点击后停止听写，按模板修正中英夹杂、标点和口头语，再替换输入框。"
            } else {
                "未配置模型 Key 时先用离线隐私增强；配置后自动启用联网润写。"
            }
            aiActionButton.text = if (aiConfigured) "AI带入" else "离线带入"
            aiActionButton.background = roundedDrawable(if (aiConfigured) COLOR_TEXT else COLOR_ACCENT, dp(14))
            aiModeBadgeView.background = roundedDrawable(
                if (aiConfigured) Color.rgb(229, 236, 255) else Color.rgb(232, 238, 246),
                dp(12),
            )
        } else {
            aiModeBadgeView.text = "离线隐私增强"
            aiTitleView.text = "模板：$scenarioLabel"
            aiDescView.text = "点击后停止听写，在本机补标点、保护术语、清理口头语并替换输入框。"
            aiActionButton.text = "整理带入"
            aiActionButton.background = roundedDrawable(COLOR_ACCENT, dp(14))
            aiModeBadgeView.background = roundedDrawable(Color.rgb(226, 244, 238), dp(12))
        }
        aiPanelView.visibility = VISIBLE
    }

    fun render(state: VoiceSessionState) {
        val statusColor: Int
        val newStatusText = when {
            state.error != null -> {
                statusColor = COLOR_RECORDING
                context.getString(R.string.status_error)
            }
            state.phase == VoiceSessionState.Phase.PREPARING -> {
                statusColor = COLOR_PREPARING
                context.getString(R.string.status_preparing)
            }
            state.phase == VoiceSessionState.Phase.TRANSLATING -> {
                statusColor = COLOR_PREPARING
                context.getString(R.string.status_translating)
            }
            state.isDecoding -> {
                statusColor = COLOR_ACCENT
                context.getString(R.string.status_decoding)
            }
            state.isActive -> {
                statusColor = COLOR_ACCENT
                context.getString(R.string.status_listening)
            }
            else -> {
                statusColor = COLOR_IDLE
                context.getString(R.string.status_idle)
            }
        }

        // Animate status text change
        if (currentStatusText != newStatusText && currentStatusText.isNotEmpty()) {
            animateStatusTextChange(newStatusText)
        } else {
            statusView.text = newStatusText
        }
        currentStatusText = newStatusText

        if (state.error != null) {
            errorDetailView.text = state.error
            errorDetailView.visibility = VISIBLE
        } else {
            errorDetailView.text = ""
            errorDetailView.visibility = GONE
        }
        renderDraftText(state.draftText)

        // Animate status dot color change
        animateStatusDotColor(statusColor)

        // Mic button animations
        micButton.contentDescription = context.getString(if (state.isActive) R.string.mic_stop else R.string.mic_start)

        val targetColor = if (state.isActive) COLOR_RECORDING else COLOR_ACCENT
        if (isRecording != state.isActive) {
            isRecording = state.isActive
            animateMicButtonColor(targetColor)
            if (state.isActive) {
                startPulseAnimations()
            } else {
                stopPulseAnimations()
            }
        }
    }

    private fun renderDraftText(draftText: String) {
        val trimmed = draftText.trim()
        if (trimmed.isBlank()) {
            aiDraftView.text = "临时润写草稿会显示在这里"
            aiDraftView.setTextColor(COLOR_MUTED)
        } else {
            aiDraftView.text = trimmed
            aiDraftView.setTextColor(COLOR_TEXT)
        }
        aiDraftScrollView.post {
            aiDraftScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun animateStatusTextChange(newText: String) {
        val fadeOut = ObjectAnimator.ofFloat(statusView, "alpha", 1f, 0f).apply {
            duration = 120
        }
        val fadeIn = ObjectAnimator.ofFloat(statusView, "alpha", 0f, 1f).apply {
            duration = 180
        }
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                statusView.text = newText
                fadeIn.start()
            }
        })
        fadeOut.start()
    }

    private fun animateStatusDotColor(targetColor: Int) {
        val currentDrawable = statusDot.background as? GradientDrawable ?: return
        val startColor = currentStatusDotColor
        if (startColor == targetColor) return

        ValueAnimator.ofObject(ArgbEvaluator(), startColor, targetColor).apply {
            duration = 300
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                currentStatusDotColor = color
                currentDrawable.setColor(color)
            }
            start()
        }
    }

    private fun animateMicButtonColor(targetColor: Int) {
        val drawable = micButton.background as? GradientDrawable ?: return
        val startColor = currentMicButtonColor
        if (startColor == targetColor) return

        ValueAnimator.ofObject(ArgbEvaluator(), startColor, targetColor).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                currentMicButtonColor = color
                drawable.setColor(color)
            }
            start()
        }
    }

    private fun startPulseAnimations() {
        // Mic button gentle pulse (breathing effect)
        val micScaleX = ObjectAnimator.ofFloat(micButton, "scaleX", 1f, 1.06f, 1f).apply {
            duration = 1200
        }
        val micScaleY = ObjectAnimator.ofFloat(micButton, "scaleY", 1f, 1.06f, 1f).apply {
            duration = 1200
        }
        micPulseAnimator = AnimatorSet().apply {
            playTogether(micScaleX, micScaleY)
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isRecording) {
                        animation.start()
                    }
                }
            })
            start()
        }

        // Status dot subtle pulse
        val dotScaleX = ObjectAnimator.ofFloat(statusDot, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 1000
        }
        val dotScaleY = ObjectAnimator.ofFloat(statusDot, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 1000
        }
        dotPulseAnimator = AnimatorSet().apply {
            playTogether(dotScaleX, dotScaleY)
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isRecording) {
                        animation.start()
                    }
                }
            })
            start()
        }
    }

    private fun stopPulseAnimations() {
        micPulseAnimator?.cancel()
        micPulseAnimator = null
        dotPulseAnimator?.cancel()
        dotPulseAnimator = null

        // Animate back to normal scale
        micButton.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        statusDot.animate().scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
    }

    override fun onDetachedFromWindow() {
        stopDeleteRepeat()
        dismissDeleteAllPopup()
        stopPulseAnimations()
        super.onDetachedFromWindow()
    }

    private fun handleDeleteTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                deleteButton.isPressed = true
                onDeleteClicked?.invoke()
                startDeleteRepeat()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val popupVisible = deleteAllPopup?.isShowing == true
                stopDeleteRepeat()
                deleteButton.isPressed = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!popupVisible) {
                    dismissDeleteAllPopup()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                stopDeleteRepeat()
                deleteButton.isPressed = false
                dismissDeleteAllPopup()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun startDeleteRepeat() {
        deleteRepeating = true
        handler.postDelayed(deleteRepeatRunnable, DELETE_REPEAT_START_MS)
        handler.postDelayed(showDeleteAllRunnable, DELETE_ALL_POPUP_DELAY_MS)
    }

    private fun stopDeleteRepeat() {
        deleteRepeating = false
        handler.removeCallbacks(deleteRepeatRunnable)
        handler.removeCallbacks(showDeleteAllRunnable)
    }

    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            if (!deleteRepeating) return
            onDeleteClicked?.invoke()
            handler.postDelayed(this, DELETE_REPEAT_INTERVAL_MS)
        }
    }

    private val showDeleteAllRunnable = Runnable {
        if (deleteRepeating) {
            showDeleteAllPopup()
        }
    }

    private fun showDeleteAllPopup() {
        if (deleteAllPopup?.isShowing == true || !deleteButton.isAttachedToWindow) return
        val button = TextView(context).apply {
            text = context.getString(R.string.delete_all_key)
            textSize = 14f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = roundedDrawable(COLOR_DELETE_ALL, dp(18))
            setPadding(dp(18), 0, dp(18), 0)
            elevation = dp(8).toFloat()
            setOnClickListener {
                onDeleteAllClicked?.invoke()
                dismissDeleteAllPopup()
            }
        }
        deleteAllPopup = PopupWindow(
            button,
            dp(104),
            dp(44),
            false,
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            showAsDropDown(deleteButton, -dp(24), -dp(94), Gravity.NO_GRAVITY)
        }
    }

    private fun dismissDeleteAllPopup() {
        deleteAllPopup?.dismiss()
        deleteAllPopup = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun panelBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(239, 241, 247), Color.rgb(213, 217, 226)),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.rgb(199, 204, 214))
        }
    }

    private fun roundedDrawable(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun ovalDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun keyBackground(color: Int): GradientDrawable {
        return roundedDrawable(color, dp(12)).apply {
            setStroke(dp(1), Color.rgb(207, 212, 220))
        }
    }

    private fun aiPanelBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(249, 252, 251), Color.rgb(238, 246, 244)),
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(207, 226, 221))
        }
    }

    private companion object {
        val COLOR_TEXT: Int = Color.rgb(29, 32, 35)
        val COLOR_MUTED: Int = Color.rgb(99, 106, 116)
        val COLOR_DELETE_KEY: Int = Color.rgb(244, 246, 250)
        val COLOR_DELETE_ICON: Int = Color.rgb(104, 112, 123)
        val COLOR_SETTINGS_KEY: Int = Color.rgb(244, 246, 250)
        val COLOR_DELETE_ALL: Int = Color.rgb(33, 37, 43)
        val COLOR_ERROR_TEXT: Int = Color.rgb(140, 42, 34)
        val COLOR_PREPARING: Int = Color.rgb(232, 149, 44)
        val COLOR_ACCENT: Int = Color.rgb(15, 194, 147)
        val COLOR_RECORDING: Int = Color.rgb(203, 72, 63)
        val COLOR_IDLE: Int = Color.rgb(142, 150, 161)
        const val DELETE_REPEAT_START_MS = 320L
        const val DELETE_REPEAT_INTERVAL_MS = 58L
        const val DELETE_ALL_POPUP_DELAY_MS = 720L
    }
}
