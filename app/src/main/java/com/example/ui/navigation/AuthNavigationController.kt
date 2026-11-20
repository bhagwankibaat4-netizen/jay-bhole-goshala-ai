package com.example.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppModule
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.ui.screens.Routes

/**
 * Represents the verified authentication and authorization state used by [AuthNavigationController]
 * to route users between the Login Screen, loading/error states, and role-based Dashboards.
 */
sealed interface AuthNavigationState {
    data object Loading : AuthNavigationState

    data class Unauthenticated(
        val errorMessage: String? = null,
        val rejectedAccountNote: String? = null
    ) : AuthNavigationState

    data class SessionError(
        val message: String
    ) : AuthNavigationState

    data class Authorized(
        val session: UserSession
    ) : AuthNavigationState
}

/**
 * Central Navigation Controller responsible for authentication state checks and role-based routing:
 * - Redirects unauthenticated or unauthorized users to [Routes.LOGIN] while clearing protected backstack entries.
 * - Redirects verified authenticated users to the Dashboard corresponding to their [UserRole]
 *   ([UserRole.ADMIN] -> [Routes.ADMIN_DASHBOARD], [UserRole.STAFF] -> [Routes.STAFF_DASHBOARD],
 *   [UserRole.PUBLIC] -> [Routes.PUBLIC_DASHBOARD]).
 * - Guards all protected module routes against both unauthenticated access and cross-role privilege escalation.
 */
@Stable
class AuthNavigationController(
    val navController: NavHostController
) {
    val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    /**
     * Synchronizes the underlying [NavHostController] with the latest [AuthNavigationState].
     * Always clears the backstack when crossing the authentication boundary so users cannot
     * navigate back into protected dashboards after sign-out or back into Login after sign-in.
     */
    fun syncWithAuthState(authState: AuthNavigationState) {
        val targetRoute = resolveTargetRoute(authState)
        if (currentRoute == targetRoute) return
        navigateReplacingStack(targetRoute)
    }

    /**
     * Guarded navigation to [route].
     * - If the user is not authorized (or has a blank UID), redirects immediately to [Routes.LOGIN].
     * - If the user is authorized for the app but not permitted on [route], redirects to their
     *   role-specific Dashboard.
     */
    fun navigateProtected(route: String, authState: AuthNavigationState) {
        if (authState !is AuthNavigationState.Authorized || authState.session.uid.isBlank()) {
            redirectToLogin()
            return
        }
        val session = authState.session
        val resolvedRoute = if (canAccessRoute(session, route)) {
            route
        } else {
            dashboardRouteForRole(session.role)
        }
        if (currentRoute == resolvedRoute) return

        if (resolvedRoute == dashboardRouteForRole(session.role)) {
            navigateReplacingStack(resolvedRoute)
        } else {
            navController.navigate(resolvedRoute) {
                launchSingleTop = true
            }
        }
    }

    fun redirectToLogin() {
        if (currentRoute == Routes.LOGIN) return
        navigateReplacingStack(Routes.LOGIN)
    }

    fun redirectToRoleDashboard(role: UserRole) {
        val target = dashboardRouteForRole(role)
        if (currentRoute == target) return
        navigateReplacingStack(target)
    }

    private fun navigateReplacingStack(route: String) {
        navController.navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    companion object {
        fun dashboardRouteForRole(role: UserRole): String = when (role) {
            UserRole.ADMIN -> Routes.ADMIN_DASHBOARD
            UserRole.STAFF -> Routes.STAFF_DASHBOARD
            UserRole.PUBLIC -> Routes.PUBLIC_DASHBOARD
        }

        /**
         * Determines the authoritative root destination for a given [AuthNavigationState].
         */
        fun resolveTargetRoute(
            authState: AuthNavigationState,
            requestedRoute: String? = null
        ): String = when (authState) {
            is AuthNavigationState.Loading -> Routes.AUTH_LOADING
            is AuthNavigationState.Unauthenticated -> Routes.LOGIN
            is AuthNavigationState.SessionError -> Routes.AUTH_ERROR
            is AuthNavigationState.Authorized -> {
                val session = authState.session
                if (session.uid.isBlank() || (session.role == UserRole.STAFF && !session.staffActive)) {
                    Routes.LOGIN
                } else if (
                    requestedRoute != null &&
                    requestedRoute != Routes.LOGIN &&
                    requestedRoute != Routes.AUTH_LOADING &&
                    requestedRoute != Routes.AUTH_ERROR &&
                    canAccessRoute(session, requestedRoute)
                ) {
                    requestedRoute
                } else {
                    dashboardRouteForRole(session.role)
                }
            }
        }

        /**
         * Verifies whether [session] is authenticated and authorized to access [route].
         */
        fun canAccessRoute(session: UserSession?, route: String): Boolean {
            if (route == Routes.LOGIN || route == Routes.AUTH_LOADING || route == Routes.AUTH_ERROR) {
                return true
            }
            if (session == null || session.uid.isBlank()) return false
            if (session.role == UserRole.STAFF && !session.staffActive) return false

            return when (route) {
                Routes.ADMIN_DASHBOARD -> session.role == UserRole.ADMIN
                Routes.STAFF_DASHBOARD -> session.role == UserRole.STAFF
                Routes.PUBLIC_DASHBOARD -> session.role == UserRole.PUBLIC
                Routes.CATTLE -> session.canView(AppModule.CATTLE)
                Routes.HEALTH -> session.role != UserRole.PUBLIC && session.canView(AppModule.HEALTH)
                Routes.FEED -> session.role != UserRole.PUBLIC && session.canView(AppModule.FEED)
                Routes.STAFF -> session.role == UserRole.ADMIN || (session.role == UserRole.STAFF && session.canView(AppModule.STAFF))
                Routes.FINANCE -> session.role != UserRole.PUBLIC && session.canView(AppModule.FINANCE)
                Routes.FARMERS -> session.role != UserRole.PUBLIC && session.canView(AppModule.FARMERS)
                Routes.PRODUCTION -> session.role != UserRole.PUBLIC && session.canView(AppModule.PRODUCTION)
                Routes.REPORTS -> session.role == UserRole.ADMIN || (session.role == UserRole.STAFF && session.canView(AppModule.REPORTS))
                Routes.DONATIONS -> session.canView(AppModule.DONATIONS) || session.role == UserRole.PUBLIC
                Routes.AI_ASSISTANT -> session.canView(AppModule.AI) || session.role == UserRole.PUBLIC
                Routes.NOTIFICATIONS, Routes.PROFILE -> true
                else -> false
            }
        }
    }
}

@Composable
fun rememberAuthNavigationController(
    navController: NavHostController = rememberNavController()
): AuthNavigationController = remember(navController) {
    AuthNavigationController(navController)
}

/**
 * Root authentication-aware [NavHost] managed by [AuthNavigationController].
 * Reacts automatically to [authState] transitions and enforces role-based route guards on every
 * dashboard destination so unauthorized users are always redirected to [Routes.LOGIN] or their
 * own verified role Dashboard.
 */
@Composable
fun GosalaRootNavHost(
    authState: AuthNavigationState,
    authNavController: AuthNavigationController = rememberAuthNavigationController(),
    loadingContent: @Composable () -> Unit,
    loginContent: @Composable (errorMessage: String?, rejectedAccountNote: String?) -> Unit,
    sessionErrorContent: @Composable (message: String) -> Unit,
    adminDashboardContent: @Composable (UserSession) -> Unit,
    staffDashboardContent: @Composable (UserSession) -> Unit,
    publicDashboardContent: @Composable (UserSession) -> Unit
) {
    val initialRoute = remember { AuthNavigationController.resolveTargetRoute(authState) }

    LaunchedEffect(authState) {
        authNavController.syncWithAuthState(authState)
    }

    NavHost(
        navController = authNavController.navController,
        startDestination = initialRoute,
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) },
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Routes.AUTH_LOADING) {
            loadingContent()
        }

        composable(Routes.LOGIN) {
            val unauth = authState as? AuthNavigationState.Unauthenticated
            loginContent(unauth?.errorMessage, unauth?.rejectedAccountNote)
        }

        composable(Routes.AUTH_ERROR) {
            val err = authState as? AuthNavigationState.SessionError
            if (err != null) {
                sessionErrorContent(err.message)
            } else {
                loadingContent()
                LaunchedEffect(authState) { authNavController.syncWithAuthState(authState) }
            }
        }

        composable(Routes.ADMIN_DASHBOARD) {
            RoleDestinationGuard(
                requiredRole = UserRole.ADMIN,
                authState = authState,
                authNavController = authNavController,
                fallbackLoadingContent = loadingContent,
                content = adminDashboardContent
            )
        }

        composable(Routes.STAFF_DASHBOARD) {
            RoleDestinationGuard(
                requiredRole = UserRole.STAFF,
                authState = authState,
                authNavController = authNavController,
                fallbackLoadingContent = loadingContent,
                content = staffDashboardContent
            )
        }

        composable(Routes.PUBLIC_DASHBOARD) {
            RoleDestinationGuard(
                requiredRole = UserRole.PUBLIC,
                authState = authState,
                authNavController = authNavController,
                fallbackLoadingContent = loadingContent,
                content = publicDashboardContent
            )
        }
    }
}

@Composable
private fun RoleDestinationGuard(
    requiredRole: UserRole,
    authState: AuthNavigationState,
    authNavController: AuthNavigationController,
    fallbackLoadingContent: @Composable () -> Unit,
    content: @Composable (UserSession) -> Unit
) {
    val authorizedSession = (authState as? AuthNavigationState.Authorized)
        ?.session
        ?.takeIf { it.uid.isNotBlank() }

    if (authorizedSession != null &&
        authorizedSession.role == requiredRole &&
        (requiredRole != UserRole.STAFF || authorizedSession.staffActive)
    ) {
        content(authorizedSession)
    } else {
        fallbackLoadingContent()
        LaunchedEffect(authState, requiredRole) {
            if (authorizedSession == null || (authorizedSession.role == UserRole.STAFF && !authorizedSession.staffActive)) {
                authNavController.redirectToLogin()
            } else {
                authNavController.redirectToRoleDashboard(authorizedSession.role)
            }
        }
    }
}
