package com.to.me.aicamera

import android.content.Context
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface DetectionApi {
    @POST
    suspend fun sendDetection(
        @Url endpoint: String,
        @Body request: DetectionRequest
    ): Response<String>
}

data class DetectionRequest(
    val label: String,
    val imageBase64: String
)


fun createApiFromSetup(context: Context): DetectionApi? {
    val config = runBlocking { SetupPrefs.get(context).firstOrNull() } ?: return null
    val baseUrl = "http://${config.ip}:${config.port}/"

    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(DetectionApi::class.java)
}