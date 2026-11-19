package com.example.core

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONArray
import org.json.JSONObject

class OfflineQueuedException(
    message: String = "Offline or slow network: change queued locally for sync."
) : Exception(message)

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyHindiError(error: Throwable, language: AppLanguage): String {
    if (error is OfflineQueuedException || error is TimeoutCancellationException) {
        return str(
            language,
            "इंटरनेट बंद या धीमा है। परिवर्तन फोन में सुरक्षित कर लिए गए हैं और नेटवर्क आने पर अपने-आप सिंक हो जाएंगे।",
            "Offline or slow network. Changes are saved locally and will sync automatically when online."
        )
    }
    val msg = error.message.orEmpty()
    return when {
        msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
            str(language, "अनुमति अस्वीकृत: आपके खाते को यह कार्य करने का अधिकार नहीं है।", "Permission denied: your role does not allow this action.")
        msg.contains("UNAVAILABLE", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ||
            msg.contains("offline", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ->
            str(language, "इंटरनेट कमजोर या बंद है। परिवर्तन सुरक्षित रखे गए हैं और नेटवर्क आने पर सिंक होंगे।", "Weak or offline network. Changes are queued safely for sync.")
        msg.contains("Quota exceeded", ignoreCase = true) ->
            str(language, "दैनिक डेटाबेस सीमा पूर्ण हो गई है। कृपया कल पुनः प्रयास करें।", "Daily database quota exceeded. Please retry tomorrow.")
        else ->
            error.localizedMessage ?: str(language, "डेटा सहेजने या लोड करने में त्रुटि हुई।", "Error loading or saving data.")
    }
}
