package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.*
import com.example.data.model.AppModule
import com.example.data.model.Cattle
import com.example.data.model.LoginPortal
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.repository.AuthSessionRepository
import com.example.data.repository.GosalaRepository
import com.example.data.repository.SessionResolution
import com.example.ui.auth.AuthScreen
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GosalaOfficialLogo
import com.example.ui.components.GosalaTopBar
import com.example.ui.navigation.AuthNavigationController
import com.example.ui.navigation.AuthNavigationState
import com.example.ui.navigation.GosalaRootNavHost
import com.example.ui.navigation.rememberAuthNavigationController
import com.example.ui.screens.*
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.AiViewModel
import com.example.ui.viewmodel.DataUiState
import com.example.ui.viewmodel.GosalaViewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FirebaseBootstrap(
    val db: FirebaseFirestore,
    val authRepo: AuthSessionRepository
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val scope = rememberCoroutineScope()
            val prefs = remember(appContext) { PreferencesManager(appContext) }
            val language by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.HINDI)
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.LIGHT)

            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            var bootstrap by remember { mutableStateOf<FirebaseBootstrap?>(null) }

            LaunchedEffect(Unit) {
                if (bootstrap == null) {
                    val initialized = withContext(Dispatchers.IO) {
                        configureAppCheck(appContext, intent)
                        val loader = appContext.imageLoader
                        loader.enqueue(
                            ImageRequest.Builder(appContext)
                                .data(R.drawable.img_hero_goshala)
                                .allowHardware(false)
                                .build()
                        )
                        loader.enqueue(
                            ImageRequest.Builder(appContext)
                                .data(R.drawable.img_official_goshala_logo)
                                .allowHardware(false)
                                .build()
                        )
                        val db = FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
                        val authRepo = AuthSessionRepository(db, Firebase.auth)
                        FirebaseBootstrap(db = db, authRepo = authRepo)
                    }
                    bootstrap = initialized
                }
            }

            var selectedPortal by remember { mutableStateOf(LoginPortal.PUBLIC) }
            // Non-null only when the user explicitly initiated a sign-in from a specific portal button
            var activeSignInPortal by remember { mutableStateOf<LoginPortal?>(null) }
            var portalRejectionMsg by remember { mutableStateOf<String?>(null) }
            var rejectedAccountNote by remember { mutableStateOf<String?>(null) }
            var skipAutoSignIn by remember { mutableStateOf(false) }

            CompositionLocalProvider(LocalAppLanguage provides language) {
                GosalaTheme(darkTheme = isDark) {
                    val credentialManager = remember(context) { CredentialManager.create(context) }
                    val authNavController = rememberAuthNavigationController()
                    val activeBootstrap = bootstrap

                    // Observe FirebaseAuth state once Firebase is bootstrapped (use currentUser immediately so persisted sessions never flash Login)
                    val initialFirebaseUser = remember(activeBootstrap) { activeBootstrap?.authRepo?.currentUser }
                    val firebaseUser by remember(activeBootstrap) {
                        activeBootstrap?.authRepo?.observeAuthState()
                            ?: kotlinx.coroutines.flow.flowOf(null)
                    }.collectAsStateWithLifecycle(initialValue = initialFirebaseUser)

                    var sessionState by remember { mutableStateOf<UserSession?>(null) }
                    var isResolving by remember { mutableStateOf(false) }
                    var sessionError by remember { mutableStateOf<String?>(null) }
                    var retryKey by remember { mutableIntStateOf(0) }

                    LaunchedEffect(activeBootstrap, firebaseUser?.uid, retryKey) {
                        val user = firebaseUser
                        val authRepo = activeBootstrap?.authRepo
                        if (authRepo == null || user == null) {
                            sessionState = null
                            sessionError = null
                            isResolving = false
                            return@LaunchedEffect
                        }
                        isResolving = true
                        sessionError = null
                        authRepo.resolveVerifiedSession(user, activeSignInPortal).fold(
                            onSuccess = { resolution ->
                                when (resolution) {
                                    is SessionResolution.Authorized -> {
                                        sessionState = resolution.session
                                        activeSignInPortal = null
                                        portalRejectionMsg = null
                                        rejectedAccountNote = null
                                        isResolving = false
                                    }
                                    is SessionResolution.UnauthorizedPortal -> {
                                        portalRejectionMsg = str(
                                            language,
                                            resolution.messageHi,
                                            resolution.messageEn
                                        )
                                        rejectedAccountNote = "Email: ${resolution.email.ifBlank { "-" }} | UID: ${resolution.uid}"
                                        activeSignInPortal = null
                                        skipAutoSignIn = true
                                        sessionState = null
                                        isResolving = false
                                        authRepo.signOut(credentialManager)
                                    }
                                }
                            },
                            onFailure = { err ->
                                sessionState = null
                                sessionError = friendlyHindiError(err, language)
                                isResolving = false
                            }
                        )
                    }

                    val verifiedSession = sessionState?.takeIf {
                        firebaseUser != null && it.uid == firebaseUser?.uid
                    }

                    val authNavState: AuthNavigationState = when {
                        activeBootstrap == null -> AuthNavigationState.Loading
                        firebaseUser == null -> AuthNavigationState.Unauthenticated(
                            errorMessage = portalRejectionMsg,
                            rejectedAccountNote = rejectedAccountNote
                        )
                        isResolving || (verifiedSession == null && sessionError == null) -> AuthNavigationState.Loading
                        sessionError != null -> AuthNavigationState.SessionError(sessionError!!)
                        verifiedSession != null -> AuthNavigationState.Authorized(verifiedSession)
                        else -> AuthNavigationState.Unauthenticated()
                    }

                    val handleSignOut: () -> Unit = {
                        skipAutoSignIn = true
                        activeSignInPortal = null
                        portalRejectionMsg = null
                        rejectedAccountNote = null
                        sessionState = null
                        sessionError = null
                        scope.launch {
                            activeBootstrap?.authRepo?.signOut(credentialManager)
                            authNavController.redirectToLogin()
                        }
                    }

                    GosalaRootNavHost(
                        authState = authNavState,
                        authNavController = authNavController,
                        loadingContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .testTag(if (activeBootstrap == null) "splash_screen" else "session_loading_screen"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    GosalaOfficialLogo(
                                        size = if (activeBootstrap == null) 116.dp else 104.dp,
                                        innerPadding = 4.dp,
                                        borderWidth = 2.5.dp,
                                        testTag = if (activeBootstrap == null) "splash_official_logo" else "session_loading_official_logo"
                                    )
                                    Text(
                                        text = if (activeBootstrap == null) {
                                            str(language, GosalaBrand.NAME_HI, GosalaBrand.NAME_EN)
                                        } else {
                                            str(language, "जय भोले गौशाला समिति\nसत्यापित सत्र लोड हो रहा है...", "Verifying role & loading session...")
                                        },
                                        style = if (activeBootstrap == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                    CircularProgressIndicator(color = ForestGreen)
                                }
                            }
                        },
                        loginContent = { errorMsg, accountNote ->
                            AuthScreen(
                                language = language,
                                onToggleLanguage = {
                                    val next = if (language == AppLanguage.HINDI) AppLanguage.ENGLISH else AppLanguage.HINDI
                                    scope.launch { prefs.setLanguage(next) }
                                },
                                selectedPortal = selectedPortal,
                                onSelectPortal = { portal ->
                                    selectedPortal = portal
                                    portalRejectionMsg = null
                                    rejectedAccountNote = null
                                },
                                sessionError = errorMsg,
                                rejectedAccountNote = accountNote,
                                skipAutoSignIn = skipAutoSignIn,
                                isResolvingSession = false,
                                onAuthAttemptStart = { portal ->
                                    activeSignInPortal = portal
                                    if (portal != null) {
                                        portalRejectionMsg = null
                                        rejectedAccountNote = null
                                    }
                                },
                                emailAuthGateway = activeBootstrap?.authRepo,
                                onAuthSuccess = {
                                    portalRejectionMsg = null
                                    rejectedAccountNote = null
                                }
                            )
                        },
                        sessionErrorContent = { errMessage ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                                        Text(
                                            text = errMessage,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = { retryKey++ },
                                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                                            ) {
                                                Text(str(language, "पुनः प्रयास करें", "Retry"))
                                            }
                                            OutlinedButton(onClick = handleSignOut) {
                                                Text(str(language, "साइन आउट", "Sign Out"))
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        adminDashboardContent = { session ->
                            val db = activeBootstrap?.db ?: return@GosalaRootNavHost
                            key(session.uid, session.role) {
                                GosalaAuthenticatedShell(
                                    db = db,
                                    session = session,
                                    language = language,
                                    themeMode = themeMode,
                                    isDark = isDark,
                                    onSetLanguage = { lang -> scope.launch { prefs.setLanguage(lang) } },
                                    onSetThemeMode = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                                    onSignOut = handleSignOut
                                )
                            }
                        },
                        staffDashboardContent = { session ->
                            val db = activeBootstrap?.db ?: return@GosalaRootNavHost
                            key(session.uid, session.role) {
                                GosalaAuthenticatedShell(
                                    db = db,
                                    session = session,
                                    language = language,
                                    themeMode = themeMode,
                                    isDark = isDark,
                                    onSetLanguage = { lang -> scope.launch { prefs.setLanguage(lang) } },
                                    onSetThemeMode = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                                    onSignOut = handleSignOut
                                )
                            }
                        },
                        publicDashboardContent = { session ->
                            val db = activeBootstrap?.db ?: return@GosalaRootNavHost
                            key(session.uid, session.role) {
                                GosalaAuthenticatedShell(
                                    db = db,
                                    session = session,
                                    language = language,
                                    themeMode = themeMode,
                                    isDark = isDark,
                                    onSetLanguage = { lang -> scope.launch { prefs.setLanguage(lang) } },
                                    onSetThemeMode = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                                    onSignOut = handleSignOut
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private data class NavTabItem(
    val route: String,
    val labelHi: String,
    val labelEn: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
private fun GosalaAuthenticatedShell(
    db: FirebaseFirestore,
    session: UserSession,
    language: AppLanguage,
    themeMode: AppThemeMode,
    isDark: Boolean,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSignOut: () -> Unit
) {
    val gosalaViewModel: GosalaViewModel = viewModel(
        key = "${session.uid}_${session.role.name}",
        factory = viewModelFactory {
            initializer { GosalaViewModel(GosalaRepository(db), session) }
        }
    )
    val aiViewModel: AiViewModel = viewModel(
        key = "ai_${session.uid}_${session.role.name}",
        factory = viewModelFactory {
            initializer { AiViewModel() }
        }
    )

    val summary by gosalaViewModel.publicSummary.collectAsStateWithLifecycle()
    val cattleState by gosalaViewModel.cattleState.collectAsStateWithLifecycle()
    val healthState by gosalaViewModel.healthState.collectAsStateWithLifecycle()
    val feedLogsState by gosalaViewModel.feedLogsState.collectAsStateWithLifecycle()
    val staffAccountsState by gosalaViewModel.staffAccountsState.collectAsStateWithLifecycle()
    val staffInvitesState by gosalaViewModel.staffInvitesState.collectAsStateWithLifecycle()
    val staffOpsState by gosalaViewModel.staffOpsState.collectAsStateWithLifecycle()
    val financeState by gosalaViewModel.financeState.collectAsStateWithLifecycle()
    val farmersState by gosalaViewModel.farmersState.collectAsStateWithLifecycle()
    val farmerTxState by gosalaViewModel.farmerTxState.collectAsStateWithLifecycle()
    val compostState by gosalaViewModel.compostState.collectAsStateWithLifecycle()
    val donationsState by gosalaViewModel.donationsState.collectAsStateWithLifecycle()
    val sponsorshipsState by gosalaViewModel.sponsorshipsState.collectAsStateWithLifecycle()
    val notificationsState by gosalaViewModel.notificationsState.collectAsStateWithLifecycle()
    val auditLogsState by gosalaViewModel.auditLogsState.collectAsStateWithLifecycle()

    val cattleList = (cattleState as? DataUiState.Success)?.data.orEmpty()
    val healthList = (healthState as? DataUiState.Success)?.data.orEmpty()
    val feedLogsList = (feedLogsState as? DataUiState.Success)?.data.orEmpty()
    val staffAccountsList = (staffAccountsState as? DataUiState.Success)?.data.orEmpty()
    val staffOpsList = (staffOpsState as? DataUiState.Success)?.data.orEmpty()
    val financeList = (financeState as? DataUiState.Success)?.data.orEmpty()
    val farmersList = (farmersState as? DataUiState.Success)?.data.orEmpty()
    val farmerTxList = (farmerTxState as? DataUiState.Success)?.data.orEmpty()
    val compostList = (compostState as? DataUiState.Success)?.data.orEmpty()
    val donationsList = (donationsState as? DataUiState.Success)?.data.orEmpty()
    val sponsorshipsList = (sponsorshipsState as? DataUiState.Success)?.data.orEmpty()
    val notificationsList = (notificationsState as? DataUiState.Success)?.data.orEmpty()
    val unreadNotifCount = remember(notificationsList, session.uid) {
        notificationsList.count { !it.readByUids.contains(session.uid) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(gosalaViewModel) {
        gosalaViewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val homeRouteForRole = remember(session.role) {
        AuthNavigationController.dashboardRouteForRole(session.role)
    }

    // Separate Navigation Configurations strictly by verified Role
    val primaryTabs = remember(session) {
        when (session.role) {
            UserRole.ADMIN -> listOf(
                NavTabItem(Routes.ADMIN_DASHBOARD, "प्रशासक", "Admin", Icons.Default.AdminPanelSettings, "nav_admin_home"),
                NavTabItem(Routes.CATTLE, "गौवंश", "Cattle", Icons.Default.Pets, "nav_cattle"),
                NavTabItem(Routes.FINANCE, "वित्त", "Finance", Icons.Default.AccountBalanceWallet, "nav_finance"),
                NavTabItem(Routes.STAFF, "स्टाफ", "Staff", Icons.Default.Groups, "nav_staff"),
                NavTabItem(Routes.PROFILE, "प्रोफाइल", "Profile", Icons.Default.Person, "nav_profile")
            )
            UserRole.STAFF -> buildList {
                add(NavTabItem(Routes.STAFF_DASHBOARD, "कार्य", "Staff", Icons.Default.Badge, "nav_staff_home"))
                if (session.canView(AppModule.CATTLE)) {
                    add(NavTabItem(Routes.CATTLE, "गौवंश", "Cattle", Icons.Default.Pets, "nav_cattle"))
                }
                add(NavTabItem(Routes.AI_ASSISTANT, "AI सहायक", "AI", Icons.Default.AutoAwesome, "nav_ai"))
                add(NavTabItem(Routes.PROFILE, "प्रोफाइल", "Profile", Icons.Default.Person, "nav_profile"))
            }
            UserRole.PUBLIC -> listOf(
                NavTabItem(Routes.PUBLIC_DASHBOARD, "होम", "Home", Icons.Default.Home, "nav_home"),
                NavTabItem(Routes.CATTLE, "गौवंश", "Cattle", Icons.Default.Pets, "nav_cattle"),
                NavTabItem(Routes.AI_ASSISTANT, "AI सहायक", "AI", Icons.Default.AutoAwesome, "nav_ai"),
                NavTabItem(Routes.DONATIONS, "दान-गोद", "Donate", Icons.Default.VolunteerActivism, "nav_donations"),
                NavTabItem(Routes.PROFILE, "प्रोफाइल", "Profile", Icons.Default.Person, "nav_profile")
            )
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: homeRouteForRole

    var openAddCattleOnStart by remember { mutableStateOf(false) }
    var openAddFeedOnStart by remember { mutableStateOf(false) }
    var openAddFinanceOnStart by remember { mutableStateOf(false) }
    var preselectedCattleForSponsor by remember { mutableStateOf<Cattle?>(null) }
    var initialAiMode by remember { mutableStateOf(AiMode.MANAGEMENT) }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun routeTitle(route: String): String? = when (route) {
        Routes.PUBLIC_DASHBOARD -> null
        Routes.ADMIN_DASHBOARD -> str(language, "प्रशासक नियंत्रण कक्ष", "Admin Control Center")
        Routes.STAFF_DASHBOARD -> str(language, "कर्मचारी डैशबोर्ड", "Staff Dashboard")
        Routes.CATTLE -> str(language, "गाय-बैल प्रबंधन", "Cattle Management")
        Routes.HEALTH -> str(language, "स्वास्थ्य व उपचार", "Health & Vet Care")
        Routes.FEED -> str(language, "चारा व दैनिक कार्य", "Feed & Daily Operations")
        Routes.STAFF -> str(language, "कर्मचारी प्रबंधन", "Staff Management")
        Routes.FINANCE -> str(language, "वित्त (आय-व्यय)", "Financial Ledger")
        Routes.FARMERS -> str(language, "किसान–गौशाला सहयोग", "Farmer Cooperation")
        Routes.PRODUCTION -> str(language, "गोबर उत्पाद व खाद", "Compost & Products")
        Routes.DONATIONS -> str(language, "दान एवं गौ-गोद", "Donations & Sponsorship")
        Routes.AI_ASSISTANT -> str(language, "AI गौशाला सहायक", "AI Goshala Assistant")
        Routes.REPORTS -> str(language, "रिपोर्ट एवं निर्यात", "Reports & Exports")
        Routes.NOTIFICATIONS -> str(language, "सूचनाएं एवं अलर्ट", "Notifications & Alerts")
        Routes.PROFILE -> str(language, "प्रोफाइल एवं सेटिंग्स", "Profile & Settings")
        else -> null
    }

    val isTopTab = primaryTabs.any { it.route == currentRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    Spacer(Modifier.height(12.dp))
                    primaryTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = str(language, tab.labelHi, tab.labelEn)) },
                            label = { Text(str(language, tab.labelHi, tab.labelEn), maxLines = 1) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    GosalaTopBar(
                        title = routeTitle(currentRoute),
                        showBack = !isTopTab,
                        onBack = { navController.popBackStack() },
                        language = language,
                        isDark = isDark,
                        unreadNotifications = unreadNotifCount,
                        onToggleLanguage = {
                            onSetLanguage(if (language == AppLanguage.HINDI) AppLanguage.ENGLISH else AppLanguage.HINDI)
                        },
                        onToggleTheme = {
                            onSetThemeMode(if (isDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
                        },
                        onNotificationsClick = {
                            if (currentRoute != Routes.NOTIFICATIONS) {
                                navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            primaryTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = {
                                        Icon(
                                            tab.icon,
                                            contentDescription = str(language, tab.labelHi, tab.labelEn)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = str(language, tab.labelHi, tab.labelEn),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = homeRouteForRole,
                    enterTransition = { fadeIn(animationSpec = tween(150)) },
                    exitTransition = { fadeOut(animationSpec = tween(150)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                    popExitTransition = { fadeOut(animationSpec = tween(150)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // 1. DEDICATED PUBLIC USER DASHBOARD (Preserved Existing UI)
                    composable(Routes.PUBLIC_DASHBOARD) {
                        ProtectedRoute(
                            allowed = session.role == UserRole.PUBLIC,
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            HomeScreen(
                                language = language,
                                session = session,
                                summary = summary,
                                cattle = cattleList,
                                feedLogs = feedLogsList,
                                finance = financeList,
                                donations = donationsList,
                                onNavigate = { route ->
                                    if (primaryTabs.any { it.route == route }) {
                                        navigateTopTab(route)
                                    } else {
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                },
                                onQuickAddCattle = {
                                    openAddCattleOnStart = true
                                    navigateTopTab(Routes.CATTLE)
                                },
                                onQuickAddFeed = {
                                    openAddFeedOnStart = true
                                    navController.navigate(Routes.FEED) { launchSingleTop = true }
                                },
                                onQuickAddExpense = {
                                    openAddFinanceOnStart = true
                                    navController.navigate(Routes.FINANCE) { launchSingleTop = true }
                                }
                            )
                        }
                    }

                    // 2. DEDICATED ADMIN DASHBOARD (Strictly Admin Only)
                    composable(Routes.ADMIN_DASHBOARD) {
                        ProtectedRoute(
                            allowed = session.role == UserRole.ADMIN,
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            AdminDashboardScreen(
                                language = language,
                                session = session,
                                cattle = cattleList,
                                health = healthList,
                                feedLogs = feedLogsList,
                                staffAccounts = staffAccountsList,
                                finance = financeList,
                                donations = donationsList,
                                sponsorships = sponsorshipsList,
                                compost = compostList,
                                onNavigate = { route ->
                                    if (primaryTabs.any { it.route == route }) {
                                        navigateTopTab(route)
                                    } else {
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                },
                                onQuickAddCattle = {
                                    openAddCattleOnStart = true
                                    navigateTopTab(Routes.CATTLE)
                                },
                                onQuickAddFeed = {
                                    openAddFeedOnStart = true
                                    navController.navigate(Routes.FEED) { launchSingleTop = true }
                                },
                                onQuickAddExpense = {
                                    openAddFinanceOnStart = true
                                    navigateTopTab(Routes.FINANCE)
                                }
                            )
                        }
                    }

                    // 3. DEDICATED STAFF DASHBOARD (Strictly Staff Only)
                    composable(Routes.STAFF_DASHBOARD) {
                        ProtectedRoute(
                            allowed = session.role == UserRole.STAFF,
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            StaffDashboardScreen(
                                language = language,
                                session = session,
                                cattle = cattleList,
                                feedLogs = feedLogsList,
                                onNavigate = { route ->
                                    if (primaryTabs.any { it.route == route }) {
                                        navigateTopTab(route)
                                    } else {
                                        navController.navigate(route) { launchSingleTop = true }
                                    }
                                },
                                onQuickAddCattle = {
                                    openAddCattleOnStart = true
                                    navigateTopTab(Routes.CATTLE)
                                },
                                onQuickAddFeed = {
                                    openAddFeedOnStart = true
                                    navController.navigate(Routes.FEED) { launchSingleTop = true }
                                },
                                onQuickAddExpense = {
                                    openAddFinanceOnStart = true
                                    navController.navigate(Routes.FINANCE) { launchSingleTop = true }
                                }
                            )
                        }
                    }

                    composable(Routes.CATTLE) {
                        ProtectedRoute(
                            allowed = session.canView(AppModule.CATTLE),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            CattleManagementScreen(
                                language = language,
                                session = session,
                                cattleState = cattleState,
                                openAddOnStart = openAddCattleOnStart,
                                onConsumedOpenAdd = { openAddCattleOnStart = false },
                                onSaveCattle = { item, isEdit -> gosalaViewModel.saveCattle(item, isEdit, language) },
                                onDeleteCattle = { item -> gosalaViewModel.deleteCattle(item, language) },
                                onSponsorCattle = { item ->
                                    preselectedCattleForSponsor = item
                                    navigateTopTab(Routes.DONATIONS)
                                }
                            )
                        }
                    }

                    composable(Routes.HEALTH) {
                        ProtectedRoute(
                            allowed = session.role != UserRole.PUBLIC && session.canView(AppModule.HEALTH),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            HealthManagementScreen(
                                language = language,
                                session = session,
                                healthState = healthState,
                                cattleList = cattleList,
                                onAddHealthRecord = { rec -> gosalaViewModel.addHealthRecord(rec, language) },
                                onDeleteHealthRecord = { id -> gosalaViewModel.deleteHealthRecord(id, language) }
                            )
                        }
                    }

                    composable(Routes.FEED) {
                        ProtectedRoute(
                            allowed = session.role != UserRole.PUBLIC && session.canView(AppModule.FEED),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            FeedAndOpsScreen(
                                language = language,
                                session = session,
                                feedLogsState = feedLogsState,
                                openAddOnStart = openAddFeedOnStart,
                                onConsumedOpenAdd = { openAddFeedOnStart = false },
                                onAddFeedLog = { log -> gosalaViewModel.addFeedLog(log, language) },
                                onToggleTaskCompleted = { id, done -> gosalaViewModel.toggleFeedTask(id, done, language) }
                            )
                        }
                    }

                    composable(Routes.STAFF) {
                        ProtectedRoute(
                            allowed = session.role == UserRole.ADMIN || (session.role == UserRole.STAFF && session.canView(AppModule.STAFF)),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            StaffManagementScreen(
                                language = language,
                                session = session,
                                staffAccountsState = staffAccountsState,
                                staffInvitesState = staffInvitesState,
                                staffOpsState = staffOpsState,
                                onCreateInvite = { email, name, phone, desig, sal, perms ->
                                    gosalaViewModel.createStaffInvite(email, name, phone, desig, sal, perms, language)
                                },
                                onUpdateStaff = { uid, active, sal, perms ->
                                    gosalaViewModel.updateStaffAccount(uid, active, sal, perms, language)
                                },
                                onAddStaffOperation = { op -> gosalaViewModel.addStaffOperation(op, language) },
                                onDeleteStaff = { uid -> gosalaViewModel.deleteStaffAccount(uid, language) },
                                onDeleteInvite = { email -> gosalaViewModel.deleteStaffInvite(email, language) }
                            )
                        }
                    }

                    composable(Routes.FINANCE) {
                        ProtectedRoute(
                            allowed = session.role != UserRole.PUBLIC && session.canView(AppModule.FINANCE),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            FinanceManagementScreen(
                                language = language,
                                session = session,
                                financeState = financeState,
                                openAddOnStart = openAddFinanceOnStart,
                                onConsumedOpenAdd = { openAddFinanceOnStart = false },
                                onAddTransaction = { tx -> gosalaViewModel.addFinancialTx(tx, language) },
                                onDeleteTransaction = { id -> gosalaViewModel.deleteFinancialTx(id, language) },
                                onUpdateTransaction = { tx -> gosalaViewModel.updateFinancialTx(tx, language) }
                            )
                        }
                    }

                    composable(Routes.FARMERS) {
                        ProtectedRoute(
                            allowed = session.role != UserRole.PUBLIC && session.canView(AppModule.FARMERS),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            FarmerCooperationScreen(
                                language = language,
                                session = session,
                                farmersState = farmersState,
                                farmerTxState = farmerTxState,
                                onAddFarmer = { farmer -> gosalaViewModel.addFarmer(farmer, language) },
                                onAddFarmerTx = { tx, farmer -> gosalaViewModel.addFarmerTx(tx, farmer, language) },
                                onUpdateFarmer = { farmer -> gosalaViewModel.updateFarmer(farmer, language) },
                                onDeleteFarmer = { farmer -> gosalaViewModel.deleteFarmer(farmer, language) }
                            )
                        }
                    }

                    composable(Routes.PRODUCTION) {
                        ProtectedRoute(
                            allowed = session.role != UserRole.PUBLIC && session.canView(AppModule.PRODUCTION),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            CompostProductionScreen(
                                language = language,
                                session = session,
                                compostState = compostState,
                                onAddBatch = { batch -> gosalaViewModel.addCompostBatch(batch, language) },
                                onRecordSale = { batch, qty -> gosalaViewModel.recordCompostSale(batch, qty, language) },
                                onAskAiCostAnalysis = {
                                    initialAiMode = AiMode.BUSINESS
                                    navigateTopTab(Routes.AI_ASSISTANT)
                                },
                                onUpdateBatch = { batch -> gosalaViewModel.updateCompostBatch(batch, language) },
                                onDeleteBatch = { batch -> gosalaViewModel.deleteCompostBatch(batch, language) }
                            )
                        }
                    }

                    composable(Routes.DONATIONS) {
                        DonationAndAdoptionScreen(
                            language = language,
                            session = session,
                            cattleList = cattleList,
                            donationsState = donationsState,
                            sponsorshipsState = sponsorshipsState,
                            preselectedCattleForSponsor = preselectedCattleForSponsor,
                            onClearPreselectedCattle = { preselectedCattleForSponsor = null },
                            onSubmitDonation = { don -> gosalaViewModel.submitDonation(don, language) },
                            onVerifyDonation = { id, status -> gosalaViewModel.verifyDonation(id, status, language) },
                            onSubmitSponsorship = { sp -> gosalaViewModel.submitSponsorship(sp, language) },
                            onReviewSponsorship = { sp, approve -> gosalaViewModel.reviewSponsorship(sp, approve, language) }
                        )
                    }

                    composable(Routes.AI_ASSISTANT) {
                        AiAssistantScreen(
                            language = language,
                            session = session,
                            summary = summary,
                            cattle = cattleList,
                            health = healthList,
                            feedLogs = feedLogsList,
                            finance = financeList,
                            farmers = farmersList,
                            farmerTx = farmerTxList,
                            compost = compostList,
                            donations = donationsList,
                            aiViewModel = aiViewModel,
                            initialMode = initialAiMode,
                            onSaveCattleDraft = { item -> gosalaViewModel.saveCattle(item, false, language) },
                            onSaveFeedDraft = { log -> gosalaViewModel.addFeedLog(log, language) },
                            onSaveExpenseDraft = { tx -> gosalaViewModel.addFinancialTx(tx, language) },
                            onSaveHealthDraft = { rec -> gosalaViewModel.addHealthRecord(rec, language) },
                            onNavigateToCattleSearch = { navigateTopTab(Routes.CATTLE) }
                        )
                    }

                    composable(Routes.REPORTS) {
                        ProtectedRoute(
                            allowed = session.role == UserRole.ADMIN || (session.role == UserRole.STAFF && session.canView(AppModule.REPORTS)),
                            language = language,
                            onRedirectHome = { navigateTopTab(homeRouteForRole) }
                        ) {
                            ReportsScreen(
                                language = language,
                                session = session,
                                cattle = cattleList,
                                health = healthList,
                                feedLogs = feedLogsList,
                                staffAccounts = staffAccountsList,
                                staffOps = staffOpsList,
                                finance = financeList,
                                donations = donationsList,
                                farmerTx = farmerTxList,
                                compost = compostList,
                                onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                    }

                    composable(Routes.NOTIFICATIONS) {
                        NotificationsScreen(
                            language = language,
                            session = session,
                            notificationsState = notificationsState,
                            onMarkRead = { id -> gosalaViewModel.markNotificationRead(id) }
                        )
                    }

                    composable(Routes.PROFILE) {
                        ProfileSettingsScreen(
                            language = language,
                            themeMode = themeMode,
                            session = session,
                            auditLogsState = auditLogsState,
                            onSetLanguage = onSetLanguage,
                            onSetThemeMode = onSetThemeMode,
                            onOpenReports = { navController.navigate(Routes.REPORTS) { launchSingleTop = true } },
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectedRoute(
    allowed: Boolean,
    language: AppLanguage,
    onRedirectHome: () -> Unit,
    content: @Composable () -> Unit
) {
    if (allowed) {
        content()
    } else {
        LaunchedEffect(Unit) {
            onRedirectHome()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("access_denied_guard"),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Default.Lock,
                title = str(language, "अनधिकृत प्रवेश अस्वीकृत", "Unauthorized Access Denied"),
                subtitle = str(
                    language,
                    "इस खाते को यह मॉड्यूल खोलने की अनुमति नहीं है।",
                    "Your account is not authorized to open this module."
                ),
                actionLabel = str(language, "डैशबोर्ड पर वापस जाएं", "Return to Dashboard"),
                onAction = onRedirectHome
            )
        }
    }
}
