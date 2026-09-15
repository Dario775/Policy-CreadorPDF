package com.speedscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.speedscan.core.utils.NativePdfGenerator
import com.speedscan.feature.camera.CameraScreen
import com.speedscan.feature.editor.PreviewScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject


import com.speedscan.core.domain.model.FilterType
import com.speedscan.core.domain.model.SignaturePlacement
import com.speedscan.core.security.SecurityManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.speedscan.core.security.LockType
import com.google.android.gms.ads.MobileAds
import com.speedscan.core.premium.PremiumManager
import com.speedscan.core.ads.AdManager
import com.speedscan.core.ads.ConsentManager

enum class AppScreen { HOME, HISTORY, CAMERA, CROP, PREVIEW, SIGNATURE, SECURITY, SETUP_PIN, SETUP_PATTERN, LOCK, PREMIUM }

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    
    @Inject
    lateinit var pdfGenerator: NativePdfGenerator
    
    @Inject
    lateinit var textExtractor: com.speedscan.core.utils.TextExtractor

    @Inject
    lateinit var pdfImporter: com.speedscan.core.utils.PdfImporter

    @Inject
    lateinit var securityManager: SecurityManager

    @Inject
    lateinit var premiumManager: PremiumManager

    @Inject
    lateinit var adManager: AdManager

    @Inject
    lateinit var consentManager: ConsentManager

    @Inject
    lateinit var billingManager: com.speedscan.core.billing.BillingManager

    // In-App Updates
    private lateinit var appUpdateManager: com.google.android.play.core.appupdate.AppUpdateManager
    private val UPDATE_REQUEST_CODE = 123
    private var pendingPdfUri by mutableStateOf<Uri?>(null)
    private var adsInitialized = false


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPdfUri = getPdfUriFromIntent(intent)
        
        consentManager.gatherConsent(this) { canRequestAds ->
            if (canRequestAds) initializeAds()
        }

        // Inicializar Google Play Billing
        billingManager.initialize()

        // In-App Updates Init
        appUpdateManager = com.google.android.play.core.appupdate.AppUpdateManagerFactory.create(this)
        appUpdateManager.registerListener { state ->
            if (state.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate()
            }
        }
        checkForUpdates()

        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        
        setContent {
            // Estado global de navegación y datos
            var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
            val capturedImages = remember { mutableStateListOf<String>() }
            val pageFilters = remember { mutableStateMapOf<String, FilterType>() }
            var imageToCrop by remember { mutableStateOf<String?>(null) }
            var initialCropBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

            // Cola de imágenes pendientes de recorte (para galería)
            val pendingGalleryImages = remember { mutableStateListOf<String>() }

            val pageSignatures = remember { mutableStateMapOf<String, SignaturePlacement>() }
            var pageToSign by remember { mutableStateOf<String?>(null) }

            // Estado de seguridad
            var isAppLocked by remember { mutableStateOf(false) }
            var currentLockType by remember { mutableStateOf(LockType.NONE) }
            var previousScreen by remember { mutableStateOf(AppScreen.HOME) }

            // Estado Premium
            val isPremium by premiumManager.isPremium.collectAsState(initial = false)
            val scanCount by premiumManager.scanCount.collectAsState(initial = 0)
            val canRequestAds by consentManager.canRequestAds.collectAsState()
            val privacyOptionsRequired by consentManager.privacyOptionsRequired.collectAsState()

            fun importPdfIntoEditor(uri: Uri) {
                lifecycleScope.launch {
                    try {
                        Toast.makeText(this@MainActivity, "Importando PDF...", Toast.LENGTH_SHORT).show()
                        val importedPages = pdfImporter.importPdf(uri)
                        if (importedPages.isNotEmpty()) {
                            capturedImages.clear()
                            pageFilters.clear()
                            pageSignatures.clear()
                            capturedImages.addAll(importedPages)
                            currentScreen = AppScreen.PREVIEW
                        } else {
                            Toast.makeText(this@MainActivity, "No se pudo leer el PDF", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error al importar PDF", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                }
            }

            LaunchedEffect(pendingPdfUri) {
                pendingPdfUri?.let { uri ->
                    pendingPdfUri = null
                    importPdfIntoEditor(uri)
                }
            }

            // Verificar tipo de bloqueo al iniciar
            LaunchedEffect(Unit) {
                currentLockType = securityManager.getLockType()
                if (currentLockType != LockType.NONE) {
                    isAppLocked = true
                    currentScreen = AppScreen.LOCK
                }
            }

            // Launcher de Galería
            val galleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetMultipleContents()
            ) { uris ->
                if (uris.isNotEmpty()) {
                    val copiedPaths = uris.mapNotNull { uri ->
                        copyUriToCache(this@MainActivity, uri)
                    }
                    
                    if (copiedPaths.isNotEmpty()) {
                        // Primera imagen va al recorte, el resto a la cola
                        pendingGalleryImages.clear()
                        pendingGalleryImages.addAll(copiedPaths.drop(1))
                        
                        imageToCrop = copiedPaths.first()
                        initialCropBounds = null // Sin detección automática para galería
                        currentScreen = AppScreen.CROP
                    }
                }
            }

            val pdfLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let { importPdfIntoEditor(it) }
            }

            // Lista de Historial de PDFs
            var historyFiles by remember { mutableStateOf(emptyList<File>()) }
            
            // Función para recargar historial
            fun refreshHistory() {
                val filesDir = this@MainActivity.filesDir
                historyFiles = filesDir.walkTopDown()
                    .filter { it.isFile && it.extension == "pdf" }
                    .toList()
            }

            // Recargar al iniciar y al volver a Historial
            // Recargar al iniciar y al volver a Historial
            LaunchedEffect(currentScreen) {
                if (currentScreen == AppScreen.HISTORY || currentScreen == AppScreen.HOME) {
                    refreshHistory()
                }
            }

            // Seguridad contra capturas en pantallas sensibles
            LaunchedEffect(currentScreen, isAppLocked) {
                val isSensitive = isAppLocked || 
                                  currentScreen == AppScreen.HISTORY || 
                                  currentScreen == AppScreen.PREVIEW ||
                                  currentScreen == AppScreen.SECURITY ||
                                  currentScreen == AppScreen.SETUP_PIN ||
                                  currentScreen == AppScreen.SETUP_PATTERN
                
                if (isSensitive) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "nav"
                    ) { screen ->
                        when (screen) {
                            AppScreen.HOME -> {
                                com.speedscan.feature.home.HomeScreen(
                                    isPremium = isPremium,
                                    onCameraClick = { 
                                        lifecycleScope.launch {
                                            if (premiumManager.canScan()) {
                                                currentScreen = AppScreen.CAMERA
                                            } else {
                                                currentScreen = AppScreen.PREMIUM
                                            }
                                        }
                                    },
                                    onGalleryClick = {
                                        lifecycleScope.launch {
                                            if (premiumManager.canScan()) {
                                                galleryLauncher.launch("image/*")
                                            } else {
                                                currentScreen = AppScreen.PREMIUM
                                            }
                                        }
                                    },
                                    onPdfClick = {
                                        lifecycleScope.launch {
                                            if (premiumManager.canScan()) {
                                                pdfLauncher.launch("application/pdf")
                                            } else {
                                                currentScreen = AppScreen.PREMIUM
                                            }
                                        }
                                    },
                                    onHistoryClick = {
                                        currentScreen = AppScreen.HISTORY
                                    },
                                    onSecurityClick = {
                                        previousScreen = currentScreen
                                        currentScreen = AppScreen.SECURITY
                                    },
                                    onPremiumClick = {
                                        currentScreen = AppScreen.PREMIUM
                                    }
                                )
                            }
                            AppScreen.PREMIUM -> {
                                val billingIsLoading by billingManager.isLoading.collectAsState()
                                
                                // Configurar callback para resultado de compra
                                LaunchedEffect(Unit) {
                                    billingManager.onPurchaseResult = { _, message ->
                                        Toast.makeText(
                                            this@MainActivity, 
                                            message, 
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                
                                com.speedscan.feature.premium.PremiumSettingsScreen(
                                    currentScanCount = scanCount,
                                    isPremium = isPremium,
                                    productPrice = premiumManager.getProductPrice(),
                                    isLoading = billingIsLoading,
                                    onBack = { currentScreen = AppScreen.HOME },
                                    onUpgrade = {
                                        billingManager.launchPurchaseFlow(this@MainActivity)
                                    },
                                    onRestorePurchase = {
                                        billingManager.restorePurchases()
                                    },
                                    privacyOptionsRequired = privacyOptionsRequired,
                                    onPrivacyOptionsClick = {
                                        consentManager.showPrivacyOptions(this@MainActivity) { error ->
                                            if (error != null) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "No se pudieron abrir las opciones de privacidad.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else if (consentManager.canRequestAds.value) {
                                                initializeAds()
                                            }
                                        }
                                    }
                                )
                            }
                            AppScreen.HISTORY -> {
                                com.speedscan.feature.history.HistoryScreen(
                                    historyFiles = historyFiles,
                                    onBackHome = { currentScreen = AppScreen.HOME },
                                    onOpenPdf = { file -> openPdf(file) },
                                    onSharePdf = { file -> sharePdf(file) },
                                    onDeletePdf = { file ->
                                        if (file.exists()) {
                                            if (file.delete()) {
                                                refreshHistory()
                                                Toast.makeText(this@MainActivity, "Documento eliminado", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "No se pudo eliminar el archivo", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                            AppScreen.CAMERA -> {
                                CameraScreen(
                                    currentImages = capturedImages,
                                    onPhotoCaptured = { path, bounds ->
                                        imageToCrop = path
                                        initialCropBounds = bounds
                                        currentScreen = AppScreen.CROP
                                    },
                                    onGalleryClick = { galleryLauncher.launch("image/*") },
                                    onReviewClicked = {
                                        if (capturedImages.isNotEmpty()) {
                                            currentScreen = AppScreen.PREVIEW
                                        }
                                    },
                                    onClose = { 
                                        // Si hay imágenes capturadas, ir a Preview para no perder el trabajo
                                        // Si no hay imágenes, volver al Home
                                        currentScreen = if (capturedImages.isNotEmpty()) AppScreen.PREVIEW else AppScreen.HOME
                                    }
                                )
                            }
                            AppScreen.CROP -> {
                                imageToCrop?.let { path ->
                                    com.speedscan.feature.crop.CropScreen(
                                        imagePath = path,
                                        initialBounds = initialCropBounds,
                                        onCropDone = { croppedPath ->
                                            capturedImages.add(croppedPath)
                                            imageToCrop = null
                                            initialCropBounds = null
                                            if (pendingGalleryImages.isNotEmpty()) {
                                                imageToCrop = pendingGalleryImages.removeAt(0)
                                            } else {
                                                // Always go to PREVIEW after one image for review, then user can add more from there.
                                                currentScreen = AppScreen.PREVIEW
                                            }
                                        },
                                        onCancel = {
                                            imageToCrop = null
                                            initialCropBounds = null
                                            pendingGalleryImages.clear()
                                            currentScreen = AppScreen.CAMERA
                                        }
                                    )
                                }
                            }
                            AppScreen.PREVIEW -> {
                                PreviewScreen(
                                    imagePaths = capturedImages,
                                    pageFilters = pageFilters,
                                    pageSignatures = pageSignatures,
                                    isPremium = isPremium,
                                    onBack = { 
                                        // If cancelling, go HOME (clear session) or back to Camera? 
                                        // User request: "cancelar" -> go back. But if "Done" -> Share -> Home.
                                        currentScreen = AppScreen.CAMERA 
                                    },
                                    onAddPageClick = {
                                        currentScreen = AppScreen.CAMERA
                                    },
                                    onGalleryClick = {
                                        galleryLauncher.launch("image/*")
                                    },
                                    onDeletePage = { index -> 
                                        if (index == -1) {
                                            capturedImages.clear()
                                            pageFilters.clear()
                                            pageSignatures.clear()
                                            currentScreen = AppScreen.HOME
                                        } else if (capturedImages.isNotEmpty()) {
                                            val path = capturedImages.removeAt(index)
                                            pageFilters.remove(path)
                                            pageSignatures.remove(path)
                                            if (capturedImages.isEmpty()) currentScreen = AppScreen.HOME
                                        }
                                    },
                                    onMovePage = { from, to ->
                                        if (from in capturedImages.indices && to in capturedImages.indices) {
                                            val item = capturedImages.removeAt(from)
                                            capturedImages.add(to, item)
                                        }
                                    },
                                    onExtractText = { path -> textExtractor.extractText(path) },
                                    onFilterChange = { path, filter -> pageFilters[path] = filter },
                                    onAddSignature = { path ->
                                        pageToSign = path
                                        currentScreen = AppScreen.SIGNATURE
                                    },
                                    onUpdateSignature = { path, signature ->
                                        pageSignatures[path] = signature
                                    },
                                    onRemoveSignature = { path ->
                                        pageSignatures.remove(path)
                                    },
                                    onGeneratePdf = { name, category, pageSize, quality -> 
                                        generateAndSharePdf(capturedImages, pageFilters, pageSignatures, name, category, pageSize, quality) {
                                            capturedImages.clear()
                                            pageFilters.clear()
                                            pageSignatures.clear()
                                            currentScreen = AppScreen.HOME
                                        }
                                    },
                                    onUpgradeClick = { currentScreen = AppScreen.PREMIUM }
                                )
                            }
                            AppScreen.SIGNATURE -> {
                                com.speedscan.feature.signature.SignatureScreen(
                                    onSignatureSaved = { signPath ->
                                        pageToSign?.let { page -> pageSignatures[page] = SignaturePlacement(signPath) }
                                        pageToSign = null
                                        currentScreen = AppScreen.PREVIEW
                                    },
                                    onCancel = {
                                        pageToSign = null
                                        currentScreen = AppScreen.PREVIEW
                                    }
                                )
                            }
                            AppScreen.SECURITY -> {
                                com.speedscan.feature.security.SecuritySettingsScreen(
                                    currentLockType = currentLockType,
                                    isBiometricAvailable = isBiometricAvailable(),
                                    onBack = { currentScreen = previousScreen }, // Return to where we came from
                                    onSelectLockType = { },
                                    onSetupPin = { currentScreen = AppScreen.SETUP_PIN },
                                    onSetupPattern = { currentScreen = AppScreen.SETUP_PATTERN },
                                    onSetupBiometric = {
                                        lifecycleScope.launch {
                                            securityManager.setBiometricOnly()
                                            currentLockType = LockType.BIOMETRIC
                                        }
                                    },
                                    onRemoveLock = {
                                        lifecycleScope.launch {
                                            securityManager.removeLock()
                                            currentLockType = LockType.NONE
                                        }
                                    }
                                )
                            }
                            AppScreen.SETUP_PIN -> {
                                com.speedscan.feature.security.SetupPinScreen(
                                    onPinSet = { pin ->
                                        lifecycleScope.launch {
                                            securityManager.setPin(pin)
                                            currentLockType = LockType.PIN
                                            currentScreen = AppScreen.SECURITY
                                        }
                                    },
                                    onCancel = { currentScreen = AppScreen.SECURITY }
                                )
                            }
                            AppScreen.SETUP_PATTERN -> {
                                com.speedscan.feature.security.SetupPatternScreen(
                                    onPatternSet = { pattern ->
                                        lifecycleScope.launch {
                                            securityManager.setPattern(pattern)
                                            currentLockType = LockType.PATTERN
                                            currentScreen = AppScreen.SECURITY
                                        }
                                    },
                                    onCancel = { currentScreen = AppScreen.SECURITY }
                                )
                            }
                            AppScreen.LOCK -> {
                                val showBio = (currentLockType == LockType.BIOMETRIC) && isBiometricAvailable()
                                LaunchedEffect(currentLockType) {
                                    if (showBio) {
                                        showBiometricPrompt(
                                            onSuccess = {
                                                isAppLocked = false
                                                currentScreen = AppScreen.HOME
                                            },
                                            onError = { }
                                        )
                                    }
                                }
                                when (currentLockType) {
                                    LockType.BIOMETRIC -> {
                                        // Pantalla limpia solo para huella
                                        com.speedscan.feature.security.BiometricLockScreen(
                                            onBiometricClick = {
                                                if (isBiometricAvailable()) {
                                                    showBiometricPrompt(
                                                        onSuccess = {
                                                            isAppLocked = false
                                                            currentScreen = AppScreen.HOME
                                                        },
                                                        onError = { }
                                                    )
                                                }
                                            },
                                            onUsePinClick = {
                                                // Permitir usar PIN como alternativa
                                                currentLockType = LockType.PIN
                                            },
                                            showPinOption = true // Siempre permitir PIN como fallback
                                        )
                                    }
                                    LockType.PIN -> {
                                        com.speedscan.feature.security.LockScreen(
                                            onUnlock = {
                                                isAppLocked = false
                                                currentScreen = AppScreen.HOME
                                            },
                                            onVerifyPin = { pin -> securityManager.verifyPin(pin) },
                                            onBiometricClick = {
                                                if (isBiometricAvailable()) {
                                                    showBiometricPrompt(
                                                        onSuccess = {
                                                            isAppLocked = false
                                                            currentScreen = AppScreen.HOME
                                                        },
                                                        onError = { }
                                                    )
                                                }
                                            },
                                            showBiometric = isBiometricAvailable()
                                        )
                                    }
                                    LockType.PATTERN -> {
                                        com.speedscan.feature.security.PatternLockScreen(
                                            onUnlock = {
                                                isAppLocked = false
                                                currentScreen = AppScreen.HOME
                                            },
                                            onVerifyPattern = { pattern -> securityManager.verifyPattern(pattern) },
                                            onBiometricClick = {
                                                if (isBiometricAvailable()) {
                                                    showBiometricPrompt(
                                                        onSuccess = {
                                                            isAppLocked = false
                                                            currentScreen = AppScreen.HOME
                                                        },
                                                        onError = { }
                                                    )
                                                }
                                            },
                                            showBiometric = isBiometricAvailable()
                                        )
                                    }
                                    LockType.NONE -> {
                                        LaunchedEffect(Unit) {
                                            isAppLocked = false
                                            currentScreen = AppScreen.HOME
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!isPremium && canRequestAds) {
                    com.speedscan.core.ads.BannerAd()
                }
            }
        }
    }

    private fun initializeAds() {
        if (adsInitialized) return
        adsInitialized = true
        MobileAds.initialize(this) {
            adManager.loadInterstitial(this)
        }
    }

    private fun copyUriToCache(context: android.content.Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
            val outputStream = java.io.FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateAndSharePdf(
        imagePaths: List<String>, 
        filters: Map<String, FilterType>,
        signatures: Map<String, SignaturePlacement>,
        fileName: String = "Scan_${System.currentTimeMillis()}",
        category: String = "General",
        pageSize: com.speedscan.core.utils.PdfPageSize = com.speedscan.core.utils.PdfPageSize.A4,
        quality: Int = 80,
        onFinish: () -> Unit
    ) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Generando PDF...", Toast.LENGTH_SHORT).show()
                val pdfFile = pdfGenerator.generatePdf(
                    imagePaths = imagePaths, 
                    outputName = fileName, 
                    category = category, 
                    filters = filters,
                    signatures = signatures,
                    pageSize = pageSize,
                    quality = quality
                )
                
                // Incrementar contador y mostrar anuncio si no es premium
                if (!premiumManager.checkIsPremium()) {
                    premiumManager.incrementScanCount()
                    adManager.showInterstitial(this@MainActivity) {
                        sharePdf(pdfFile)
                        onFinish()
                    }
                } else {
                    sharePdf(pdfFile)
                    onFinish()
                }

                // Estado limpiado via callback
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error generando PDF", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun sharePdf(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider", 
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Compartir Documento PDF"))
    }

    private fun openPdf(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Abrir con"))
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró una app para abrir PDFs", Toast.LENGTH_SHORT).show()
        }
    }

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    fun showBiometricPrompt(onSuccess: () -> Unit, onError: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(this@MainActivity, "Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                    onError()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Huella no reconocida", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Creador PDF")
            .setSubtitle("Usa tu huella digital para acceder")
            .setNegativeButtonText("Usar PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == com.google.android.play.core.install.model.InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPdfUri = getPdfUriFromIntent(intent)
    }

    private fun getPdfUriFromIntent(intent: Intent?): Uri? {
        if (intent == null) return null

        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
    }

    private fun checkForUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == com.google.android.play.core.install.model.UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE,
                        this,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun popupSnackbarForCompleteUpdate() {
        // Usamos la vista raíz de la actividad para mostrar el Snackbar
        val rootView = window.decorView.findViewById<android.view.View>(android.R.id.content)
        val snackbar = com.google.android.material.snackbar.Snackbar.make(
            rootView,
            "Actualización lista para instalar",
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
        )
        snackbar.setAction("REINICIAR") {
            appUpdateManager.completeUpdate()
        }
        snackbar.setActionTextColor(android.graphics.Color.YELLOW)
        snackbar.show()
    }
}
