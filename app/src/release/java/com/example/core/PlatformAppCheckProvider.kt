package com.example.core

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release/Production App Check provider installer.
 * Exclusively installs PlayIntegrityAppCheckProviderFactory — DebugAppCheckProviderFactory is
 * not even on the Release classpath.
 */
internal fun installPlatformAppCheckProvider(appCheck: FirebaseAppCheck): AppCheckProviderKind {
    appCheck.installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance(),
        /* isTokenAutoRefreshEnabled = */ true
    )
    return AppCheckProviderKind.PLAY_INTEGRITY
}
