package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: default

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = "PUBLIC",
    val preferredLanguage: String = "hi",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffInvite(
    @DocumentId val id: String = "",
    val employeeId: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val designation: String = "",
    val monthlySalary: Double = 0.0,
    val active: Boolean = true,
    val permissions: Map<String, List<String>> = emptyMap(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val employeeId: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val designation: String = "",
    val monthlySalary: Double = 0.0,
    val active: Boolean = true,
    val permissions: Map<String, List<String>> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicSummary(
    @DocumentId val id: String = "",
    val totalCattle: Long = 0L,
    val healthyCattle: Long = 0L,
    val sickCattle: Long = 0L,
    val todayFeedKg: Double = 0.0,
    val todayExpenseInr: Double = 0.0,
    val totalDonationsInr: Double = 0.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Cattle(
    @DocumentId val id: String = "",
    val animalCode: String = "",
    val name: String = "",
    val category: String = "COW",
    val breed: String = "",
    val sex: String = "FEMALE",
    val ageText: String = "",
    val birthDate: String = "",
    val colour: String = "",
    val weightKg: Double = 0.0,
    val healthStatus: String = "HEALTHY",
    val entryDate: String = "",
    val shedNumber: String = "",
    val identificationMarks: String = "",
    val qrPayload: String = "",
    val photoUri: String? = null,
    val notes: String? = null,
    val publicApproved: Boolean = false,
    val sponsorshipAvailable: Boolean = false,
    val monthlySupportInr: Long = 2100L,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "animalCode" to animalCode,
        "name" to name,
        "category" to category,
        "breed" to breed,
        "sex" to sex,
        "ageText" to ageText,
        "birthDate" to birthDate,
        "colour" to colour,
        "weightKg" to weightKg,
        "healthStatus" to healthStatus,
        "entryDate" to entryDate,
        "shedNumber" to shedNumber,
        "identificationMarks" to identificationMarks,
        "qrPayload" to qrPayload,
        "photoUri" to photoUri,
        "notes" to notes,
        "publicApproved" to publicApproved,
        "sponsorshipAvailable" to sponsorshipAvailable,
        "monthlySupportInr" to monthlySupportInr,
        "createdBy" to createdBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class HealthRecord(
    @DocumentId val id: String = "",
    val cattleId: String = "",
    val cattleName: String = "",
    val animalCode: String = "",
    val recordType: String = "VACCINATION",
    val title: String = "",
    val medicine: String = "",
    val vetName: String = "",
    val costInr: Double = 0.0,
    val weightKg: Double = 0.0,
    val nextFollowUpDate: String = "",
    val reminderActive: Boolean = false,
    val notes: String = "",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "cattleId" to cattleId,
        "cattleName" to cattleName,
        "animalCode" to animalCode,
        "recordType" to recordType,
        "title" to title,
        "medicine" to medicine,
        "vetName" to vetName,
        "costInr" to costInr,
        "weightKg" to weightKg,
        "nextFollowUpDate" to nextFollowUpDate,
        "reminderActive" to reminderActive,
        "notes" to notes,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FeedAndOperationLog(
    @DocumentId val id: String = "",
    val logDate: String = "",
    val greenFodderKg: Double = 0.0,
    val dryFodderBhusaKg: Double = 0.0,
    val concentrateKg: Double = 0.0,
    val waterLiters: Double = 0.0,
    val shedCleaned: Boolean = false,
    val cleaningNotes: String = "",
    val assignedStaffName: String = "",
    val taskTitle: String = "",
    val taskCompleted: Boolean = false,
    val feedStockRemainingKg: Double = 0.0,
    val notes: String = "",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "logDate" to logDate,
        "greenFodderKg" to greenFodderKg,
        "dryFodderBhusaKg" to dryFodderBhusaKg,
        "concentrateKg" to concentrateKg,
        "waterLiters" to waterLiters,
        "shedCleaned" to shedCleaned,
        "cleaningNotes" to cleaningNotes,
        "assignedStaffName" to assignedStaffName,
        "taskTitle" to taskTitle,
        "taskCompleted" to taskCompleted,
        "feedStockRemainingKg" to feedStockRemainingKg,
        "notes" to notes,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StaffOperation(
    @DocumentId val id: String = "",
    val staffUid: String = "",
    val staffName: String = "",
    val entryType: String = "ATTENDANCE",
    val dateStr: String = "",
    val attendanceStatus: String = "PRESENT",
    val amountInr: Double = 0.0,
    val taskDescription: String = "",
    val paidStatus: String = "NA",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "staffUid" to staffUid,
        "staffName" to staffName,
        "entryType" to entryType,
        "dateStr" to dateStr,
        "attendanceStatus" to attendanceStatus,
        "amountInr" to amountInr,
        "taskDescription" to taskDescription,
        "paidStatus" to paidStatus,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FinancialTransaction(
    @DocumentId val id: String = "",
    val txType: String = "EXPENSE",
    val category: String = "FEED_PURCHASE",
    val amountInr: Double = 0.0,
    val paymentMode: String = "CASH",
    val paymentStatus: String = "COMPLETED",
    val partyName: String = "",
    val description: String = "",
    val dateStr: String = "",
    val receiptNumber: String = "",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "txType" to txType,
        "category" to category,
        "amountInr" to amountInr,
        "paymentMode" to paymentMode,
        "paymentStatus" to paymentStatus,
        "partyName" to partyName,
        "description" to description,
        "dateStr" to dateStr,
        "receiptNumber" to receiptNumber,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FarmerRecord(
    @DocumentId val id: String = "",
    val name: String = "",
    val mobile: String = "",
    val village: String = "",
    val address: String = "",
    val cooperationType: String = "SUPPLIER",
    val totalPurchasedInr: Double = 0.0,
    val totalSoldInr: Double = 0.0,
    val outstandingInr: Double = 0.0,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name,
        "mobile" to mobile,
        "village" to village,
        "address" to address,
        "cooperationType" to cooperationType,
        "totalPurchasedInr" to totalPurchasedInr,
        "totalSoldInr" to totalSoldInr,
        "outstandingInr" to outstandingInr,
        "createdBy" to createdBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class FarmerTransaction(
    @DocumentId val id: String = "",
    val farmerId: String = "",
    val farmerName: String = "",
    val village: String = "",
    val direction: String = "PURCHASE_FROM_FARMER",
    val productName: String = "",
    val quantity: Double = 0.0,
    val unit: String = "QUINTAL",
    val qualityGrade: String = "A",
    val ratePerUnitInr: Double = 0.0,
    val totalAmountInr: Double = 0.0,
    val paidAmountInr: Double = 0.0,
    val outstandingInr: Double = 0.0,
    val invoiceNumber: String = "",
    val dateStr: String = "",
    val notes: String = "",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "farmerId" to farmerId,
        "farmerName" to farmerName,
        "village" to village,
        "direction" to direction,
        "productName" to productName,
        "quantity" to quantity,
        "unit" to unit,
        "qualityGrade" to qualityGrade,
        "ratePerUnitInr" to ratePerUnitInr,
        "totalAmountInr" to totalAmountInr,
        "paidAmountInr" to paidAmountInr,
        "outstandingInr" to outstandingInr,
        "invoiceNumber" to invoiceNumber,
        "dateStr" to dateStr,
        "notes" to notes,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CompostBatch(
    @DocumentId val id: String = "",
    val batchCode: String = "",
    val productType: String = "COMPOST",
    val quantity: Double = 0.0,
    val unit: String = "KG",
    val rawDungKg: Double = 0.0,
    val productionCostInr: Double = 0.0,
    val packagingCostInr: Double = 0.0,
    val sellingPricePerUnitInr: Double = 0.0,
    val soldQuantity: Double = 0.0,
    val stockRemaining: Double = 0.0,
    val status: String = "IN_PROGRESS",
    val dateStr: String = "",
    val notes: String = "",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "batchCode" to batchCode,
        "productType" to productType,
        "quantity" to quantity,
        "unit" to unit,
        "rawDungKg" to rawDungKg,
        "productionCostInr" to productionCostInr,
        "packagingCostInr" to packagingCostInr,
        "sellingPricePerUnitInr" to sellingPricePerUnitInr,
        "soldQuantity" to soldQuantity,
        "stockRemaining" to stockRemaining,
        "status" to status,
        "dateStr" to dateStr,
        "notes" to notes,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Donation(
    @DocumentId val id: String = "",
    val donorUid: String = "",
    val donorName: String = "",
    val donorPhone: String = "",
    val donorCity: String = "",
    val purpose: String = "GAU_SEVA",
    val amountInr: Double = 0.0,
    val paymentProviderRef: String = "MANUAL_PENDING",
    val paymentStatus: String = "PENDING_VERIFICATION",
    val receiptNumber: String = "",
    val dateStr: String = "",
    val recordedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "donorUid" to donorUid,
        "donorName" to donorName,
        "donorPhone" to donorPhone,
        "donorCity" to donorCity,
        "purpose" to purpose,
        "amountInr" to amountInr,
        "paymentProviderRef" to paymentProviderRef,
        "paymentStatus" to paymentStatus,
        "receiptNumber" to receiptNumber,
        "dateStr" to dateStr,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Sponsorship(
    @DocumentId val id: String = "",
    val applicantUid: String = "",
    val applicantName: String = "",
    val applicantPhone: String = "",
    val applicantAddress: String = "",
    val cattleId: String = "",
    val cattleName: String = "",
    val animalCode: String = "",
    val monthlySupportInr: Double = 0.0,
    val durationMonths: Long = 1L,
    val totalSupportInr: Double = 0.0,
    val status: String = "PENDING",
    val message: String = "",
    val paymentMode: String = "CASH",
    val paymentStatus: String = "PENDING",
    val paymentRef: String = "",
    val certificateNumber: String = "",
    val disclaimerAccepted: Boolean = false,
    val dateStr: String = "",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "applicantUid" to applicantUid,
        "applicantName" to applicantName,
        "applicantPhone" to applicantPhone,
        "applicantAddress" to applicantAddress,
        "cattleId" to cattleId,
        "cattleName" to cattleName,
        "animalCode" to animalCode,
        "monthlySupportInr" to monthlySupportInr,
        "durationMonths" to durationMonths,
        "totalSupportInr" to totalSupportInr,
        "status" to status,
        "message" to message,
        "paymentMode" to paymentMode,
        "paymentStatus" to paymentStatus,
        "paymentRef" to paymentRef,
        "certificateNumber" to certificateNumber,
        "disclaimerAccepted" to disclaimerAccepted,
        "dateStr" to dateStr,
        "reviewedBy" to reviewedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ProductOrder(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val buyerUid: String = "",
    val buyerName: String = "",
    val buyerPhone: String = "",
    val buyerAddress: String = "",
    val productId: String = "",
    val productName: String = "",
    val unitLabel: String = "",
    val unitPriceInr: Double = 0.0,
    val quantity: Long = 1L,
    val totalAmountInr: Double = 0.0,
    val orderStatus: String = "NEW",
    val paymentMode: String = "CASH",
    val paymentStatus: String = "PENDING",
    val paymentRef: String = "",
    val notes: String = "",
    val adminNote: String = "",
    val dateStr: String = "",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "orderNumber" to orderNumber,
        "buyerUid" to buyerUid,
        "buyerName" to buyerName,
        "buyerPhone" to buyerPhone,
        "buyerAddress" to buyerAddress,
        "productId" to productId,
        "productName" to productName,
        "unitLabel" to unitLabel,
        "unitPriceInr" to unitPriceInr,
        "quantity" to quantity,
        "totalAmountInr" to totalAmountInr,
        "orderStatus" to orderStatus,
        "paymentMode" to paymentMode,
        "paymentStatus" to paymentStatus,
        "paymentRef" to paymentRef,
        "notes" to notes,
        "adminNote" to adminNote,
        "dateStr" to dateStr,
        "reviewedBy" to reviewedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val ALL_ORDER_STATUSES = listOf("NEW", "APPROVED", "READY", "DELIVERY", "COMPLETED", "CANCELLED")
        val ALL_PAYMENT_MODES = listOf("CASH", "UPI", "ONLINE")
        val ALL_PAYMENT_STATUSES = listOf("PENDING", "PAID", "VERIFIED")
    }
}

fun productOrderStatusLabel(status: String, isHindi: Boolean): String = when (status) {
    "NEW" -> if (isHindi) "नया आवेदन" else "New"
    "APPROVED" -> if (isHindi) "स्वीकृत" else "Approved"
    "READY" -> if (isHindi) "तैयार" else "Ready"
    "DELIVERY" -> if (isHindi) "डिलीवरी" else "Delivery"
    "COMPLETED" -> if (isHindi) "पूर्ण" else "Completed"
    "CANCELLED" -> if (isHindi) "रद्द" else "Cancelled"
    else -> status
}

fun paymentStatusLabel(status: String, isHindi: Boolean): String = when (status) {
    "PENDING" -> if (isHindi) "Pending (लंबित)" else "Pending"
    "PAID" -> if (isHindi) "Paid (भुगतान किया)" else "Paid"
    "VERIFIED" -> if (isHindi) "Verified (सत्यापित)" else "Verified"
    else -> status
}

fun paymentModeLabel(mode: String, isHindi: Boolean): String = when (mode) {
    "CASH" -> if (isHindi) "नकद (Cash)" else "Cash"
    "UPI" -> if (isHindi) "UPI" else "UPI"
    "ONLINE" -> if (isHindi) "ऑनलाइन (Online)" else "Online"
    else -> mode
}

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val targetRole: String = "ALL",
    val targetUid: String = "",
    val category: String = "",
    val title: String = "",
    val message: String = "",
    val readByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "targetRole" to targetRole,
        "targetUid" to targetUid,
        "category" to category,
        "title" to title,
        "message" to message,
        "readByUids" to readByUids,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "PUBLIC",
    val action: String = "",
    val module: String = "",
    val entityId: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
