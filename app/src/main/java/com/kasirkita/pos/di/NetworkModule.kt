package com.kasirkita.pos.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kasirkita.pos.core.network.ApiConstants
import com.kasirkita.pos.core.network.AuthInterceptor
import com.kasirkita.pos.core.network.AuthTokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthTokenProvider(): AuthTokenProvider = AuthTokenProvider {
        // Replace with the latest cached token when DataStore integration is added.
        null
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(
        @ApplicationContext context: Context,
    ): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        level = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        gson: Gson,
        okHttpClient: OkHttpClient,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(ApiConstants.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private const val NETWORK_TIMEOUT_SECONDS = 30L
}
