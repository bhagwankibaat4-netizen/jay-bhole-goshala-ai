package com.example.data.model

import com.example.core.AppLanguage
import com.example.core.str

enum class LoginPortal {
    PUBLIC,
    STAFF,
    ADMIN
}

enum class UserRole {
    ADMIN,
    STAFF,
    PUBLIC;

    fun label(lang: AppLanguage): String = when (this) {
        ADMIN -> str(lang, "प्रशासक (Admin)", "Admin")
        STAFF -> str(lang, "कर्मचारी (Staff)", "Staff")
        PUBLIC -> str(lang, "सार्वजनिक उपयोगकर्ता", "Public User")
    }
}

enum class CrudAction {
    VIEW,
    ADD,
    EDIT,
    DELETE;

    fun label(lang: AppLanguage): String = when (this) {
        VIEW -> str(lang, "देखें", "View")
        ADD -> str(lang, "जोड़ें", "Add")
        EDIT -> str(lang, "संपादित करें", "Edit")
        DELETE -> str(lang, "हटाएं", "Delete")
    }
}

enum class AppModule(val key: String, val labelHi: String, val labelEn: String) {
    CATTLE("cattle", "गाय-बैल प्रबंधन", "Cattle Management"),
    HEALTH("health", "स्वास्थ्य व उपचार", "Health & Vet"),
    FEED("feed", "चारा व दैनिक कार्य", "Feed & Daily Ops"),
    STAFF("staff", "कर्मचारी प्रबंधन", "Staff Management"),
    FINANCE("finance", "वित्त आय-व्यय", "Finance & Ledger"),
    FARMERS("farmers", "किसान सहयोग", "Farmer Cooperation"),
    PRODUCTION("production", "गोबर व खाद उत्पाद", "Compost & Products"),
    DONATIONS("donations", "दान व गौ-गोद", "Donations & Adoption"),
    REPORTS("reports", "रिपोर्ट व निर्यात", "Reports & Exports"),
    AI("ai", "AI गौशाला सहायक", "AI Assistant");

    fun label(lang: AppLanguage): String = str(lang, labelHi, labelEn)
}

data class UserSession(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: UserRole = UserRole.PUBLIC,
    val permissions: Map<String, List<String>> = emptyMap(),
    val staffActive: Boolean = false
) {
    fun hasPermission(module: AppModule, action: CrudAction): Boolean {
        if (role == UserRole.ADMIN) return true
        if (role == UserRole.STAFF && staffActive) {
            return permissions[module.key]?.contains(action.name) == true
        }
        return false
    }

    fun canView(module: AppModule): Boolean {
        if (role == UserRole.ADMIN) return true
        if (role == UserRole.STAFF) return hasPermission(module, CrudAction.VIEW)
        // Public users can view approved public cattle, public donations/adoption, and public AI assistant
        return module == AppModule.CATTLE || module == AppModule.DONATIONS || module == AppModule.AI
    }

    fun canAdd(module: AppModule): Boolean = hasPermission(module, CrudAction.ADD)
    fun canEdit(module: AppModule): Boolean = hasPermission(module, CrudAction.EDIT)
    fun canDelete(module: AppModule): Boolean = hasPermission(module, CrudAction.DELETE)
}
