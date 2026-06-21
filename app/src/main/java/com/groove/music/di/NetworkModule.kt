package com.groove.music.di

import com.groove.music.BuildConfig
import com.groove.music.core.network.AuthInterceptor
import com.groove.music.core.network.LyricsApiService
import com.groove.music.core.network.RecommendationsApiService
import com.groove.music.core.network.SonglinkApiService
import com.groove.music.core.network.SpotifyApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(): AuthInterceptor = AuthInterceptor()

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // Use BASIC in debug so we don't log full audio bytes in Logcat
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)   // TIDAL mirrors can be slow
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)     // 5 min for large audio files
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val rawUrl = BuildConfig.SERVER_BASE_URL
        // Guard against placeholder/invalid URLs that cause Retrofit to crash at startup
        val safeUrl = if (rawUrl.isBlank() || rawUrl == "https://your-server.com" ||
            rawUrl.contains(".X:") || rawUrl.contains("your-") || !rawUrl.startsWith("http")) {
            "http://localhost:3001/" // safe fallback — will fail at runtime, not at startup
        } else {
            if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
        }
        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSpotifyApiService(retrofit: Retrofit): SpotifyApiService =
        retrofit.create(SpotifyApiService::class.java)

    @Provides
    @Singleton
    fun provideSonglinkApiService(retrofit: Retrofit): SonglinkApiService =
        retrofit.create(SonglinkApiService::class.java)

    @Provides
    @Singleton
    fun provideRecommendationsApiService(retrofit: Retrofit): RecommendationsApiService =
        retrofit.create(RecommendationsApiService::class.java)

    @Provides
    @Singleton
    fun provideLyricsApiService(retrofit: Retrofit): LyricsApiService =
        retrofit.create(LyricsApiService::class.java)
}
