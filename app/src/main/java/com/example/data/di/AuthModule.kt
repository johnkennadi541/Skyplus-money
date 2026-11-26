package com.example.data.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.example.data.remote.CredentialManagerGoogleClient
import com.example.data.remote.FirebaseAuthDataSource
import com.example.data.remote.FirebaseAuthGateway
import com.example.data.remote.GoogleCredentialClient
import com.example.data.repository.AuthRepositoryImpl
import com.example.domain.repository.AuthRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthProvidersModule {

  @Provides
  @Singleton
  fun provideFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth {
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    return Firebase.auth
  }

  @Provides
  @Singleton
  fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager =
    CredentialManager.create(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {

  @Binds
  @Singleton
  abstract fun bindGoogleCredentialClient(
    impl: CredentialManagerGoogleClient
  ): GoogleCredentialClient

  @Binds
  @Singleton
  abstract fun bindFirebaseAuthGateway(impl: FirebaseAuthDataSource): FirebaseAuthGateway

  @Binds
  @Singleton
  abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
