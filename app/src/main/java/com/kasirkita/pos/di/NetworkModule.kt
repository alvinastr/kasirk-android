package com.kasirkita.pos.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.kasirkita.pos.core.datastore.TokenDataStore
import com.kasirkita.pos.core.network.ApiConstants
import com.kasirkita.pos.core.network.AuthInterceptor
import com.kasirkita.pos.core.network.AuthTokenProvider
import com.kasirkita.pos.data.api.AuthApi
import com.kasirkita.pos.data.api.OutletApi
import com.kasirkita.pos.data.api.ProductApi
import com.kasirkita.pos.data.api.ReceiptApi
import com.kasirkita.pos.data.api.ShiftApi
import com.kasirkita.pos.data.api.TransactionApi
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
    fun provideAuthTokenProvider(tokenDataStore: TokenDataStore): AuthTokenProvider = tokenDataStore

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .serializeNulls()
        .create()

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

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideOutletApi(retrofit: Retrofit): OutletApi = retrofit.create(OutletApi::class.java)

    @Provides
    @Singleton
    fun provideProductApi(retrofit: Retrofit): ProductApi = retrofit.create(ProductApi::class.java)

    @Provides
    @Singleton
    fun provideReceiptApi(retrofit: Retrofit): ReceiptApi = retrofit.create(ReceiptApi::class.java)

    @Provides
    @Singleton
    fun provideShiftApi(retrofit: Retrofit): ShiftApi = retrofit.create(ShiftApi::class.java)

    @Provides
    @Singleton
    fun provideTransactionApi(retrofit: Retrofit): TransactionApi =
        retrofit.create(TransactionApi::class.java)

    private const val NETWORK_TIMEOUT_SECONDS = 30L
}
