package com.yvesds.vt5.di

import com.yvesds.vt5.features.telling.data.TellingRepository
import android.content.Context
import android.content.SharedPreferences
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.features.birdnet.BirdNetSseClient
import com.yvesds.vt5.features.telling.*
import com.yvesds.vt5.features.telling.controller.UploadController
import com.yvesds.vt5.net.TrektellenApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt Module for providing dependencies that cannot be constructor-injected easily.
 */
@Module
@InstallIn(SingletonComponent::class)
object ControllersModule {

    @Singleton
    @Provides
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Provides
    @Singleton
    fun provideTrektellenApi(): TrektellenApi = TrektellenApi

    @Provides
    @Singleton
    fun provideTellingUploadCore(@ApplicationContext context: Context): TellingUploadCore =
        TellingUploadCore(context)

    @Provides
    @Singleton
    fun provideUploadController(
        scope: CoroutineScope,
        api: TrektellenApi,
        afrondHandler: TellingAfrondHandler,
        uploadCore: TellingUploadCore,
        repository: TellingRepository
    ): UploadController = UploadController(scope, api, afrondHandler, uploadCore, repository)

    @Provides
    @Singleton
    fun provideTellingAfrondHandler(
        @ApplicationContext context: Context,
        backupManager: TellingBackupManager,
        dataProcessor: TellingDataProcessor,
        repository: TellingRepository,
        envelopePersistence: TellingEnvelopePersistence
    ): TellingAfrondHandler = TellingAfrondHandler(context, backupManager, dataProcessor, repository, envelopePersistence)

    @Provides
    @Singleton
    fun provideTellingLogManager(): TellingLogManager = TellingLogManager(600)

    @Provides
    @Singleton
    fun provideTegelBeheer(): TegelBeheer = TegelBeheer(object : TegelUi {
        override fun submitTiles(list: List<SoortTile>) {}
        override fun onTileCountUpdated(soortId: String, newCount: Int) {}
    })

    @Provides
    @Singleton
    fun provideTellingBackupManager(
        @ApplicationContext context: Context,
        safHelper: SaFStorageHelper
    ): TellingBackupManager = TellingBackupManager(context, safHelper)

    @Provides
    @Singleton
    fun provideTellingEnvelopePersistence(
        @ApplicationContext context: Context,
        safHelper: SaFStorageHelper
    ): TellingEnvelopePersistence = TellingEnvelopePersistence(context, safHelper)

    @Provides
    @Singleton
    fun provideTellingSpeechHandler(
        @ApplicationContext context: Context,
        scope: CoroutineScope,
        safHelper: SaFStorageHelper,
        @Named("vt5_prefs") prefs: SharedPreferences
    ): TellingSpeechHandler = TellingSpeechHandler(context as android.app.Activity, context as androidx.lifecycle.LifecycleOwner, safHelper, prefs)

    @Provides
    @Singleton
    fun provideTellingMatchResultHandler(@ApplicationContext context: Context): TellingMatchResultHandler = 
        TellingMatchResultHandler(context as android.app.Activity)
}
