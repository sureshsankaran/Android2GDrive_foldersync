package com.foldersync.di

import android.content.Context
import com.foldersync.auth.AuthConfig
import com.foldersync.auth.TokenRefreshInterceptor
import com.foldersync.auth.TokenRefreshManager
import com.foldersync.data.repository.AuthRepository
import com.foldersync.data.repository.AuthRepositoryImpl
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideGoogleSignInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestServerAuthCode(AuthConfig.WEB_CLIENT_ID, true)
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope(DriveScopes.DRIVE_READONLY)
            )
            .build()

    @Provides
    @Singleton
    fun provideGoogleSignInClient(
        @ApplicationContext context: Context,
        options: GoogleSignInOptions
    ): GoogleSignInClient = GoogleSignIn.getClient(context, options)

    @Provides
    @Singleton
    fun provideTokenRefreshInterceptor(
        tokenRefreshManager: TokenRefreshManager
    ): TokenRefreshInterceptor = TokenRefreshInterceptor(tokenRefreshManager)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindings {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}
