package com.typetype.droid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.typetype.droid.session.DictationMode
import com.typetype.droid.settings.VoiceImePreferences
import com.typetype.droid.translation.TranslationBackend
import com.typetype.droid.translation.TranslationOutputMode
import com.typetype.droid.translation.TranslationSettings
import com.typetype.droid.translation.TranslationTargetLanguage

class MainActivity : Activity() {
    private val baseHorizontalPadding by lazy { dp(20) }
    private val baseTopPadding by lazy { dp(30) }
    private val baseBottomPadding by lazy { dp(34) }
    private lateinit var preferences: VoiceImePreferences
    private lateinit var streamingOption: TextView
    private lateinit var offlineOption: TextView
    private lateinit var dictationOutputOption: TextView
    private lateinit var translationOutputOption: TextView
    private lateinit var hyMtBackendOption: TextView
    private lateinit var mlKitBackendOption: TextView
    private lateinit var targetLanguageSpinner: Spinner
    private var suppressTargetLanguageSelection = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = VoiceImePreferences(this)
        TypeTypeReturnNotification.hide(this)
        FloatingImeSwitcherService.startIfAllowed(this)
        window.statusBarColor = COLOR_PAGE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(baseHorizontalPadding, baseTopPadding, baseHorizontalPadding, baseBottomPadding)
            setBackgroundColor(COLOR_PAGE)
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = baseTopPadding + systemBars.top,
                bottom = baseBottomPadding + systemBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(content)

        content.addView(
            header(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            },
        )
        content.addView(
            heroCard(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(sectionTitle(getString(R.string.settings_section)))
        content.addView(
            modeSelector(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(space(1, dp(16)))
        content.addView(
            translationSelector(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(space(1, dp(16)))
        content.addView(actionPanel())

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(COLOR_PAGE)
                addView(content)
            },
        )
    }

    private fun header(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_launcher_mark)
                    background = ovalDrawable(Color.WHITE)
                    elevation = dp(2).toFloat()
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                },
                LinearLayout.LayoutParams(dp(62), dp(62)),
            )

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(18), 0, 0, 0)
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(R.string.setup_title)
                            textSize = 24f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(COLOR_TEXT)
                            includeFontPadding = false
                            setLineSpacing(dp(2).toFloat(), 1f)
                        },
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(R.string.setup_subtitle)
                            textSize = 14f
                            setTextColor(COLOR_MUTED)
                            includeFontPadding = false
                            setPadding(0, dp(4), 0, 0)
                        },
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = "纯语音输入 · HY-MT2 翻译"
                            textSize = 12f
                            setTextColor(COLOR_ACCENT_DEEP)
                            includeFontPadding = false
                            background = roundedDrawable(Color.WHITE, dp(12))
                            setPadding(dp(10), dp(5), dp(10), dp(5))
                        }.apply {
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            )
                            params.topMargin = dp(10)
                            layoutParams = params
                        },
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun heroCard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(24))
            background = heroBackground()
            elevation = dp(3).toFloat()

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.ic_mic_line)
                            setColorFilter(Color.WHITE)
                            background = roundedDrawable(COLOR_ACCENT_DEEP, dp(18))
                            setPadding(dp(11), dp(11), dp(11), dp(11))
                        },
                        LinearLayout.LayoutParams(dp(52), dp(52)),
                    )
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(16), 0, 0, 0)
                            addView(
                                TextView(this@MainActivity).apply {
                                    text = getString(R.string.hero_title)
                                    textSize = 22f
                                    typeface = Typeface.DEFAULT_BOLD
                                    setTextColor(COLOR_TEXT)
                                },
                            )
                            addView(
                                TextView(this@MainActivity).apply {
                                    text = getString(R.string.hero_subtitle)
                                    textSize = 14f
                                    setTextColor(COLOR_MUTED_DARK)
                                    setPadding(0, dp(4), 0, 0)
                                },
                            )
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.hero_body)
                    textSize = 15f
                    setTextColor(COLOR_MUTED_DARK)
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(0, dp(10), 0, dp(18))
                },
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(statusPill(getString(R.string.hero_chip_offline), true))
                    addView(space(dp(10), 1))
                    addView(statusPill(getString(R.string.hero_chip_streaming), false))
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "翻译模式会自动切换到稳妥模式"
                    textSize = 12.5f
                    setTextColor(COLOR_ACCENT_DEEP)
                    setPadding(0, dp(14), 0, 0)
                },
            )
        }
    }

    private fun actionPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                primaryActionCard(
                    CardSpec(R.drawable.ic_switch_line, R.string.card_picker_title, R.string.card_picker_desc) {
                        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    },
                ),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(118)).apply {
                    bottomMargin = dp(16)
                },
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(
                        compactActionCard(
                            CardSpec(R.drawable.ic_mic_line, R.string.card_voice_title, R.string.card_voice_desc) {
                                requestMicPermission()
                            },
                        ),
                        LinearLayout.LayoutParams(0, dp(150), 1f).apply { rightMargin = dp(10) },
                    )
                    addView(
                        compactActionCard(
                            CardSpec(R.drawable.ic_keyboard_line, R.string.card_ime_title, R.string.card_ime_desc) {
                                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                            },
                        ),
                        LinearLayout.LayoutParams(0, dp(150), 1f).apply { leftMargin = dp(10) },
                    )
                },
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun modeSelector(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = sectionCardBackground()
            elevation = dp(1).toFloat()

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.ic_stream_line)
                            setColorFilter(COLOR_ACCENT)
                            background = roundedDrawable(COLOR_ACCENT_SOFT, dp(14))
                            setPadding(dp(8), dp(8), dp(8), dp(8))
                        },
                        LinearLayout.LayoutParams(dp(40), dp(40)),
                    )
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(12), 0, 0, 0)
                            addView(
                                TextView(this@MainActivity).apply {
                                    text = getString(R.string.mode_setting_title)
                                    textSize = 18f
                                    typeface = Typeface.DEFAULT_BOLD
                                    setTextColor(COLOR_TEXT)
                                    includeFontPadding = false
                                },
                            )
                            addView(
                                TextView(this@MainActivity).apply {
                                    text = getString(R.string.mode_setting_desc)
                                    textSize = 13.5f
                                    setTextColor(COLOR_MUTED)
                                    setPadding(0, dp(4), 0, 0)
                                },
                            )
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                },
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(14), 0, 0)
                    streamingOption = modeOption(getString(R.string.mode_streaming)) {
                        saveMode(DictationMode.STREAMING)
                    }
                    offlineOption = modeOption(getString(R.string.mode_offline)) {
                        saveMode(DictationMode.OFFLINE)
                    }
                    addView(streamingOption, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(8) })
                    addView(offlineOption, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(8) })
                },
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )

            updateModeSelection(preferences.loadEffectiveMode(), preferences.loadTranslationSettings())
        }
    }

    private fun translationSelector(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = sectionCardBackground()
            elevation = dp(1).toFloat()

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.ic_switch_line)
                            setColorFilter(COLOR_ACCENT)
                            background = roundedDrawable(COLOR_ACCENT_SOFT, dp(14))
                            setPadding(dp(8), dp(8), dp(8), dp(8))
                        },
                        LinearLayout.LayoutParams(dp(40), dp(40)),
                    )
                    addView(
                        LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(12), 0, 0, 0)
                            addView(
                                TextView(this@MainActivity).apply {
                                    text = getString(R.string.translation_setting_title)
                                    textSize = 18f
                                    typeface = Typeface.DEFAULT_BOLD
                                    setTextColor(COLOR_TEXT)
                                    includeFontPadding = false
                                },
                            )
                            addView(
                                TextView(this@MainActivity).apply {
                                    text = getString(R.string.translation_setting_desc)
                                    textSize = 13.5f
                                    setTextColor(COLOR_MUTED)
                                    setPadding(0, dp(4), 0, 0)
                                },
                            )
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                },
            )

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(14), 0, 0)
                    dictationOutputOption = modeOption(getString(R.string.output_dictation)) {
                        saveTranslationOutputMode(TranslationOutputMode.DICTATION)
                    }
                    translationOutputOption = modeOption(getString(R.string.output_translation)) {
                        saveTranslationOutputMode(TranslationOutputMode.TRANSLATION)
                    }
                    addView(dictationOutputOption, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(8) })
                    addView(translationOutputOption, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(8) })
                },
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.translation_target_desc)
                    textSize = 13f
                    setTextColor(COLOR_MUTED)
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(0, dp(14), 0, 0)
                },
            )

            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.translation_backend_desc)
                    textSize = 13f
                    setTextColor(COLOR_MUTED)
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(0, dp(14), 0, 0)
                },
            )

            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(12), 0, 0)
                    hyMtBackendOption = modeOption(getString(R.string.translation_backend_hymt)) {
                        saveTranslationBackend(TranslationBackend.HY_MT)
                    }
                    mlKitBackendOption = modeOption(getString(R.string.translation_backend_mlkit)) {
                        saveTranslationBackend(TranslationBackend.ML_KIT)
                    }
                    addView(hyMtBackendOption, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(6) })
                    addView(mlKitBackendOption, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(6) })
                },
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )

            addView(targetLanguageDropdown())

            updateTranslationSelection(preferences.loadTranslationSettings())
        }
    }

    private fun targetLanguageDropdown(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)

            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.translation_target_title)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(COLOR_TEXT)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )

            addView(space(dp(12), 1))

            targetLanguageSpinner = Spinner(this@MainActivity).apply {
                background = roundedDrawable(Color.rgb(245, 247, 249), dp(14))
                setPadding(dp(12), 0, dp(12), 0)
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    TranslationTargetLanguage.entries.map { it.label },
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                val initialIndex = TranslationTargetLanguage.entries
                    .indexOf(preferences.loadTranslationSettings().targetLanguage)
                    .coerceAtLeast(0)
                setSelection(initialIndex, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long,
                    ) {
                        if (suppressTargetLanguageSelection) return
                        val language = TranslationTargetLanguage.entries[position]
                        if (preferences.loadTranslationSettings().targetLanguage != language) {
                            saveTranslationTargetLanguage(language)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            addView(targetLanguageSpinner, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
    }

    private fun modeOption(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            foreground = selectableForeground()
            setOnClickListener { onClick() }
        }
    }

    private fun primaryActionCard(spec: CardSpec): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(18), dp(18))
            background = sectionCardBackground()
            elevation = dp(1).toFloat()
            isClickable = true
            isFocusable = true
            foreground = selectableForeground()
            setOnClickListener { spec.onClick() }

            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(spec.icon)
                    setColorFilter(Color.WHITE)
                    background = roundedDrawable(COLOR_TEXT, dp(18))
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                },
                LinearLayout.LayoutParams(dp(56), dp(56)),
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), 0, dp(12), 0)
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(spec.title)
                            textSize = 20f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(COLOR_TEXT)
                            includeFontPadding = false
                        },
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(spec.desc)
                            textSize = 14f
                            setTextColor(COLOR_MUTED)
                            setPadding(0, dp(7), 0, 0)
                        },
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_chevron_right_24)
                    setColorFilter(COLOR_MUTED)
                },
                LinearLayout.LayoutParams(dp(24), dp(24)),
            )
        }
    }

    private fun compactActionCard(spec: CardSpec): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(15), dp(16))
            background = sectionCardBackground()
            isClickable = true
            isFocusable = true
            foreground = selectableForeground()
            setOnClickListener { spec.onClick() }

            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(spec.icon)
                    setColorFilter(COLOR_ICON)
                },
                LinearLayout.LayoutParams(dp(32), dp(32)),
            )
            addView(space(1, dp(20)))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(spec.title)
                            textSize = 17f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(COLOR_TEXT)
                            includeFontPadding = false
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(R.drawable.ic_chevron_right_24)
                            setColorFilter(COLOR_MUTED)
                        },
                        LinearLayout.LayoutParams(dp(18), dp(18)),
                    )
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = getString(spec.desc)
                    textSize = 13.5f
                    setTextColor(COLOR_MUTED)
                    setPadding(0, dp(7), 0, 0)
                    setLineSpacing(dp(2).toFloat(), 1f)
                },
            )
        }
    }

    private fun sectionTitle(text: String): View {
        return TextView(this).apply {
            this.text = text
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
            setPadding(dp(2), dp(34), 0, dp(18))
        }
    }

    private fun statusPill(text: String, filled: Boolean): View {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (filled) Color.WHITE else COLOR_ACCENT)
            includeFontPadding = false
            minHeight = dp(36)
            setPadding(dp(16), dp(7), dp(16), dp(7))
            background = roundedDrawable(if (filled) COLOR_ACCENT_DEEP else Color.WHITE, dp(18))
        }
    }

    private fun requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    private fun saveMode(mode: DictationMode) {
        preferences.saveMode(mode)
        val effectiveMode = preferences.loadEffectiveMode()
        updateModeSelection(effectiveMode, preferences.loadTranslationSettings())
        (application as TypeTypeApplication).warmUpAsr(effectiveMode)
    }

    private fun saveTranslationOutputMode(mode: TranslationOutputMode) {
        preferences.saveTranslationOutputMode(mode)
        val settings = preferences.loadTranslationSettings().copy(outputMode = mode)
        updateTranslationSelection(settings)
        updateModeSelection(preferences.loadEffectiveMode(), settings)
        if (mode == TranslationOutputMode.TRANSLATION) {
            (application as TypeTypeApplication).warmUpTranslation(settings.backend, settings.targetLanguage)
            (application as TypeTypeApplication).warmUpAsr(DictationMode.OFFLINE)
        }
    }

    private fun saveTranslationBackend(backend: TranslationBackend) {
        preferences.saveTranslationBackend(backend)
        val previous = preferences.loadTranslationSettings()
        val adjustedTarget = if (backend == TranslationBackend.ML_KIT && !previous.targetLanguage.isMlKitSupported) {
            TranslationTargetLanguage.ENGLISH
        } else {
            previous.targetLanguage
        }
        if (adjustedTarget != previous.targetLanguage) {
            preferences.saveTranslationTargetLanguage(adjustedTarget)
        }
        val settings = previous.copy(backend = backend, targetLanguage = adjustedTarget)
        updateTranslationSelection(settings)
        updateModeSelection(preferences.loadEffectiveMode(), settings)
        if (settings.outputMode == TranslationOutputMode.TRANSLATION) {
            (application as TypeTypeApplication).warmUpTranslation(backend, settings.targetLanguage)
        }
    }

    private fun saveTranslationTargetLanguage(targetLanguage: TranslationTargetLanguage) {
        preferences.saveTranslationTargetLanguage(targetLanguage)
        val settings = preferences.loadTranslationSettings()
        updateTranslationSelection(settings)
        updateModeSelection(preferences.loadEffectiveMode(), settings)
        if (settings.outputMode == TranslationOutputMode.TRANSLATION) {
            (application as TypeTypeApplication).warmUpTranslation(settings.backend, settings.targetLanguage)
        }
    }

    private fun updateModeSelection(mode: DictationMode, translationSettings: TranslationSettings) {
        styleModeOption(streamingOption, mode == DictationMode.STREAMING)
        styleModeOption(offlineOption, mode == DictationMode.OFFLINE)
        val translationEnabled = translationSettings.outputMode == TranslationOutputMode.TRANSLATION
        setTargetOptionEnabled(streamingOption, !translationEnabled)
    }

    private fun updateTranslationSelection(settings: TranslationSettings) {
        styleModeOption(dictationOutputOption, settings.outputMode == TranslationOutputMode.DICTATION)
        styleModeOption(translationOutputOption, settings.outputMode == TranslationOutputMode.TRANSLATION)
        styleModeOption(hyMtBackendOption, settings.backend == TranslationBackend.HY_MT)
        styleModeOption(mlKitBackendOption, settings.backend == TranslationBackend.ML_KIT)
        val targetIndex = TranslationTargetLanguage.entries.indexOf(settings.targetLanguage).coerceAtLeast(0)
        suppressTargetLanguageSelection = true
        targetLanguageSpinner.setSelection(targetIndex, false)
        suppressTargetLanguageSelection = false

        val translationEnabled = settings.outputMode == TranslationOutputMode.TRANSLATION
        setTargetOptionEnabled(hyMtBackendOption, translationEnabled)
        setTargetOptionEnabled(mlKitBackendOption, translationEnabled)
        setTargetOptionEnabled(targetLanguageSpinner, translationEnabled)
    }

    private fun styleModeOption(view: TextView, selected: Boolean) {
        view.setTextColor(if (selected) Color.WHITE else COLOR_TEXT)
        view.background = roundedDrawable(if (selected) COLOR_TEXT else Color.rgb(245, 247, 249), dp(14))
    }

    private fun setTargetOptionEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.45f
    }

    private fun space(width: Int, height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, height)
    }

    private fun roundedDrawable(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun sectionCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.rgb(224, 229, 232))
        }
    }

    private fun heroBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(248, 253, 250), Color.rgb(228, 245, 239)),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), Color.rgb(213, 233, 226))
        }
    }

    private fun ovalDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun selectableForeground() = obtainStyledAttributes(
        intArrayOf(android.R.attr.selectableItemBackground),
    ).let { attrs ->
        attrs.getDrawable(0).also { attrs.recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class CardSpec(
        val icon: Int,
        val title: Int,
        val desc: Int,
        val onClick: () -> Unit,
    )

    private companion object {
        const val REQUEST_RECORD_AUDIO = 1001
        val COLOR_PAGE: Int = Color.rgb(232, 247, 244)
        val COLOR_TEXT: Int = Color.rgb(25, 31, 30)
        val COLOR_MUTED: Int = Color.rgb(143, 153, 151)
        val COLOR_MUTED_DARK: Int = Color.rgb(109, 122, 119)
        val COLOR_ICON: Int = Color.rgb(70, 76, 75)
        val COLOR_ACCENT: Int = Color.rgb(15, 194, 147)
        val COLOR_ACCENT_DEEP: Int = Color.rgb(11, 149, 113)
        val COLOR_ACCENT_SOFT: Int = Color.rgb(222, 247, 240)
    }
}
