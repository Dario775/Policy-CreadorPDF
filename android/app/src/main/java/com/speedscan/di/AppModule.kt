package com.speedscan.di

import android.content.Context
import androidx.room.Room
import com.speedscan.core.utils.NativePdfGenerator
import com.speedscan.data.local.AppDatabase
import com.speedscan.data.local.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "speedscan-db"
        ).build()
    }

    @Provides
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()

    @Provides
    @Singleton
    fun providePdfGenerator(@ApplicationContext context: Context): NativePdfGenerator {
        return NativePdfGenerator(context)
    }

    @Provides
    @Singleton
    fun provideSecurityManager(@ApplicationContext context: Context): com.speedscan.core.security.SecurityManager {
        return com.speedscan.core.security.SecurityManager(context)
    }
}
