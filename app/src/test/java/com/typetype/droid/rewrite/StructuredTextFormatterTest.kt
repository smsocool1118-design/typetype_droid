package com.typetype.droid.rewrite

import org.junit.Assert.assertEquals
import org.junit.Test

class StructuredTextFormatterTest {
    @Test
    fun shortTextGetsPunctuationWithoutOverStructuring() {
        assertEquals("今天我们测试语音输入。", StructuredTextFormatter.rewrite("嗯今天我们测试语音输入"))
    }

    @Test
    fun enumeratedSpeechBecomesNumberedListWithRisk() {
        val raw = "今天开会主要有三件事第一产品这周要把流式输入修好第二翻译功能粤语要继续测试第三我下周一之前整理使用说明另外风险是开机自启动可能不稳定"

        assertEquals(
            """
            今天开会主要有3件事：
            1. 产品这周要把流式输入修好。
            2. 翻译功能粤语要继续测试。
            3. 我下周一之前整理使用说明。
            风险：开机自启动可能不稳定。
            """.trimIndent(),
            StructuredTextFormatter.rewrite(raw),
        )
    }

    @Test
    fun streamingBoundaryUsesCommaBetweenSegments() {
        assertEquals(
            "，继续测试",
            StructuredTextFormatter.prefixStreamingBoundaryPunctuation("第一段", "继续测试"),
        )
    }

    @Test
    fun questionClauseBeforeStatementGetsInternalQuestionMark() {
        assertEquals(
            "你今天有没有空？明天继续测试。",
            StructuredTextFormatter.rewrite("你今天有没有空，明天继续测试"),
        )
        assertEquals(
            "我们不知道有没有空，明天再说。",
            StructuredTextFormatter.rewrite("我们不知道有没有空，明天再说"),
        )
    }

    @Test
    fun streamingQuestionBoundaryUsesQuestionMark() {
        assertEquals(
            "？明天继续测试",
            StructuredTextFormatter.prefixStreamingBoundaryPunctuation("你今天有没有空", "明天继续测试"),
        )
        assertEquals(
            "你今天有没有空？明天继续测试",
            StructuredTextFormatter.punctuateStreamingQuestions("你今天有没有空，明天继续测试"),
        )
    }

    @Test
    fun asrUnknownArtifactsAreRemovedWithoutDroppingEnglish() {
        assertEquals(
            "有 enough，行了",
            StructuredTextFormatter.removeAsrArtifacts("有<unk> enough，<unk>行了"),
        )
        assertEquals(
            "有，够。行了",
            StructuredTextFormatter.removeAsrArtifacts("有<unk>，够。<unk>，<unk><unk>，行了"),
        )
    }

    @Test
    fun uppercaseAsrEnglishBecomesNaturalCasing() {
        assertEquals(
            "你好呀，小伙子，hello hello hello。",
            StructuredTextFormatter.removeAsrArtifacts("你好呀，小伙子，HELLO HELLO HELLO。"),
        )
        assertEquals(
            "hello，go to sleep。OK 了。",
            StructuredTextFormatter.removeAsrArtifacts("HELLO，GO TO SLEEP。OK 了。"),
        )
    }

    @Test
    fun technicalEnglishAcronymsKeepCanonicalCasing() {
        assertEquals(
            "AI API USB APK OK ML Kit OpenAI iOS Android HY-MT2",
            StructuredTextFormatter.removeAsrArtifacts("AI API USB APK OK ML KIT OPENAI IOS ANDROID HY-MT2"),
        )
    }

    @Test
    fun chineseNumberWordsBecomeArabicNumbers() {
        assertEquals(
            "我要买3个苹果，20块钱。",
            StructuredTextFormatter.rewrite("我要买三个苹果，二十块钱"),
        )
        assertEquals(
            "电话是13800123456",
            StructuredTextFormatter.punctuateStreamingQuestions("电话是一三八零零一二三四五六"),
        )
        assertEquals(
            "2026年6月10日晚上8点30分开会。",
            StructuredTextFormatter.rewrite("二零二六年六月十日晚上八点三十分开会"),
        )
    }

    @Test
    fun weekdayWordsAreNotConvertedToDigits() {
        assertEquals(
            "下周一上午开会。",
            StructuredTextFormatter.rewrite("下周一上午开会"),
        )
    }

    @Test
    fun windows032ConservativeNumberCasesAreSupported() {
        assertEquals("我的手机号是13812345678。", StructuredTextFormatter.rewrite("我的手机号是一三八一二三四五六七八"))
        assertEquals("客服电话是4008001234。", StructuredTextFormatter.rewrite("客服电话是四零零八零零一二三四"))
        assertEquals("座机是02168889999。", StructuredTextFormatter.rewrite("座机是零二一六八八八九九九九"))
        assertEquals("分机号806。", StructuredTextFormatter.rewrite("分机号八零六"))
        assertEquals("订单号是123456。", StructuredTextFormatter.rewrite("订单号是一二三四五六"))
        assertEquals("周一上午10点开会。", StructuredTextFormatter.rewrite("周一上午十点开会"))
        assertEquals("周二下午2点 review。", StructuredTextFormatter.rewrite("周二下午两点 review"))
        assertEquals("星期三晚上8点直播。", StructuredTextFormatter.rewrite("星期三晚上八点直播"))
        assertEquals("6月11号下午3点半开 meeting。", StructuredTextFormatter.rewrite("六月十一号下午三点半开 meeting"))
        assertEquals("2015年3月8日。", StructuredTextFormatter.rewrite("零一五年三月八日"))
        assertEquals("今天 ROI 是30%。", StructuredTextFormatter.rewrite("今天 ROI 是百分之三十"))
        assertEquals("预算是12000元。", StructuredTextFormatter.rewrite("预算是一万二千元"))
        assertEquals("这个版本是3.2.1。", StructuredTextFormatter.rewrite("这个版本是三点二点一"))
    }

    @Test
    fun idiomNumbersArePreserved() {
        assertEquals("一心一意做好服务。", StructuredTextFormatter.rewrite("一心一意做好服务"))
        assertEquals("三三两两的人过来。", StructuredTextFormatter.rewrite("三三两两的人过来"))
        assertEquals("周一周二都可以。", StructuredTextFormatter.rewrite("周一周二都可以"))
    }
}
