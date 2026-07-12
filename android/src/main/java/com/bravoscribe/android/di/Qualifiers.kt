package com.bravoscribe.android.di

import javax.inject.Qualifier

/** The plain OkHttpClient (CookieJar only, no Authenticator) used by TokenAuthenticator
 * itself to call /api/users/refresh without recursing into its own auth handling. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshHttpClient
