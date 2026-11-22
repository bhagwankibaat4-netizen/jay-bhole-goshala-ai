package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.Cattle
import com.example.data.model.FeedAndOperationLog
import com.example.data.model.HealthRecord
import com.example.ui.components.CowPhotoImage
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CattleQrScannerDialog(
    language: AppLanguage,
    isSearching: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var torchEnabled by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var scanPaused by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequested = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Resume camera scanning automatically when error is cleared
    LaunchedEffect(errorMessage, isSearching) {
        if (errorMessage == null && !isSearching) {
            scanPaused = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("cattle_qr_scanner_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = ForestGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = str(language, "गाय QR कोड स्कैनर", "Cow QR Code Scanner"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasCameraPermission && activeCamera?.cameraInfo?.hasFlashUnit() == true) {
                            IconButton(
                                onClick = {
                                    torchEnabled = !torchEnabled
                                    activeCamera?.cameraControl?.enableTorch(torchEnabled)
                                },
                                modifier = Modifier.testTag("qr_scanner_torch_button")
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = str(language, "फ्लैशलाइट", "Flashlight"),
                                    tint = if (torchEnabled) Saffron else ForestGreen
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("qr_scanner_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = str(language, "बंद करें", "Close"))
                        }
                    }
                }

                // Live CameraX Preview Area (or Permission Card)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0E1D15))
                        .border(2.dp, ForestGreen, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        CameraXQrPreview(
                            paused = scanPaused || isSearching || errorMessage != null,
                            onCameraBound = { activeCamera = it },
                            onQrDecoded = { payload ->
                                if (!scanPaused && !isSearching) {
                                    scanPaused = true
                                    onQrCodeDetected(payload)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Viewfinder overlay frame
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .border(2.5.dp, Golden, RoundedCornerShape(16.dp))
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.58f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = str(language, "गाय के QR कोड को फ्रेम के अंदर रखें", "Align Cow QR code inside the frame"),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = Golden,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = str(
                                    language,
                                    "QR कोड स्कैन करने के लिए कैमरे की अनुमति आवश्यक है।",
                                    "Camera permission is required to scan Cow QR codes."
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                modifier = Modifier.testTag("qr_request_camera_permission_btn")
                            ) {
                                Text(str(language, "कैमरा अनुमति दें", "Grant Camera Permission"))
                            }
                        }
                    }

                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = Golden)
                                Text(
                                    text = str(language, "Firebase से गाय का रिकॉर्ड खोजा जा रहा है...", "Fetching cow record from Firebase..."),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                // Clear Hindi Error Banner for invalid QR or Cow Not Found
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = SoftRedTint,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_scanner_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AlertRed, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = errorMessage,
                                    color = AlertRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = {
                                    onClearError()
                                    scanPaused = false
                                },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("qr_scanner_retry_button")
                            ) {
                                Text(str(language, "पुनः स्कैन करें", "Scan Again"), color = ForestGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Manual Cow ID / QR Payload Fallback
                Text(
                    text = str(language, "या Cow ID / QR कोड सीधे दर्ज करें:", "Or enter Cow ID / QR code manually:"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = {
                            manualInput = it
                            if (errorMessage != null) onClearError()
                        },
                        placeholder = { Text(str(language, "जैसे: JBGS-G-101 या Cow ID", "e.g. JBGS-G-101 or Cow ID")) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_manual_input")
                    )
                    Button(
                        onClick = {
                            val trimmed = manualInput.trim()
                            if (trimmed.isNotBlank()) {
                                scanPaused = true
                                onQrCodeDetected(trimmed)
                            }
                        },
                        enabled = !isSearching && manualInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.testTag("qr_manual_search_button")
                    ) {
                        Text(str(language, "खोजें", "Find"))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraXQrPreview(
    paused: Boolean,
    onCameraBound: (Camera?) -> Unit,
    onQrDecoded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pausedState by rememberUpdatedState(paused)
    val onQrDecodedState by rememberUpdatedState(onQrDecoded)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val isDecoding = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            onCameraBound(null)
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    analyzeFrameForQr(
                                        imageProxy = imageProxy,
                                        paused = pausedState,
                                        isDecoding = isDecoding,
                                        onDecoded = { decoded ->
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onQrDecodedState(decoded)
                                            }
                                        }
                                    )
                                }
                            }
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        onCameraBound(camera)
                    } catch (e: Exception) {
                        Log.w("CattleQrScanner", "CameraX initialization failed", e)
                        onCameraBound(null)
                    }
                },
                ContextCompat.getMainExecutor(ctx)
            )
            previewView
        }
    )
}

private fun analyzeFrameForQr(
    imageProxy: ImageProxy,
    paused: Boolean,
    isDecoding: AtomicBoolean,
    onDecoded: (String) -> Unit
) {
    if (paused || !isDecoding.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }
    try {
        val plane = imageProxy.planes.firstOrNull()
        if (plane != null) {
            val width = imageProxy.width
            val height = imageProxy.height
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val yBytes = ByteArray(width * height)

            if (pixelStride == 1 && rowStride == width) {
                buffer.rewind()
                buffer.get(yBytes, 0, yBytes.size.coerceAtMost(buffer.remaining()))
            } else {
                val rowData = ByteArray(rowStride)
                buffer.rewind()
                for (row in 0 until height) {
                    val bytesToRead = rowStride.coerceAtMost(buffer.remaining())
                    if (bytesToRead <= 0) break
                    buffer.get(rowData, 0, bytesToRead)
                    val destOffset = row * width
                    for (col in 0 until width) {
                        val srcIndex = col * pixelStride
                        if (srcIndex < bytesToRead) {
                            yBytes[destOffset + col] = rowData[srcIndex]
                        }
                    }
                }
            }
            val decoded = QrAndExportUtils.decodeQrFromYuvByteArray(yBytes, width, height)
            if (!decoded.isNullOrBlank()) {
                onDecoded(decoded)
            }
        }
    } catch (e: Exception) {
        Log.d("CattleQrScanner", "Frame QR decode skipped: ${e.message}")
    } finally {
        isDecoding.set(false)
        imageProxy.close()
    }
}

@Composable
fun CattleDetailFullScreen(
    cattle: Cattle,
    language: AppLanguage,
    healthRecords: List<HealthRecord>,
    feedLogs: List<FeedAndOperationLog>,
    canEdit: Boolean,
    canDelete: Boolean,
    openedFromQrScan: Boolean,
    onBack: () -> Unit,
    onShowQr: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSponsor: () -> Unit
) {
    BackHandler(onBack = onBack)

    val isHindi = language == AppLanguage.HINDI
    val cowHealth = remember(healthRecords, cattle.id, cattle.animalCode) {
        healthRecords.filter { rec ->
            (cattle.id.isNotBlank() && rec.cattleId == cattle.id) ||
                (cattle.animalCode.isNotBlank() && rec.animalCode.equals(cattle.animalCode, ignoreCase = true))
        }
    }
    val vaccinationRecords = remember(cowHealth) {
        cowHealth.filter { it.recordType.equals("VACCINATION", ignoreCase = true) }
    }
    val illnessTreatmentRecords = remember(cowHealth) {
        cowHealth.filter { !it.recordType.equals("VACCINATION", ignoreCase = true) }
    }
    val recentFeedLogs = remember(feedLogs) { feedLogs.take(5) }

    val qrPayload = remember(cattle.id, cattle.animalCode, cattle.qrPayload) {
        cattle.qrPayload.ifBlank { QrAndExportUtils.buildCattleQrPayload(cattle.id, cattle.animalCode) }
    }
    val qrBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, qrPayload) {
        value = withContext(Dispatchers.Default) {
            QrAndExportUtils.generateQrBitmap(qrPayload, 320)
        }
    }

    val statusColor = when (cattle.healthStatus) {
        "HEALTHY" -> GoshalaGreen
        "SICK" -> AlertRed
        else -> Saffron
    }
    val statusLabel = when (cattle.healthStatus) {
        "HEALTHY" -> str(language, "स्वस्थ (HEALTHY)", "Healthy")
        "SICK" -> str(language, "बीमार (SICK)", "Sick")
        "UNDER_TREATMENT" -> str(language, "उपचारधीन (UNDER TREATMENT)", "Under Treatment")
        else -> cattle.healthStatus
    }
    val categoryLabel = when (cattle.category) {
        "COW" -> str(language, "गाय (Cow)", "Cow")
        "BULL" -> str(language, "बैल / नंदी (Bull)", "Bull")
        "CALF" -> str(language, "बछड़ा / बछिया (Calf)", "Calf")
        else -> cattle.category
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("cattle_detail_full_screen")
    ) {
        // Detail Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("cattle_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = str(language, "वापस सूची में", "Back to list"),
                        tint = ForestGreen
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cattle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Text(
                        text = "${cattle.animalCode} • ${str(language, "गाय का संपूर्ण विवरण", "Complete Cow Profile")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEdit && !openedFromQrScan) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("cattle_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = str(language, "संपादित करें", "Edit"), tint = ForestGreen)
                    }
                }
                if (canDelete && !openedFromQrScan) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("cattle_delete_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = str(language, "हटाएं", "Delete"), tint = AlertRed)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("cattle_detail_scroll_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (openedFromQrScan) {
                item {
                    Surface(
                        color = SoftGreenTint,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cattle_detail_qr_verified_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = str(
                                    language,
                                    "QR कोड सत्यापित — सार्वजनिक दृश्य (केवल देखने हेतु) • Firebase से गाय का लाइव रिकॉर्ड लोड हुआ।",
                                    "QR Code Verified — Public Read-Only View • Live cow profile loaded from Firebase."
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = ForestGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 1. Hero Photo, Name, Unique ID, Health Badge
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        key(cattle.id, cattle.photoUri) {
                            CowPhotoImage(
                                photoUri = cattle.photoUri,
                                contentDescription = cattle.name,
                                contentScale = ContentScale.Crop,
                                testTagPrefix = "cattle_detail_photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cattle.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${str(language, "गाय ID", "Cow ID")}: ${cattle.animalCode}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ForestGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                if (cattle.id.isNotBlank()) {
                                    Text(
                                        text = "${str(language, "Unique Firebase ID", "Unique Firebase ID")}: ${cattle.id}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onShowQr,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cattle_detail_show_qr_btn")
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(str(language, "QR कोड देखें", "View QR"))
                            }
                            if (cattle.sponsorshipAvailable) {
                                Button(
                                    onClick = onSponsor,
                                    colors = ButtonDefaults.buttonColors(containerColor = Saffron),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cattle_detail_sponsor_btn")
                                ) {
                                    Icon(Icons.Default.VolunteerActivism, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(str(language, "गाय गोद लें", "Adopt Cow"))
                                }
                            }
                        }
                    }
                }
            }

            // 2. Basic & Arrival / Purchase Details Card
            item {
                DetailSectionCard(
                    title = str(language, "बुनियादी एवं आगमन / खरीद जानकारी", "Basic & Arrival / Purchase Information"),
                    icon = Icons.Default.Info
                ) {
                    CowDetailRow(str(language, "श्रेणी व लिंग", "Category / Sex"), "$categoryLabel • ${cattle.sex}")
                    CowDetailRow(str(language, "नस्ल (Breed)", "Breed"), cattle.breed.ifBlank { "-" })
                    CowDetailRow(str(language, "उम्र / जन्मतिथि", "Age / Birth Date"), "${cattle.ageText.ifBlank { "-" }}${if (cattle.birthDate.isNotBlank()) " (${cattle.birthDate})" else ""}")
                    CowDetailRow(str(language, "वजन (Weight)", "Weight"), if (cattle.weightKg > 0) "${cattle.weightKg} किग्रा (kg)" else "-")
                    CowDetailRow(str(language, "रंग (Colour)", "Colour"), cattle.colour.ifBlank { "-" })
                    CowDetailRow(str(language, "स्वास्थ्य स्थिति", "Health Status"), statusLabel)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    CowDetailRow(
                        str(language, "खरीद / प्रवेश तिथि", "Purchase / Entry Date"),
                        cattle.entryDate.ifBlank { "-" }
                    )
                    CowDetailRow(str(language, "शेड नंबर", "Shed Number"), cattle.shedNumber.ifBlank { "-" })
                    CowDetailRow(str(language, "पहचान चिह्न", "ID Marks"), cattle.identificationMarks.ifBlank { "-" })
                    CowDetailRow(
                        str(language, "गौ-गोद सेवा", "Cow Adoption"),
                        if (cattle.sponsorshipAvailable) {
                            str(language, "उपलब्ध (₹${cattle.monthlySupportInr}/माह)", "Available (₹${cattle.monthlySupportInr}/month)")
                        } else {
                            str(language, "उपलब्ध नहीं", "Not listed")
                        }
                    )
                    CowDetailRow(
                        str(language, "खरीद / अन्य जानकारी", "Arrival / Other Notes"),
                        cattle.notes?.takeIf { it.isNotBlank() } ?: str(language, "कोई अतिरिक्त टिप्पणी दर्ज नहीं", "No additional notes recorded")
                    )
                }
            }

            // 3. Vaccination Records Card
            item {
                DetailSectionCard(
                    title = str(language, "टीकाकरण रिकॉर्ड (${vaccinationRecords.size})", "Vaccination Records (${vaccinationRecords.size})"),
                    icon = Icons.Default.Vaccines,
                    modifier = Modifier.testTag("cattle_detail_vaccination_section")
                ) {
                    if (vaccinationRecords.isEmpty()) {
                        if (!cattle.vaccinationSummary.isNullOrBlank()) {
                            Text(
                                text = cattle.vaccinationSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ForestGreen
                            )
                        } else {
                            Text(
                                text = str(language, "इस गाय के लिए अभी कोई टीकाकरण रिकॉर्ड दर्ज नहीं है।", "No vaccination records logged for this cow yet."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        vaccinationRecords.forEachIndexed { idx, rec ->
                            if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(rec.title.ifBlank { str(language, "टीकाकरण", "Vaccination") }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = ForestGreen)
                                if (rec.medicine.isNotBlank()) {
                                    Text("${str(language, "टीका / दवा", "Vaccine / Med")}: ${rec.medicine}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (rec.vetName.isNotBlank()) {
                                    Text("${str(language, "चिकित्सक", "Vet")}: ${rec.vetName}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (rec.nextFollowUpDate.isNotBlank()) {
                                    Text("${str(language, "अगला टीकाकरण", "Next Due")}: ${rec.nextFollowUpDate}", style = MaterialTheme.typography.labelSmall, color = Saffron, fontWeight = FontWeight.SemiBold)
                                }
                                if (rec.notes.isNotBlank()) {
                                    Text(rec.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Illness & Treatment Records Card
            item {
                DetailSectionCard(
                    title = str(language, "बीमारी एवं उपचार रिकॉर्ड (${illnessTreatmentRecords.size})", "Illness & Treatment Records (${illnessTreatmentRecords.size})"),
                    icon = Icons.Default.MedicalServices,
                    modifier = Modifier.testTag("cattle_detail_treatment_section")
                ) {
                    if (illnessTreatmentRecords.isEmpty()) {
                        Text(
                            text = str(language, "इस गाय के लिए अभी कोई बीमारी या उपचार रिकॉर्ड दर्ज नहीं है।", "No illness or treatment records logged for this cow yet."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        illnessTreatmentRecords.forEachIndexed { idx, rec ->
                            if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(rec.title.ifBlank { rec.recordType }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = AlertRed)
                                    if (rec.costInr > 0) {
                                        Text("₹${rec.costInr.toInt()}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (rec.medicine.isNotBlank()) {
                                    Text("${str(language, "दवा / उपचार", "Medicine")}: ${rec.medicine}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (rec.vetName.isNotBlank()) {
                                    Text("${str(language, "चिकित्सक", "Doctor/Vet")}: ${rec.vetName}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (rec.nextFollowUpDate.isNotBlank()) {
                                    Text("${str(language, "फॉलो-अप तिथि", "Follow-up")}: ${rec.nextFollowUpDate}", style = MaterialTheme.typography.labelSmall, color = ForestGreen)
                                }
                                if (rec.notes.isNotBlank()) {
                                    Text(rec.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Feed & Daily Care Records Card
            item {
                DetailSectionCard(
                    title = str(language, "चारा एवं दैनिक देखभाल रिकॉर्ड (${recentFeedLogs.size})", "Feed & Daily Care Records (${recentFeedLogs.size})"),
                    icon = Icons.Default.Grass,
                    modifier = Modifier.testTag("cattle_detail_feed_section")
                ) {
                    if (recentFeedLogs.isEmpty()) {
                        Text(
                            text = str(
                                language,
                                if (isHindi) "वर्तमान लॉगिन के लिए कोई चारा लॉग उपलब्ध नहीं है (गौशाला शेड ${cattle.shedNumber} में नियमित हरा व सूखा चारा दिया जाता है)।" else "No feed logs available for current role.",
                                "No feed logs available for this view."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentFeedLogs.forEachIndexed { idx, log ->
                            if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.logDate.ifBlank { "-" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = ForestGreen)
                                    if (log.shedCleaned) {
                                        Text(str(language, "शेड स्वच्छ ✓", "Shed Clean ✓"), style = MaterialTheme.typography.labelSmall, color = GoshalaGreen, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text(
                                    text = "${str(language, "हरा चारा", "Green")}: ${log.greenFodderKg} kg • ${str(language, "भूसा/सूखा चारा", "Dry")}: ${log.dryFodderBhusaKg} kg • ${str(language, "दाना-खली", "Concentrate")}: ${log.concentrateKg} kg",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (log.waterLiters > 0 || log.assignedStaffName.isNotBlank()) {
                                    Text(
                                        text = "${str(language, "पानी", "Water")}: ${log.waterLiters} L${if (log.assignedStaffName.isNotBlank()) " • ${str(language, "सेवक", "Staff")}: ${log.assignedStaffName}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Permanent Cow QR Card
            item {
                DetailSectionCard(
                    title = str(language, "स्थायी गाय QR पहचान कार्ड", "Permanent Cow QR Identity"),
                    icon = Icons.Default.QrCode2
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bmp = qrBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR ${cattle.animalCode}",
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.5.dp, Golden, RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .padding(4.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = str(
                                    language,
                                    "यह QR कोड गाय की स्थायी Firebase Unique ID से जुड़ा है। जानकारी अपडेट करने पर भी QR नहीं बदलेगा।",
                                    "This QR code is permanently linked to the cow's Firebase Unique ID and stays unchanged on profile updates."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = qrPayload,
                                style = MaterialTheme.typography.labelSmall,
                                color = ForestGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreen
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            content()
        }
    }
}

@Composable
private fun CowDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(138.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
