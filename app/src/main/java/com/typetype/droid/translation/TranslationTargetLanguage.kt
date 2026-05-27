package com.typetype.droid.translation

import com.google.mlkit.nl.translate.TranslateLanguage

enum class TranslationTargetLanguage(
    val label: String,
    val hyMtTargetLabel: String,
    val mlKitCode: String?,
) {
    CHINESE(
        label = "中文",
        hyMtTargetLabel = "中文",
        mlKitCode = null,
    ),
    ENGLISH(
        label = "英语",
        hyMtTargetLabel = "英语",
        mlKitCode = TranslateLanguage.ENGLISH,
    ),
    FRENCH(
        label = "法语",
        hyMtTargetLabel = "法语",
        mlKitCode = null,
    ),
    PORTUGUESE(
        label = "葡萄牙语",
        hyMtTargetLabel = "葡萄牙语",
        mlKitCode = null,
    ),
    SPANISH(
        label = "西班牙语",
        hyMtTargetLabel = "西班牙语",
        mlKitCode = null,
    ),
    JAPANESE(
        label = "日语",
        hyMtTargetLabel = "日语",
        mlKitCode = TranslateLanguage.JAPANESE,
    ),
    TURKISH(
        label = "土耳其语",
        hyMtTargetLabel = "土耳其语",
        mlKitCode = null,
    ),
    RUSSIAN(
        label = "俄语",
        hyMtTargetLabel = "俄语",
        mlKitCode = null,
    ),
    ARABIC(
        label = "阿拉伯语",
        hyMtTargetLabel = "阿拉伯语",
        mlKitCode = null,
    ),
    KOREAN(
        label = "韩语",
        hyMtTargetLabel = "韩语",
        mlKitCode = null,
    ),
    THAI(
        label = "泰语",
        hyMtTargetLabel = "泰语",
        mlKitCode = null,
    ),
    ITALIAN(
        label = "意大利语",
        hyMtTargetLabel = "意大利语",
        mlKitCode = null,
    ),
    GERMAN(
        label = "德语",
        hyMtTargetLabel = "德语",
        mlKitCode = TranslateLanguage.GERMAN,
    ),
    VIETNAMESE(
        label = "越南语",
        hyMtTargetLabel = "越南语",
        mlKitCode = null,
    ),
    MALAY(
        label = "马来语",
        hyMtTargetLabel = "马来语",
        mlKitCode = null,
    ),
    INDONESIAN(
        label = "印尼语",
        hyMtTargetLabel = "印尼语",
        mlKitCode = null,
    ),
    FILIPINO(
        label = "菲律宾语",
        hyMtTargetLabel = "菲律宾语",
        mlKitCode = null,
    ),
    HINDI(
        label = "印地语",
        hyMtTargetLabel = "印地语",
        mlKitCode = null,
    ),
    TRADITIONAL_CHINESE(
        label = "繁体中文",
        hyMtTargetLabel = "繁体中文",
        mlKitCode = null,
    ),
    POLISH(
        label = "波兰语",
        hyMtTargetLabel = "波兰语",
        mlKitCode = null,
    ),
    CZECH(
        label = "捷克语",
        hyMtTargetLabel = "捷克语",
        mlKitCode = null,
    ),
    DUTCH(
        label = "荷兰语",
        hyMtTargetLabel = "荷兰语",
        mlKitCode = null,
    ),
    KHMER(
        label = "高棉语",
        hyMtTargetLabel = "高棉语",
        mlKitCode = null,
    ),
    BURMESE(
        label = "缅甸语",
        hyMtTargetLabel = "缅甸语",
        mlKitCode = null,
    ),
    PERSIAN(
        label = "波斯语",
        hyMtTargetLabel = "波斯语",
        mlKitCode = null,
    ),
    GUJARATI(
        label = "古吉拉特语",
        hyMtTargetLabel = "古吉拉特语",
        mlKitCode = null,
    ),
    URDU(
        label = "乌尔都语",
        hyMtTargetLabel = "乌尔都语",
        mlKitCode = null,
    ),
    TELUGU(
        label = "泰卢固语",
        hyMtTargetLabel = "泰卢固语",
        mlKitCode = null,
    ),
    MARATHI(
        label = "马拉地语",
        hyMtTargetLabel = "马拉地语",
        mlKitCode = null,
    ),
    HEBREW(
        label = "希伯来语",
        hyMtTargetLabel = "希伯来语",
        mlKitCode = null,
    ),
    BENGALI(
        label = "孟加拉语",
        hyMtTargetLabel = "孟加拉语",
        mlKitCode = null,
    ),
    TAMIL(
        label = "泰米尔语",
        hyMtTargetLabel = "泰米尔语",
        mlKitCode = null,
    ),
    UKRAINIAN(
        label = "乌克兰语",
        hyMtTargetLabel = "乌克兰语",
        mlKitCode = null,
    ),
    TIBETAN(
        label = "藏语",
        hyMtTargetLabel = "藏语",
        mlKitCode = null,
    ),
    KAZAKH(
        label = "哈萨克语",
        hyMtTargetLabel = "哈萨克语",
        mlKitCode = null,
    ),
    MONGOLIAN(
        label = "蒙古语",
        hyMtTargetLabel = "蒙古语",
        mlKitCode = null,
    ),
    UYGHUR(
        label = "维吾尔语",
        hyMtTargetLabel = "维吾尔语",
        mlKitCode = null,
    ),
    CANTONESE(
        label = "粤语",
        hyMtTargetLabel = "粤语",
        mlKitCode = null,
    );

    val isMlKitSupported: Boolean
        get() = mlKitCode != null
}
