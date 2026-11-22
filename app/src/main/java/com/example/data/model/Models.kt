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
    val photoStoragePath: String? = null,
    val vaccinationSummary: String? = null,
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
        "photoStoragePath" to photoStoragePath,
        "vaccinationSummary" to vaccinationSummary,
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
    val suppliesOffered: String = "",
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
        "suppliesOffered" to suppliesOffered,
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
    val paymentStatus: String = "PAID",
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
        "paymentStatus" to paymentStatus,
        "dateStr" to dateStr,
        "notes" to notes,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val ALL_PAYMENT_STATUSES = listOf("PAID", "PARTIAL", "PENDING")
    }
}

fun farmerPaymentStatusLabel(status: String, isHindi: Boolean): String = when (status) {
    "PAID" -> if (isHindi) "पूर्ण भुगतान (Paid)" else "Paid"
    "PARTIAL" -> if (isHindi) "आंशिक (Partial)" else "Partial"
    "PENDING" -> if (isHindi) "बकाया (Pending)" else "Pending"
    else -> status
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
    val staffNames: String = "",
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
        "staffNames" to staffNames,
        "notes" to notes,
        "recordedBy" to recordedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

fun productionProductTypeLabel(type: String, isHindi: Boolean): String = when (type) {
    "COMPOST" -> if (isHindi) "जैविक खाद (कम्पोस्ट)" else "Organic Compost"
    "VERMICOMPOST" -> if (isHindi) "वर्मी कम्पोस्ट" else "Vermicompost"
    "KANDE" -> if (isHindi) "गोबर के कंडे" else "Cow Dung Cakes (Kande)"
    "GAU_KASHTHA" -> if (isHindi) "गोबर की लकड़ी (गौ-काष्ठ)" else "Cow Dung Logs (Gau-Kashtha)"
    "DHOOP" -> if (isHindi) "प्राकृतिक धूपबत्ती" else "Natural Dhoop"
    "OTHER_PRODUCT" -> if (isHindi) "अन्य गोबर उत्पाद" else "Other Dung Products"
    "DUNG_COLLECTION" -> if (isHindi) "कच्चा गोबर संग्रहण" else "Raw Dung Collection"
    "DRY_MANURE" -> if (isHindi) "सूखी गोबर खाद" else "Dry Manure"
    "NURSERY_PLANTS" -> if (isHindi) "नर्सरी पौधे" else "Nursery Plants"
    "PACKAGED_PRODUCT" -> if (isHindi) "पैकेज्ड उत्पाद" else "Packaged Product"
    else -> type
}

@Keep
@IgnoreExtraProperties
data class GoshalaProduct(
    @DocumentId val id: String = "",
    val nameHi: String = "",
    val nameEn: String = "",
    val categoryKey: String = "MANURE",
    val descriptionHi: String = "",
    val descriptionEn: String = "",
    val priceInr: Double = 0.0,
    val stockQuantity: Long = 0L,
    val unitLabel: String = "किलो",
    val available: Boolean = true,
    val photoKey: String = "compost",
    val photoUri: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "nameHi" to nameHi,
        "nameEn" to nameEn,
        "categoryKey" to categoryKey,
        "descriptionHi" to descriptionHi,
        "descriptionEn" to descriptionEn,
        "priceInr" to priceInr,
        "stockQuantity" to stockQuantity,
        "unitLabel" to unitLabel,
        "available" to available,
        "photoKey" to photoKey,
        "photoUri" to photoUri,
        "createdBy" to createdBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val ALL_CATEGORIES = listOf("MANURE", "FUEL", "PUJA", "OTHER")
        val PRESET_PHOTO_KEYS = listOf("compost", "vermicompost", "kande", "gaukashtha", "dhoop")
        val SUGGESTED_UNITS = listOf("50 किग्रा बैग", "40 किग्रा बैग", "किलो", "पैकेट", "पीस", "25 नग पैक", "10 किग्रा बंडल", "12 नग सेट")

        fun defaultSeedProducts(): List<GoshalaProduct> = listOf(
            GoshalaProduct(
                id = "compost",
                nameHi = "जैविक गोबर खाद",
                nameEn = "Organic Cow Dung Manure",
                categoryKey = "MANURE",
                descriptionHi = "देशी गाय के गोबर से प्राकृतिक विधि द्वारा तैयार पोषक तत्वों से भरपूर जैविक खाद — खेत की उर्वरता बढ़ाए।",
                descriptionEn = "Nutrient-rich natural fertilizer crafted from indigenous cow dung to restore soil fertility.",
                priceInr = 350.0,
                stockQuantity = 100L,
                unitLabel = "50 किग्रा बैग",
                available = true,
                photoKey = "compost"
            ),
            GoshalaProduct(
                id = "vermicompost",
                nameHi = "वर्मी कम्पोस्ट (केंचुआ खाद)",
                nameEn = "Vermicompost",
                categoryKey = "MANURE",
                descriptionHi = "जैविक खेती, सब्ज़ी, बागवानी व नर्सरी पौधों के लिए उच्च गुणवत्ता वाली छनी हुई केंचुआ खाद।",
                descriptionEn = "Fine-sieved earthworm vermicompost ideal for organic farming, vegetables, and home gardens.",
                priceInr = 450.0,
                stockQuantity = 80L,
                unitLabel = "40 किग्रा बैग",
                available = true,
                photoKey = "vermicompost"
            ),
            GoshalaProduct(
                id = "kande",
                nameHi = "गोबर के कंडे",
                nameEn = "Cow Dung Cakes (Kande)",
                categoryKey = "FUEL",
                descriptionHi = "हवन, यज्ञ, पूजा-पाठ एवं पारंपरिक उपयोग हेतु धूप में सुखाए गए शुद्ध देशी गाय के कंडे।",
                descriptionEn = "Sun-dried pure indigenous cow dung cakes for havan, rituals, and traditional eco-friendly use.",
                priceInr = 120.0,
                stockQuantity = 150L,
                unitLabel = "25 नग पैक",
                available = true,
                photoKey = "kande"
            ),
            GoshalaProduct(
                id = "gaukashtha",
                nameHi = "गोबर की लकड़ी / उपले (गौ-काष्ठ)",
                nameEn = "Cow Dung Logs (Gau-Kashtha)",
                categoryKey = "FUEL",
                descriptionHi = "वृक्ष संरक्षण हेतु गोबर से निर्मित पर्यावरण-अनुकूल लकड़ी — होलिका दहन, यज्ञ व ईंधन के लिए सर्वोत्तम।",
                descriptionEn = "Eco-friendly compressed cow dung logs to save trees — ideal for rituals, bonfires, and clean fuel.",
                priceInr = 180.0,
                stockQuantity = 60L,
                unitLabel = "10 किग्रा बंडल",
                available = true,
                photoKey = "gaukashtha"
            ),
            GoshalaProduct(
                id = "dhoop",
                nameHi = "प्राकृतिक धूपबत्ती",
                nameEn = "Natural Cow Dung Dhoop",
                categoryKey = "PUJA",
                descriptionHi = "गाय के गोबर, हवन सामग्री, गूगल व प्राकृतिक जड़ी-बूटियों से बनी सुगंधित रसायन-मुक्त धूपबत्ती।",
                descriptionEn = "Chemical-free aromatic incense cones made with cow dung, guggal, and sacred herbal blends.",
                priceInr = 80.0,
                stockQuantity = 120L,
                unitLabel = "30 कोन डिब्बा",
                available = true,
                photoKey = "dhoop"
            ),
            GoshalaProduct(
                id = "other_products",
                nameHi = "गोबर से बने अन्य उत्पाद (दीये व गमले)",
                nameEn = "Other Cow Dung Crafts (Diyas & Pots)",
                categoryKey = "OTHER",
                descriptionHi = "पर्यावरण-अनुकूल गोमय दीये, पौध-गमले व हवन कप जो उपयोग के बाद मिट्टी में खाद बन जाते हैं।",
                descriptionEn = "Biodegradable cow dung diyas, nursery planters, and havan cups that enrich soil after use.",
                priceInr = 150.0,
                stockQuantity = 45L,
                unitLabel = "12 नग सेट",
                available = true,
                photoKey = "dhoop"
            )
        )
    }
}

fun productCategoryLabel(categoryKey: String, isHindi: Boolean): String = when (categoryKey) {
    "MANURE" -> if (isHindi) "जैविक खाद" else "Organic Manure"
    "FUEL" -> if (isHindi) "कंडे व गौ-काष्ठ" else "Kande & Logs"
    "PUJA" -> if (isHindi) "धूपबत्ती व पूजा" else "Dhoop & Puja"
    "OTHER" -> if (isHindi) "अन्य उत्पाद" else "Other Products"
    else -> categoryKey
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

enum class NotificationCategory(
    val titleHi: String,
    val titleEn: String,
    val emoji: String
) {
    ACTIVITIES("गौशाला गतिविधियाँ", "Goshala Activities", "🐄"),
    PRODUCTS("गौशाला उत्पाद", "Goshala Products", "🌿"),
    PRODUCT_AVAILABILITY("उत्पाद उपलब्धता एवं बिक्री", "Product Availability & Sales", "🛒"),
    DONATIONS("दान एवं सहयोग", "Donations & Support", "❤️"),
    ADOPTION("गौ-गोद योजना", "Cow Adoption Scheme", "🐮"),
    ANNOUNCEMENTS("महत्वपूर्ण घोषणाएँ", "Important Announcements", "📢"),
    REMINDERS("रिमाइंडर", "Reminders", "🔔");

    fun label(isHindi: Boolean): String = if (isHindi) titleHi else titleEn

    companion object {
        fun fromRaw(raw: String?): NotificationCategory {
            val key = raw?.trim()?.uppercase().orEmpty()
            enumValues<NotificationCategory>().firstOrNull { it.name == key }?.let { return it }
            return when (key) {
                "HEALTH", "VACCINATION", "FEED", "FOLLOW_UP", "REMINDER", "ALERT" -> REMINDERS
                "DONATION", "SEVA" -> DONATIONS
                "SPONSORSHIP", "COW_ADOPTION" -> ADOPTION
                "ORDER", "PRODUCT_ORDER", "SALES", "PRICE_UPDATE" -> PRODUCT_AVAILABILITY
                "PRODUCT", "COMPOST", "GOBAR" -> PRODUCTS
                "ACTIVITY", "CATTLE", "EVENT", "CAMP" -> ACTIVITIES
                else -> ANNOUNCEMENTS
            }
        }
    }
}

enum class ReminderRecurrence(val titleHi: String, val titleEn: String) {
    ONCE("एक बार", "Once"),
    DAILY("दैनिक", "Daily"),
    WEEKLY("साप्ताहिक", "Weekly"),
    MONTHLY("मासिक", "Monthly");

    fun label(isHindi: Boolean): String = if (isHindi) titleHi else titleEn

    companion object {
        fun fromRaw(raw: String?): ReminderRecurrence =
            enumValues<ReminderRecurrence>().firstOrNull { it.name == raw?.trim()?.uppercase() } ?: ONCE
    }
}

enum class NotificationPriority(val titleHi: String, val titleEn: String) {
    NORMAL("सामान्य", "Normal"),
    HIGH("उच्च", "High"),
    URGENT("अति-आवश्यक", "Urgent");

    fun label(isHindi: Boolean): String = if (isHindi) titleHi else titleEn

    companion object {
        fun fromRaw(raw: String?): NotificationPriority =
            enumValues<NotificationPriority>().firstOrNull { it.name == raw?.trim()?.uppercase() } ?: NORMAL
    }
}

fun todayIsoDateString(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

fun normalizeNotifModuleKey(raw: String?): String {
    val clean = raw?.trim().orEmpty()
    if (clean.isBlank()) return ""
    val matched = enumValues<AppModule>().firstOrNull {
        it.key.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
    }
    return matched?.key ?: clean.lowercase().take(40)
}

fun computeNotificationDedupKey(
    title: String,
    category: String,
    targetRole: String,
    publishDate: String,
    itemKind: String,
    reminderTime: String,
    targetUid: String = ""
): String {
    val normTitle = title.trim().lowercase().replace(Regex("\\s+"), " ").take(70)
    val normCat = NotificationCategory.fromRaw(category).name
    val normRole = targetRole.trim().uppercase().ifBlank { "ALL" }
    val normUid = targetUid.trim().take(28)
    val normDate = publishDate.trim().take(10)
    val normKind = if (itemKind.equals("REMINDER", ignoreCase = true)) "REMINDER" else "NOTICE"
    val normTime = reminderTime.trim().take(10)
    return "$normKind|$normRole|$normUid|$normCat|$normDate|$normTime|$normTitle".take(180)
}

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val targetRole: String = "ALL",
    val targetUid: String = "",
    val category: String = NotificationCategory.ANNOUNCEMENTS.name,
    val title: String = "",
    val message: String = "",
    val photoUrl: String = "",
    val published: Boolean = true,
    val publishDate: String = "",
    val expiryDate: String = "",
    val itemKind: String = "NOTICE",
    val reminderTime: String = "",
    val recurrence: String = ReminderRecurrence.ONCE.name,
    val priority: String = NotificationPriority.NORMAL.name,
    val assigneeName: String = "",
    val targetModule: String = "",
    val createdBy: String = "",
    val dedupKey: String = "",
    val readByUids: List<String> = emptyList(),
    val completedByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): NotificationCategory = NotificationCategory.fromRaw(category)

    fun resolvedRecurrence(): ReminderRecurrence = ReminderRecurrence.fromRaw(recurrence)

    fun resolvedPriority(): NotificationPriority = NotificationPriority.fromRaw(priority)

    fun isReminderItem(): Boolean =
        itemKind.equals("REMINDER", ignoreCase = true) || resolvedCategory() == NotificationCategory.REMINDERS

    fun isReadBy(uid: String): Boolean = uid.isNotBlank() && readByUids.contains(uid)

    fun isCompletedBy(uid: String): Boolean = uid.isNotBlank() && completedByUids.contains(uid)

    fun isScheduledFuture(todayIso: String = todayIsoDateString()): Boolean =
        publishDate.isNotBlank() && publishDate > todayIso

    fun isExpired(todayIso: String = todayIsoDateString()): Boolean =
        expiryDate.isNotBlank() && expiryDate < todayIso

    fun isHistoryForUser(uid: String, todayIso: String = todayIsoDateString()): Boolean =
        isExpired(todayIso) || isReadBy(uid)

    fun isVisibleToSession(session: UserSession, todayIso: String = todayIsoDateString()): Boolean {
        if (session.role == UserRole.ADMIN) return true
        if (!published) return false
        if (isScheduledFuture(todayIso)) return false
        // Strict Specific-User / Specific-Staff privacy isolation: if targetUid is set, ONLY that UID sees it!
        if (targetUid.isNotBlank()) {
            return targetUid == session.uid
        }
        return when (session.role) {
            UserRole.PUBLIC -> targetRole == "PUBLIC" || targetRole == "ALL"
            UserRole.STAFF -> {
                if (!session.staffActive) {
                    targetRole == "PUBLIC" || targetRole == "ALL"
                } else if (targetRole == "PUBLIC" || targetRole == "ALL") {
                    true
                } else if (targetRole == "STAFF") {
                    val normMod = normalizeNotifModuleKey(targetModule)
                    if (normMod.isBlank()) {
                        true
                    } else {
                        val mod = enumValues<AppModule>().firstOrNull {
                            it.key.equals(normMod, ignoreCase = true) || it.name.equals(normMod, ignoreCase = true)
                        }
                        mod != null && session.hasPermission(mod, CrudAction.VIEW)
                    }
                } else {
                    false
                }
            }
            UserRole.ADMIN -> true
        }
    }

    fun toCreateMap(): Map<String, Any> {
        val safeRole = targetRole.trim().uppercase().takeIf { it in setOf("ALL", "PUBLIC", "STAFF", "ADMIN") } ?: "ALL"
        val safeKind = if (itemKind.equals("REMINDER", ignoreCase = true)) "REMINDER" else "NOTICE"
        val safeRecurrence = resolvedRecurrence().name
        val safePriority = resolvedPriority().name
        val safeModule = normalizeNotifModuleKey(targetModule)
        val safeDate = publishDate.trim().ifBlank { todayIsoDateString() }.take(20)
        val safeDedup = dedupKey.trim().ifBlank {
            computeNotificationDedupKey(title, category, safeRole, safeDate, safeKind, reminderTime, targetUid)
        }.take(200)
        return mapOf(
            "targetRole" to safeRole,
            "targetUid" to targetUid.trim().take(128),
            "category" to category.trim().ifBlank { NotificationCategory.ANNOUNCEMENTS.name }.take(40),
            "title" to title.trim().take(140),
            "message" to message.trim().take(1500),
            "photoUrl" to photoUrl.trim().take(2000),
            "published" to published,
            "publishDate" to safeDate,
            "expiryDate" to expiryDate.trim().take(20),
            "itemKind" to safeKind,
            "reminderTime" to reminderTime.trim().take(20),
            "recurrence" to safeRecurrence,
            "priority" to safePriority,
            "assigneeName" to assigneeName.trim().take(120),
            "targetModule" to safeModule,
            "createdBy" to createdBy.trim().take(128),
            "dedupKey" to safeDedup,
            "readByUids" to readByUids.distinct().take(500),
            "completedByUids" to completedByUids.distinct().take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdateMap(): Map<String, Any> {
        val safeRole = targetRole.trim().uppercase().takeIf { it in setOf("ALL", "PUBLIC", "STAFF", "ADMIN") } ?: "ALL"
        val safeKind = if (itemKind.equals("REMINDER", ignoreCase = true)) "REMINDER" else "NOTICE"
        val safeRecurrence = resolvedRecurrence().name
        val safePriority = resolvedPriority().name
        val safeModule = normalizeNotifModuleKey(targetModule)
        val safeDate = publishDate.trim().ifBlank { todayIsoDateString() }.take(20)
        val safeDedup = dedupKey.trim().ifBlank {
            computeNotificationDedupKey(title, category, safeRole, safeDate, safeKind, reminderTime, targetUid)
        }.take(200)
        return mapOf(
            "targetRole" to safeRole,
            "targetUid" to targetUid.trim().take(128),
            "category" to category.trim().ifBlank { NotificationCategory.ANNOUNCEMENTS.name }.take(40),
            "title" to title.trim().take(140),
            "message" to message.trim().take(1500),
            "photoUrl" to photoUrl.trim().take(2000),
            "published" to published,
            "publishDate" to safeDate,
            "expiryDate" to expiryDate.trim().take(20),
            "itemKind" to safeKind,
            "reminderTime" to reminderTime.trim().take(20),
            "recurrence" to safeRecurrence,
            "priority" to safePriority,
            "assigneeName" to assigneeName.trim().take(120),
            "targetModule" to safeModule,
            "dedupKey" to safeDedup,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
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
