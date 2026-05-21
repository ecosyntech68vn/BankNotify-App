package com.banknotify.di

import android.app.Application
import com.banknotify.core.AppConfig
import com.banknotify.core.BankNotifyApp
import com.banknotify.core.db.DatabaseHelper
import com.banknotify.service.webhook.WebhookManager
import com.banknotify.update.UpdateManager
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
    fun provideAppConfig(app: Application): AppConfig = AppConfig(
        version = (app as BankNotifyApp).appVersion,
        build = (app as BankNotifyApp).appBuild
    )

    @Provides
    @Singleton
    fun provideDatabaseHelper(@ApplicationContext context: android.content.Context): DatabaseHelper =
        DatabaseHelper(context)

    @Provides
    @Singleton
    fun provideWebhookManager(@ApplicationContext context: android.content.Context): WebhookManager =
        WebhookManager(context)

    @Provides
    @Singleton
    fun provideUpdateManager(@ApplicationContext context: android.content.Context, appConfig: AppConfig): UpdateManager =
        UpdateManager(context, appConfig)
}
