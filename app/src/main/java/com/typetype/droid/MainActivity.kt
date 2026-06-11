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
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.typetype.droid.session.DictationMode
import com.typetype.droid.settings.LlmProviderPresets
import com.typetype.droid.settings.RewriteBackendPreference
import com.typetype.droid.settings.RewriteScenario
import com.typetype.droid.settings.StreamingEnhancementMode
import com.typetype.droid.settings.StreamingModelPreference
import com.typetype.droid.settings.VoiceImePreferences
import com.typetype.droid.settings.VoicePackagePreference
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
    private lateinit var llmProviderSpinner: Spinner
    private lateinit var llmApiKeyInput: EditText
    private lateinit var llmBaseUrlInput: EditText
    private lateinit var llmModelInput: EditText
    private lateinit var llmStatusText: TextView
    private var suppressTargetLanguageSelection = false
    private var suppressLlmProviderSelection = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = VoiceImePreferences(this)
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
        content.addView(
            android031Selector(),
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

    override fun onResume() {
        super.onResume()
        FloatingImeSwitcherService.startIfAllowed(this)
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
            addView(
                primaryActionCard(
                    CardSpec(R.drawable.ic_switch_line, R.string.card_overlay_title, R.string.card_overlay_desc) {
                        requestOverlayPermission()
                    },
                ),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(106)).apply {
                    topMargin = dp(16)
                },
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

    private fun android031Selector(): View {
        val settings = preferences.loadAndroid031Settings()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                settingsSectionCard(
                    title = "流式设置",
                    desc = "决定实时听写的速度、准确率和流式整理入口。",
                    icon = R.drawable.ic_stream_line,
                ) {
                    addView(
                        dropdownRow(
                            title = "实时模型",
                            desc = "多语言实时适合日常；中文高准确率适合长句和客户对话。",
                            labels = StreamingModelPreference.entries.map { it.label },
                            selectedIndex = StreamingModelPreference.entries.indexOf(settings.streamingModel),
                        ) { index ->
                            preferences.saveStreamingModel(StreamingModelPreference.entries[index])
                        },
                    )
                    addView(
                        dropdownRow(
                            title = "识别策略",
                            desc = "轻量实时优先启动速度；高准确率优先会预热更重的本地识别资源。",
                            labels = VoicePackagePreference.entries.map { it.label },
                            selectedIndex = VoicePackagePreference.entries.indexOf(settings.voicePackage),
                        ) { index ->
                            preferences.saveVoicePackage(VoicePackagePreference.entries[index])
                        },
                    )
                    addView(
                        dropdownRow(
                            title = "流式处理方式",
                            desc = "离线隐私增强只做本机标点和术语保护；AI 联网增强可一键润写当前文字。",
                            labels = StreamingEnhancementMode.entries.map { it.label },
                            selectedIndex = StreamingEnhancementMode.entries.indexOf(settings.streamingEnhancementMode),
                        ) { index ->
                            preferences.saveStreamingEnhancementMode(StreamingEnhancementMode.entries[index])
                        },
                    )
                    addView(settingsSwitch("流式整理面板", "在输入法面板显示整理方式和一键带入按钮。", settings.streamingAiPanelEnabled) {
                        preferences.saveStreamingAiPanelEnabled(it)
                    })
                    addView(
                        dropdownRow(
                            title = "流式润写场景",
                            desc = "流式整理带入会按这个模板处理；与 Windows 端模板保持一致。",
                            labels = RewriteScenario.entries.map { it.menuLabel },
                            selectedIndex = RewriteScenario.entries.indexOf(settings.rewriteScenario),
                        ) { index ->
                            preferences.saveRewriteScenario(RewriteScenario.entries[index])
                        },
                    )
                },
            )
            addView(space(1, dp(16)))
            addView(
                settingsSectionCard(
                    title = "稳妥设置",
                    desc = "稳妥模式会先完成识别，再按所选方式一次性写入。",
                    icon = R.drawable.ic_switch_line,
                ) {
                    addView(
                        dropdownRow(
                            title = "稳妥模式处理",
                            desc = "离线结构化不联网；AI 联网润写会调用你配置的模型并清理思考过程。",
                            labels = RewriteBackendPreference.entries.map { it.label },
                            selectedIndex = RewriteBackendPreference.entries.indexOf(settings.rewriteBackend),
                        ) { index ->
                            preferences.saveRewriteBackend(RewriteBackendPreference.entries[index])
                            refreshLlmFormIfReady()
                        },
                    )
                    addView(settingsSwitch("结构化润写", "自动补标点、去口头语、整理枚举列表。", settings.voiceFormattingEnabled) {
                        preferences.saveVoiceFormattingEnabled(it)
                    })
                },
            )
            addView(space(1, dp(16)))
            addView(
                settingsSectionCard(
                    title = "术语与词库治理",
                    desc = "管理本地词库、自动学习和 code-switch 保护，减少人名、品牌、项目名被误改。",
                    icon = R.drawable.ic_keyboard_line,
                ) {
                    addView(settingsSwitch("自动学习词汇", "识别到的新词进入本地个人词典，用于后续术语保护。", settings.autoLearningEnabled) {
                        preferences.saveAutoLearningEnabled(it)
                    })
                    addView(settingsSwitch("本地大词库保护", "启用系统基础词库和 code-switch 词库，减少专名被误改。", settings.systemLexiconEnabled) {
                        preferences.saveSystemLexiconEnabled(it)
                    })
                },
            )
            addView(space(1, dp(16)))
            addView(
                settingsSectionCard(
                    title = "AI 平台",
                    desc = "兼容 OpenAI 风格接口，适合连接你自己的模型平台。",
                    icon = R.drawable.ic_mic_line,
                ) {
                    addView(llmRewritePanel(settings))
                },
            )
        }
    }

    private fun settingsSectionCard(
        title: String,
        desc: String,
        icon: Int,
        buildContent: LinearLayout.() -> Unit,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = sectionCardBackground()
            elevation = dp(1).toFloat()
            addView(featureHeader(title, desc, icon))
            buildContent()
        }
    }

    private fun featureHeader(title: String, desc: String, icon: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                ImageView(this@MainActivity).apply {
                    setImageResource(icon)
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
                            text = title
                            textSize = 18f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(COLOR_TEXT)
                            includeFontPadding = false
                        },
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = desc
                            textSize = 13.5f
                            setTextColor(COLOR_MUTED)
                            setPadding(0, dp(4), 0, 0)
                            setLineSpacing(dp(2).toFloat(), 1f)
                        },
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
    }

    private fun dropdownRow(
        title: String,
        desc: String,
        labels: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
            addView(rowTitle(title, desc))
            addView(
                Spinner(this@MainActivity).apply {
                    background = roundedDrawable(Color.rgb(245, 247, 249), dp(14))
                    setPadding(dp(12), 0, dp(12), 0)
                    adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_item,
                        labels,
                    ).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }
                    var currentIndex = selectedIndex.coerceAtLeast(0)
                    setSelection(currentIndex, false)
                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (position == currentIndex) return
                            currentIndex = position
                            onSelected(position)
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
                    topMargin = dp(8)
                },
            )
        }
    }

    private fun settingsSwitch(
        title: String,
        desc: String,
        checked: Boolean,
        onCheckedChanged: (Boolean) -> Unit,
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
            addView(
                rowTitle(title, desc),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                Switch(this@MainActivity).apply {
                    isChecked = checked
                    setOnCheckedChangeListener { _, value -> onCheckedChanged(value) }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun rowTitle(title: String, desc: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(COLOR_TEXT)
                    includeFontPadding = false
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = desc
                    textSize = 12.5f
                    setTextColor(COLOR_MUTED)
                    setPadding(0, dp(4), 0, 0)
                    setLineSpacing(dp(2).toFloat(), 1f)
                },
            )
        }
    }

    private fun llmRewritePanel(settings: com.typetype.droid.settings.Android031Settings): View {
        val llm = settings.llmRewrite
        val providerIndex = LlmProviderPresets.all.indexOfFirst { it.key == llm.providerKey }.coerceAtLeast(0)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, 0)
            addView(settingsSwitch("LLM 结构化润写 API", "填写平台、模型和 Key 后，原文听写可进入联网结构化整理。", llm.enabled) {
                preferences.saveLlmEnabled(it)
            })
            addView(rowTitle("模型平台", LlmProviderPresets.presetFor(llm.providerKey).apiKeyHelp))
            llmProviderSpinner = Spinner(this@MainActivity).apply {
                background = roundedDrawable(Color.rgb(245, 247, 249), dp(14))
                setPadding(dp(12), 0, dp(12), 0)
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    LlmProviderPresets.all.map { it.label },
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                setSelection(providerIndex, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (suppressLlmProviderSelection) return
                        val provider = LlmProviderPresets.all[position]
                        if (preferences.loadAndroid031Settings().llmRewrite.providerKey != provider.key) {
                            preferences.saveLlmProvider(provider.key)
                            refreshLlmForm()
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }
            addView(llmProviderSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
                topMargin = dp(8)
            })
            llmBaseUrlInput = settingsEditText(llm.baseUrl, "Base URL")
            addView(llmBaseUrlInput)
            llmModelInput = settingsEditText(llm.model, "模型名")
            addView(llmModelInput)
            llmApiKeyInput = settingsEditText(llm.apiKey, "API Key", password = true)
            addView(llmApiKeyInput)
            llmStatusText = TextView(this@MainActivity).apply {
                text = "未测试连接"
                textSize = 12.5f
                setTextColor(COLOR_MUTED)
                setPadding(0, dp(8), 0, 0)
            }
            addView(llmStatusText)
            addView(
                solidActionButton("测试连接") { button -> testLlmConnection(button) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    topMargin = dp(10)
                },
            )
        }
    }

    private fun settingsEditText(initialValue: String, hintText: String, password: Boolean = false): EditText {
        return EditText(this).apply {
            setText(initialValue)
            hint = hintText
            textSize = 13.5f
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedDrawable(Color.rgb(245, 247, 249), dp(14))
            inputType = if (password) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveLlmForm()
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun refreshLlmForm() {
        val llm = preferences.loadAndroid031Settings().llmRewrite
        suppressLlmProviderSelection = true
        llmProviderSpinner.setSelection(LlmProviderPresets.all.indexOfFirst { it.key == llm.providerKey }.coerceAtLeast(0), false)
        suppressLlmProviderSelection = false
        llmBaseUrlInput.setText(llm.baseUrl)
        llmModelInput.setText(llm.model)
        llmApiKeyInput.setText(llm.apiKey)
        llmStatusText.text = LlmProviderPresets.presetFor(llm.providerKey).apiKeyHelp
    }

    private fun refreshLlmFormIfReady() {
        if (::llmProviderSpinner.isInitialized &&
            ::llmBaseUrlInput.isInitialized &&
            ::llmModelInput.isInitialized &&
            ::llmApiKeyInput.isInitialized &&
            ::llmStatusText.isInitialized
        ) {
            refreshLlmForm()
        }
    }

    private fun saveLlmForm() {
        if (!::llmApiKeyInput.isInitialized) return
        preferences.saveLlmBaseUrl(llmBaseUrlInput.text.toString())
        preferences.saveLlmModel(llmModelInput.text.toString())
        preferences.saveLlmApiKey(llmApiKeyInput.text.toString())
    }

    private fun solidActionButton(label: String, onClick: (TextView) -> Unit): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = roundedDrawable(COLOR_TEXT, dp(14))
            isClickable = true
            isFocusable = true
            foreground = selectableForeground()
            setOnClickListener { onClick(this) }
        }
    }

    private fun testLlmConnection(button: TextView) {
        saveLlmForm()
        val config = preferences.loadAndroid031Settings().llmRewrite.copy(enabled = true)
        if (config.apiKey.isBlank()) {
            Toast.makeText(this, "请先填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }
        button.isEnabled = false
        llmStatusText.text = "正在测试连接..."
        Thread {
            val result = (application as TypeTypeApplication).llmRewriteEngine.testConnection(config)
            runOnUiThread {
                button.isEnabled = true
                llmStatusText.text = result.message
                llmStatusText.setTextColor(if (result.ok) COLOR_ACCENT_DEEP else Color.rgb(191, 64, 64))
            }
        }.start()
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

    private fun requestOverlayPermission() {
        if (FloatingImeSwitcherService.canDrawOverlays(this)) {
            FloatingImeSwitcherService.startIfAllowed(this)
            Toast.makeText(this, R.string.overlay_permission_ready, Toast.LENGTH_SHORT).show()
            return
        }
        FloatingImeSwitcherService.requestOverlayPermission(this)
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
