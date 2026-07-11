package com.bravoscribe.android.di

import com.bravoscribe.android.data.repository.AuthRepositoryImpl
import com.bravoscribe.android.data.repository.JournalRepositoryImpl
import com.bravoscribe.android.domain.repository.AuthRepository
import com.bravoscribe.android.domain.repository.JournalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository
}
