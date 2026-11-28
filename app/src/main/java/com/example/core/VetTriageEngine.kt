package com.example.core

import com.example.data.model.Cattle
import com.example.data.model.todayIsoDateString

enum class VetRiskLevel(
    val labelHi: String,
    val labelEn: String
) {
    NORMAL("सामान्य", "Normal"),
    CAUTION("सावधानी", "Caution"),
    EMERGENCY("आपातकाल", "Emergency");

    fun label(language: AppLanguage): String =
        if (language == AppLanguage.HINDI) labelHi else labelEn

    companion object {
        fun fromRaw(raw: String?): VetRiskLevel {
            val upper = raw?.trim()?.uppercase().orEmpty()
            return when {
                upper == "EMERGENCY" || upper.contains("आपातकाल") || upper.contains("CRITICAL") -> EMERGENCY
                upper == "CAUTION" || upper.contains("सावधानी") || upper.contains("मध्यम") || upper.contains("गंभीर") -> CAUTION
                else -> NORMAL
            }
        }
    }
}

data class VetSourceReference(
    val title: String,
    val url: String,
    val domain: String = "",
    val retrievedDate: String = todayIsoDateString()
) {
    fun serialize(): String = listOf(
        title.replace("|", " ").trim().take(120),
        url.replace("|", "").trim().take(360),
        retrievedDate.replace("|", "").trim().take(20)
    ).joinToString("|")

    companion object {
        fun parse(raw: String): VetSourceReference? {
            val parts = raw.split("|")
            if (parts.size < 2) return null
            val title = parts[0].trim()
            val url = parts[1].trim()
            if (url.isBlank() || (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true))) {
                return null
            }
            val date = parts.getOrNull(2)?.trim().orEmpty().ifBlank { todayIsoDateString() }
            val domain = extractDomain(url)
            return VetSourceReference(
                title = title.ifBlank { domain.ifBlank { "Veterinary Reference" } },
                url = url,
                domain = domain,
                retrievedDate = date
            )
        }

        fun extractDomain(url: String): String {
            return runCatching {
                java.net.URI(url).host?.removePrefix("www.").orEmpty()
            }.getOrDefault("")
        }
    }
}

data class VetTriageAnswers(
    val ageText: String = "",
    val durationText: String = "",
    val temperatureText: String = "",
    val eatingFodder: String = "",
    val drinkingWater: String = "",
    val dungUrineNormal: String = "",
    val ruminating: String = "",
    val ableToStand: String = "",
    val breathingNormal: String = "",
    val pregnantStatus: String = ""
) {
    fun hasAnyAnswer(): Boolean = listOf(
        ageText, durationText, temperatureText, eatingFodder, drinkingWater,
        dungUrineNormal, ruminating, ableToStand, breathingNormal, pregnantStatus
    ).any { it.isNotBlank() }

    fun toReadableSummary(language: AppLanguage): String = buildString {
        if (ageText.isNotBlank()) appendLine(str(language, "• उम्र: $ageText", "• Age: $ageText"))
        if (durationText.isNotBlank()) appendLine(str(language, "• लक्षण अवधि: $durationText", "• Duration: $durationText"))
        if (temperatureText.isNotBlank()) appendLine(str(language, "• शरीर तापमान / बुखार: $temperatureText", "• Body Temp / Fever: $temperatureText"))
        if (eatingFodder.isNotBlank()) appendLine(str(language, "• चारा खाना: $eatingFodder", "• Eating Fodder: $eatingFodder"))
        if (drinkingWater.isNotBlank()) appendLine(str(language, "• पानी पीना: $drinkingWater", "• Drinking Water: $drinkingWater"))
        if (dungUrineNormal.isNotBlank()) appendLine(str(language, "• गोबर व पेशाब: $dungUrineNormal", "• Dung & Urine: $dungUrineNormal"))
        if (ruminating.isNotBlank()) appendLine(str(language, "• जुगाली: $ruminating", "• Rumination: $ruminating"))
        if (ableToStand.isNotBlank()) appendLine(str(language, "• खड़े होने की स्थिति: $ableToStand", "• Able to Stand: $ableToStand"))
        if (breathingNormal.isNotBlank()) appendLine(str(language, "• सांस की स्थिति: $breathingNormal", "• Breathing: $breathingNormal"))
        if (pregnantStatus.isNotBlank()) appendLine(str(language, "• गर्भावस्था स्थिति: $pregnantStatus", "• Pregnancy: $pregnantStatus"))
    }.trim()
}

data class VetTriageAssessment(
    val riskLevel: VetRiskLevel,
    val detectedRedFlagsHi: List<String>,
    val detectedRedFlagsEn: List<String>,
    val missingQuestionsHi: List<String>,
    val missingQuestionsEn: List<String>,
    val requiresImmediateVetCall: Boolean,
    val summaryBannerHi: String,
    val summaryBannerEn: String
)

/**
 * Deterministic veterinary safety & clinical triage analyzer supporting Hindi, Hinglish, and English.
 * Guarantees that critical life-threatening signs ALWAYS trigger EMERGENCY classification and an
 * immediate veterinarian call alert without delaying with general home advice.
 */
object VetTriageEngine {

    private data class RedFlagRule(
        val labelHi: String,
        val labelEn: String,
        val patterns: List<String>
    )

    private val EMERGENCY_RULES = listOf(
        RedFlagRule(
            labelHi = "गाय खड़ी नहीं हो पा रही / गिर गई है",
            labelEn = "Unable to stand / Downer cow",
            patterns = listOf(
                "खड़ी नहीं हो", "खड़े नहीं हो", "खड़े न हो", "खड़ी न हो", "उठ नहीं पा", "उठ न पा",
                "बैठी है उठ", "गिर गई", "पैर काम नहीं", "khadi nahi", "khade nahi", "khade na ho",
                "uth nahi", "uth na pa", "cannot stand", "can't stand", "unable to stand",
                "not able to stand", "downer", "collapsed"
            )
        ),
        RedFlagRule(
            labelHi = "सांस लेने में परेशानी / तेज सांस",
            labelEn = "Breathing difficulty / Respiratory distress",
            patterns = listOf(
                "सांस लेने में", "साँस लेने में", "सांस फूल", "साँस फूल", "सांस की तकलीफ", "साँस की तकलीफ",
                "सांस नहीं ले", "साँस नहीं ले", "दम घुट", "हाँफ रही", "हाफ रही", "मुँह खोलकर सांस",
                "saans lene", "sans lene", "saans phul", "saans ful", "saans me taklif",
                "breathing trouble", "difficulty breathing", "breathless", "gasping", "dyspnea",
                "labored breathing", "respiratory distress", "hard to breathe"
            )
        ),
        RedFlagRule(
            labelHi = "पेट में गंभीर सूजन / आफरा (Bloat)",
            labelEn = "Severe bloat / Swollen abdomen",
            patterns = listOf(
                "पेट बहुत फूल", "पेट फूल गया", "पेट फूला", "पेट में सूजन", "पेट सूज", "आफरा", "अफारा",
                "गैस से पेट", "बायाँ पेट फूल", "बायां पेट फूल", "pet bahut phul", "pet phul gaya",
                "pet fool", "pet me sujan", "pet mein sujan", "afara", "aafra", "aaphra",
                "severe bloat", "bloat", "tympany", "swollen abdomen", "swollen belly", "distended abdomen"
            )
        ),
        RedFlagRule(
            labelHi = "बेहोशी / अचेत होना या झटके आना",
            labelEn = "Unconsciousness / Collapse / Seizures",
            patterns = listOf(
                "बेहोश", "अचेत", "होश नहीं", "चक्कर खाकर", "झटके आ", "ऐंठन",
                "behosh", "unconscious", "faint", "seizure", "convulsion", "coma"
            )
        ),
        RedFlagRule(
            labelHi = "लगातार खाना और पानी दोनों छोड़ देना",
            labelEn = "Persistent refusal of both food and water",
            patterns = listOf(
                "खाना-पानी छोड़", "खाना पानी छोड़", "खाना और पानी दोनों बंद", "न चारा न पानी",
                "चारा और पानी बिल्कुल बंद", "लगातार खाना पानी नहीं", "khana pani band",
                "khana pani chhod", "chara pani band", "not eating and not drinking",
                "stopped eating and drinking", "refusing food and water"
            )
        ),
        RedFlagRule(
            labelHi = "प्रसव (ब्याने) में परेशानी",
            labelEn = "Calving trouble / Dystocia",
            patterns = listOf(
                "प्रसव में परेशानी", "प्रसव समस्या", "ब्याने में दिक्कत", "बच्चा फंस", "जेर नहीं गिर",
                "बच्चेदानी बाहर", "prasav", "byane me dikkat", "calving trouble", "dystocia",
                "prolapse", "difficult calving", "stuck calf"
            )
        ),
        RedFlagRule(
            labelHi = "खून बहना / गंभीर चोट या विषाक्तता",
            labelEn = "Bleeding / Severe injury / Suspected poisoning",
            patterns = listOf(
                "खून बह", "रक्तस्राव", "गंभीर चोट", "हड्डी टूट", "जहर खा", "विषाक्तता", "साँप ने काट",
                "सांप ने काट", "khoon bah", "bleeding", "hemorrhage", "severe injury",
                "fracture", "poison", "snake bite", "toxicity"
            )
        )
    )

    private val CAUTION_PATTERNS = listOf(
        "चारा नहीं खा", "चारा ठीक से नहीं", "खाना नहीं खा", "भूख नहीं", "कम खा रही", "जुगाली नहीं",
        "बुखार", "तापमान", "गर्म शरीर", "दस्त", "पतला गोबर", "कब्ज", "कमजोरी", "सुस्त", "उदास",
        "घाव", "सूजन", "लंगड़ा", "थन में सूजन", "थनैला", "दूध कम", "आँख से पानी", "नाक से पानी",
        "chara nahi", "khana nahi", "bhusa nahi", "bukhar", "dast", "kamjori", "sust",
        "ghav", "sujan", "langda", "thanaila", "jugali nahi",
        "not eating", "fever", "diarrhea", "loose motion", "lethargic", "dull", "weak",
        "wound", "swelling", "limping", "mastitis", "not ruminating", "cough", "खांसी"
    )

    fun evaluate(
        symptomsText: String,
        triageAnswers: VetTriageAnswers = VetTriageAnswers(),
        selectedCow: Cattle? = null
    ): VetTriageAssessment {
        val normalized = buildString {
            append(symptomsText.trim()).append(' ')
            append(triageAnswers.temperatureText).append(' ')
            append(triageAnswers.eatingFodder).append(' ')
            append(triageAnswers.drinkingWater).append(' ')
            append(triageAnswers.dungUrineNormal).append(' ')
            append(triageAnswers.ableToStand).append(' ')
            append(triageAnswers.breathingNormal).append(' ')
            append(triageAnswers.pregnantStatus)
        }.lowercase()

        val flagsHi = mutableListOf<String>()
        val flagsEn = mutableListOf<String>()

        EMERGENCY_RULES.forEach { rule ->
            if (rule.patterns.any { normalized.contains(it) }) {
                flagsHi.add(rule.labelHi)
                flagsEn.add(rule.labelEn)
            }
        }

        // Structured triage red-flag checks
        val standLower = triageAnswers.ableToStand.trim().lowercase()
        if (standLower == "नहीं" || standLower == "no" || standLower.contains("नहीं हो")) {
            if (flagsHi.none { it.contains("खड़ी नहीं") }) {
                flagsHi.add("गाय खड़ी नहीं हो पा रही / गिर गई है")
                flagsEn.add("Unable to stand / Downer cow")
            }
        }

        val breathLower = triageAnswers.breathingNormal.trim().lowercase()
        if (breathLower.contains("तकलीफ") || breathLower.contains("तेज") || breathLower.contains("trouble") || breathLower == "असामान्य") {
            if (flagsHi.none { it.contains("सांस") }) {
                flagsHi.add("सांस लेने में परेशानी / तेज सांस")
                flagsEn.add("Breathing difficulty / Respiratory distress")
            }
        }

        val eatLower = triageAnswers.eatingFodder.trim().lowercase()
        val waterLower = triageAnswers.drinkingWater.trim().lowercase()
        val notEating = eatLower.contains("नहीं") || eatLower.contains("बंद") || eatLower == "no"
        val notDrinking = waterLower.contains("नहीं") || waterLower.contains("बंद") || waterLower == "no"
        if (notEating && notDrinking) {
            if (flagsHi.none { it.contains("खाना और पानी") }) {
                flagsHi.add("लगातार खाना और पानी दोनों छोड़ देना")
                flagsEn.add("Persistent refusal of both food and water")
            }
        }

        // High fever check (>= 105°F or >= 40.5°C)
        val tempNumber = Regex("""(\d{2,3}(?:\.\d)?)""").find(triageAnswers.temperatureText.ifBlank { symptomsText })
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val hasDangerousFever = tempNumber != null && (tempNumber >= 105.0 || (tempNumber in 40.5..44.0))
        if (hasDangerousFever) {
            flagsHi.add("अत्यधिक तेज बुखार ($tempNumber°)")
            flagsEn.add("Dangerously high fever ($tempNumber°)")
        }

        val hasModerateFever = tempNumber != null && ((tempNumber in 102.6..104.9) || (tempNumber in 39.2..40.49))
        val hasCautionSignal = hasModerateFever ||
            notEating ||
            notDrinking ||
            triageAnswers.ruminating.contains("नहीं", ignoreCase = true) ||
            triageAnswers.ruminating.equals("no", ignoreCase = true) ||
            triageAnswers.dungUrineNormal.contains("दस्त") ||
            triageAnswers.dungUrineNormal.contains("असामान्य") ||
            CAUTION_PATTERNS.any { normalized.contains(it) } ||
            selectedCow?.healthStatus?.uppercase() in setOf("SICK", "UNDER_TREATMENT", "ATTENTION")

        val riskLevel = when {
            flagsHi.isNotEmpty() -> VetRiskLevel.EMERGENCY
            hasCautionSignal -> VetRiskLevel.CAUTION
            else -> VetRiskLevel.NORMAL
        }

        val (missingHi, missingEn) = collectMissingTriageQuestions(symptomsText, triageAnswers, selectedCow)

        val bannerHi = when (riskLevel) {
            VetRiskLevel.EMERGENCY -> "आपातकाल (EMERGENCY): ${flagsHi.joinToString(", ")}। सामान्य घरेलू उपाय में समय न गँवाएँ — तुरंत योग्य पशु चिकित्सक को बुलाएँ!"
            VetRiskLevel.CAUTION -> "सावधानी (CAUTION): लक्षणों पर तुरंत निगरानी रखें, प्राथमिक सुरक्षित देखभाल करें और सुधार न होने पर पशु चिकित्सक को दिखाएँ।"
            VetRiskLevel.NORMAL -> "सामान्य (NORMAL): अभी कोई गंभीर आपातकालीन संकेत नहीं मिला है। नियमित देखभाल, स्वच्छ पानी और निगरानी जारी रखें।"
        }
        val bannerEn = when (riskLevel) {
            VetRiskLevel.EMERGENCY -> "EMERGENCY: ${flagsEn.joinToString(", ")}. Do not delay with home remedies — call a qualified veterinarian immediately!"
            VetRiskLevel.CAUTION -> "CAUTION: Monitor closely, provide safe primary care, and consult a veterinarian promptly if symptoms persist or worsen."
            VetRiskLevel.NORMAL -> "NORMAL: No immediate emergency red flag detected. Continue routine observation, hygiene, and clean water."
        }

        return VetTriageAssessment(
            riskLevel = riskLevel,
            detectedRedFlagsHi = flagsHi,
            detectedRedFlagsEn = flagsEn,
            missingQuestionsHi = missingHi,
            missingQuestionsEn = missingEn,
            requiresImmediateVetCall = riskLevel == VetRiskLevel.EMERGENCY,
            summaryBannerHi = bannerHi,
            summaryBannerEn = bannerEn
        )
    }

    /**
     * Prevents AI text from ever downgrading a deterministic EMERGENCY red flag to CAUTION or NORMAL,
     * while allowing AI clinical reasoning to escalate risk if additional serious cues exist.
     */
    fun resolveFinalRiskLevel(aiResponseText: String, baseline: VetRiskLevel): VetRiskLevel {
        val text = aiResponseText.lowercase()
        val aiSuggested = when {
            text.contains("खतरे का स्तर") && text.contains("आपातकाल") -> VetRiskLevel.EMERGENCY
            text.contains("risk level") && text.contains("emergency") -> VetRiskLevel.EMERGENCY
            text.contains("तुरंत पशु चिकित्सक") && text.contains("आपातकाल") -> VetRiskLevel.EMERGENCY
            text.contains("सावधानी") || text.contains("caution") -> VetRiskLevel.CAUTION
            else -> baseline
        }
        return if (aiSuggested.ordinal > baseline.ordinal) aiSuggested else baseline
    }

    private fun collectMissingTriageQuestions(
        symptomsText: String,
        triage: VetTriageAnswers,
        selectedCow: Cattle?
    ): Pair<List<String>, List<String>> {
        val lower = symptomsText.lowercase()
        val hi = mutableListOf<String>()
        val en = mutableListOf<String>()

        if (triage.ageText.isBlank() && selectedCow?.ageText.isNullOrBlank() && !lower.contains("वर्ष") && !lower.contains("साल") && !lower.contains("माह") && !lower.contains("age")) {
            hi.add("गाय की उम्र कितनी है?")
            en.add("What is the cow's age?")
        }
        if (triage.durationText.isBlank() && !lower.contains("दिन") && !lower.contains("घंटे") && !lower.contains("सुबह") && !lower.contains("कल") && !lower.contains("since") && !lower.contains("hours") && !lower.contains("days")) {
            hi.add("यह लक्षण कितने समय / कितने दिन से हैं?")
            en.add("How long have these symptoms been present?")
        }
        if (triage.temperatureText.isBlank() && !lower.contains("बुखार") && !lower.contains("तापमान") && !lower.contains("fever") && !lower.contains("temp")) {
            hi.add("क्या शरीर का तापमान बढ़ा है या बुखार है?")
            en.add("Is body temperature elevated or is there fever?")
        }
        if (triage.eatingFodder.isBlank() && !lower.contains("चारा") && !lower.contains("खाना") && !lower.contains("eat") && !lower.contains("fodder")) {
            hi.add("क्या गाय चारा खा रही है?")
            en.add("Is the cow eating fodder?")
        }
        if (triage.drinkingWater.isBlank() && !lower.contains("पानी") && !lower.contains("water") && !lower.contains("drink")) {
            hi.add("क्या गाय पानी पी रही है?")
            en.add("Is the cow drinking water?")
        }
        if (triage.dungUrineNormal.isBlank() && !lower.contains("गोबर") && !lower.contains("पेशाब") && !lower.contains("दस्त") && !lower.contains("dung") && !lower.contains("urine")) {
            hi.add("क्या गोबर और पेशाब सामान्य है?")
            en.add("Are dung and urine normal?")
        }
        if (triage.ruminating.isBlank() && !lower.contains("जुगाली") && !lower.contains("jugali") && !lower.contains("ruminat")) {
            hi.add("क्या गाय जुगाली कर रही है?")
            en.add("Is the cow ruminating (chewing cud)?")
        }
        if (triage.ableToStand.isBlank() && !lower.contains("खड़ी") && !lower.contains("खड़े") && !lower.contains("उठ") && !lower.contains("stand")) {
            hi.add("क्या गाय आसानी से खड़ी हो पा रही है?")
            en.add("Is the cow able to stand normally?")
        }
        if (triage.breathingNormal.isBlank() && !lower.contains("सांस") && !lower.contains("साँस") && !lower.contains("breath")) {
            hi.add("क्या सांस लेना सामान्य है?")
            en.add("Is breathing normal?")
        }
        if (triage.pregnantStatus.isBlank() && !lower.contains("गर्भवती") && !lower.contains("गाभिन") && !lower.contains("pregnan")) {
            hi.add("क्या गाय गर्भवती (गाभिन) है?")
            en.add("Is the cow pregnant?")
        }

        return hi to en
    }
}
