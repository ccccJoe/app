/*
 * File: NetworkModule.kt
 * Description: Hilt module providing Retrofit/OkHttp for network layer with timeouts, logging and common headers.
 * Author: SIMS Team
 */
package com.simsapp.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.simsapp.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * NetworkModule
 *
 * Provides network-related singletons via Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /** Provide a Gson instance. */
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /**
     * Provide a common header interceptor.
     * - Adds Accept and User-Agent headers for all requests.
     * - Injects default X-USERNAME header when not explicitly provided by the request (defaults to "test").
     * - Injects a default Authorization header when not provided (priority: BuildConfig.DEV_AUTH_TOKEN > fixed debug token).
     */
    @Provides
    @Singleton
    fun provideHeaderInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "SIMS-Android/${com.simsapp.BuildConfig.VERSION_NAME}")

        // 1) Default X-USERNAME header (if absent). Keep existing header if already present.
        val defaultUsername = "test"
        if (original.header("X-USERNAME") == null) {
            builder.header("X-USERNAME", defaultUsername)
        }

        // 2) Default Authorization header. If the request already sets Authorization, keep it.
        val defaultAuth: String? = when {
            com.simsapp.BuildConfig.DEV_AUTH_TOKEN.isNotBlank() -> "Bearer ${com.simsapp.BuildConfig.DEV_AUTH_TOKEN}"
            com.simsapp.BuildConfig.DEBUG -> "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0IiwiaWF0IjoxNzYwMDY0MzEyLCJleHAiOjE3NjAxNTA3MTJ9.E4-RSPiLw2J9lxVXuy1wT0SNYV4uo-Pz0oeZiWcltcTzuJ0OJNTppTTgNJKiei9p__Wx-yFgt10Fs3tXcQf3KQ"
            else -> null
        }
        if (original.header("Authorization") == null && !defaultAuth.isNullOrBlank()) {
            builder.header("Authorization", defaultAuth)
        }

        val newReq = builder.build()
        chain.proceed(newReq)
    }

    /** Provide OkHttp client with timeouts and logging for debug builds. */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        headerInterceptor: Interceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(headerInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (com.simsapp.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    /** Provide Retrofit instance bound to the base URL. */
    @Provides
    @Singleton
    fun provideRetrofit(gson: Gson, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://your-api.example.com/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(client)
        .build()

    /** Provide API service implementation. */
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}