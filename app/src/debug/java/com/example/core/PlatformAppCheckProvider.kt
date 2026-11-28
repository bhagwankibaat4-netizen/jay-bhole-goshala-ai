package com.example.core

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-only App Check provider installer.
 * Compiled strictly into `debug` builds via `debugImplementation(libs.firebase.appcheck.debug)`
 * so the Debug provider SDK is never present in Production/Release APKs.
 */
internal fun installPlatformAppCheckProvider(appCheck: FirebaseAppCheck): AppCheckProviderKind {
    appCheck.installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance(),
        /* isTokenAutoRefreshEnabled = */ true
    )
    return AppCheckProviderKind.DEBUG
}
