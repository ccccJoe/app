/*
 * File: NetworkModule.kt
 * Description: Provides network-related dependencies including Retrofit and OkHttp configuration
 * Author: SIMS Team
 */
package com.simsapp.di

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.simsapp.BuildConfig
import com.simsapp.data.local.AuthManager
import com.simsapp.data.remote.ApiService
import com.simsapp.data.remote.AuthApiService
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

    @Provides
    @Singleton
    fun provideHeaderInterceptor(authManager: AuthManager): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val authData = authManager.getUserAuthData()
            
            val requestBuilder = original.newBuilder()
            
            // 基础认证头
            if (authData != null) {
                // 已绑定设备，使用完整的安全认证机制
                try {
                    // 1. 生成nonce（时间戳（毫秒）+ 五位随机数）
                    val nonce = com.simsapp.utils.CryptoUtils.generateNonce()
                    
                    // 2. 获取私钥
                    val privateKey = authManager.getPrivateKey()
                    
                    if (privateKey != null) {
                        // 3. 生成签名：用工号对nonce做盐 -> SHA256 -> RSA签名
                        val signature = com.simsapp.utils.CryptoUtils.generateRequestSignature(
                            nonce, 
                            authData.userCode,
                            privateKey
                        )
                        
                        // 4. 添加安全认证请求头
                        requestBuilder
                            .header("X-CLIENT-TYPE", "mobile")
                            .header("X-USERNAME", authData.userCode)
                            .header("X-NORCE", nonce)
                            .header("X-SIGN", signature)
                            .header("Authorization", "Bearer ${authData.token}")
                    } else {
                        // 私钥不存在，使用基础认证
                        Log.w("NetworkModule", "Private key not found, using basic auth")
                        requestBuilder
                            .header("X-USERNAME", authData.userCode)
                            .header("Authorization", "Bearer ${authData.token}")
                    }
                } catch (e: Exception) {
                    // 签名生成失败，使用基础认证
                    Log.e("NetworkModule", "Failed to generate signature, using basic auth", e)
                    requestBuilder
                        .header("X-USERNAME", authData.userCode)
                        .header("Authorization", "Bearer ${authData.token}")
                }
            } else {
                // 未绑定设备，使用调试模式
                if (BuildConfig.DEBUG) {
                    requestBuilder
                        .header("X-USERNAME", "debug_user")
                        .header("Authorization", "Bearer debug_token")
                } else {
                    Log.w("NetworkModule", "No auth data available in production mode")
                }
            }
            
            chain.proceed(requestBuilder.build())
        }
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
        // 使用 BuildConfig.BASE_URL 按产品风味切换 UAT/Prod 基础地址
        // UAT: https://sims-uat.ink-stone.win/zuul/sims-master/
        // Prod: https://sims.ink-stone.win/zuul/sims-master/
        .baseUrl(BuildConfig.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(client)
        .build()

    /** Provide API service implementation. */
    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService = retrofit.create(AuthApiService::class.java)
}