package com.example.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val label: String) {
    HINDI("hi", "हिंदी"),
    ENGLISH("en", "English")
}

enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.HINDI }

@Composable
fun str(hi: String, en: String): String {
    return if (LocalAppLanguage.current == AppLanguage.HINDI) hi else en
}

fun str(lang: AppLanguage, hi: String, en: String): String {
    return if (lang == AppLanguage.HINDI) hi else en
}

object GosalaBrand {
    const val NAME_HI = "जय भोले गौशाला समिति"
    const val NAME_EN = "Jay Bhole Gosala Samiti"
    const val MAIN_SLOGAN_HI_1 = "महादेव की कृपा, गौमाता की सेवा।"
    const val MAIN_SLOGAN_HI_2 = "बेसहारा गौवंश का सहारा बनना ही हमारा संकल्प है।"
    const val MAIN_SLOGAN_EN = "Grace of Mahadev, Service to Gaumata — Supporting destitute cattle is our solemn pledge."
    const val SUPPORTING_SLOGAN_HI = "गौ सेवा • जीव रक्षा • समाज सेवा"
    const val SUPPORTING_SLOGAN_EN = "Cow Care • Animal Protection • Social Service"
    const val ADDRESS_HI = "ग्राम – मेहदोरा, विकासखंड – पोरसा, जिला – मुरैना, मध्य प्रदेश, भारत।"
    const val ADDRESS_EN = "Village – Mehdora, Block – Porsa, District – Morena, Madhya Pradesh, India."
    const val SHORT_ADDRESS_HI = "मेहदोरा, पोरसा, मुरैना (म. प्र.)"
    const val SHORT_ADDRESS_EN = "Mehdora, Porsa, Morena (M.P.)"
}
