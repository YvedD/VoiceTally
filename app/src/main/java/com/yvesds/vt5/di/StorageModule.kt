package com.yvesds.vt5.di

import android.content.Context
import androidx.room.Room
import com.yvesds.vt5.core.database.VT5Database
import com.yvesds.vt5.core.database.dao.TellingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Named

/**
 * Hilt Module para Storage/Database dependencies.
 * Provee Room Database singleton y DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Singleton
    @Provides
    fun provideVT5Database(
        @ApplicationContext context: Context
    ): VT5Database = Room.databaseBuilder(
        context,
        VT5Database::class.java,
        "vt5_database"
    ).build()

    @Provides
    fun provideTellingDao(database: VT5Database): TellingDao = database.tellingDao()

    @Provides
    @Singleton
    @Named("vt5_prefs")
    fun provideVt5Prefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("vt5_prefs", Context.MODE_PRIVATE)
}

