package com.groove.music.core.network

import com.groove.music.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("X-Groove-Api-Key", BuildConfig.GROOVE_API_KEY)
        val request = requestBuilder.build()
        return chain.proceed(request)
    }
}
