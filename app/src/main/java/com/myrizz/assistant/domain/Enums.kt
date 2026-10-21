package com.myrizz.assistant.domain

import androidx.annotation.StringRes
import com.myrizz.assistant.R

/** Reply styles. [apiId] values must match backend/src/prompts.js STYLES. */
enum class ReplyStyle(val apiId: String, @StringRes val labelRes: Int) {
    FLIRTY("flirty", R.string.style_flirty),
    SMOOTH("smooth", R.string.style_smooth),
    FUNNY("funny", R.string.style_funny),
    CONFIDENT("confident", R.string.style_confident),
    ROMANTIC("romantic", R.string.style_romantic),
    CASUAL("casual", R.string.style_casual),
    PLAYFUL("playful", R.string.style_playful),
    RESPECTFUL("respectful", R.string.style_respectful),
    BOLD("bold", R.string.style_bold),
    SHORT_SWEET("short_sweet", R.string.style_short_sweet),
    SERIOUS("serious", R.string.style_serious),
    WITTY("witty", R.string.style_witty),
}

/** Language the replies are written in. */
enum class ReplyLanguage(val apiId: String, @StringRes val labelRes: Int) {
    AUTO("auto", R.string.reply_lang_auto),
    GREEK("el", R.string.reply_lang_el),
    ENGLISH("en", R.string.reply_lang_en),
    GREEKLISH("greeklish", R.string.reply_lang_greeklish),
}

/** Language of the app's own UI. */
enum class UiLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.lang_system),
    ENGLISH("en", R.string.lang_en),
    GREEK("el", R.string.lang_el),
}

/** AI text tools. [apiId] values must match backend/src/prompts.js TOOLS (REPLY is handled by /v1/replies). */
enum class ToolType(val apiId: String, @StringRes val labelRes: Int, val textRequired: Boolean = true) {
    REPLY("reply", R.string.tool_reply),
    IMPROVE("improve", R.string.tool_improve),
    FLIRTIER("flirtier", R.string.tool_flirty),
    FUNNIER("funnier", R.string.tool_funny),
    SHORTER("shorter", R.string.tool_shorter),
    CONFIDENT("confident", R.string.tool_confident),
    FIX("fix", R.string.tool_fix),
    TRANSLATE("translate", R.string.tool_translate),
    STARTER("starter", R.string.tool_starter, textRequired = false),
    FOLLOWUP("followup", R.string.tool_followup),
    TONE("tone", R.string.tool_tone),
}
