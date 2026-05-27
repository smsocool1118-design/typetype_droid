package com.typetype.droid.translation

class FallbackTranslationEngine(
    private val primary: TranslationEngine,
    private val fallback: TranslationEngine,
) : TranslationEngine {
    override fun warmUp(targetLanguage: TranslationTargetLanguage) {
        runCatching { primary.warmUp(targetLanguage) }
            .recoverCatching { error ->
                if (!targetLanguage.isMlKitSupported) throw error
                fallback.warmUp(targetLanguage)
            }
            .getOrThrow()
    }

    override fun translate(text: String, targetLanguage: TranslationTargetLanguage): String {
        return runCatching { primary.translate(text, targetLanguage) }
            .recoverCatching { error ->
                if (!targetLanguage.isMlKitSupported) throw error
                fallback.translate(text, targetLanguage)
            }
            .getOrThrow()
    }

    override fun close() {
        runCatching { primary.close() }
        runCatching { fallback.close() }
    }
}
