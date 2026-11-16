package com.example.myproducts.di

import com.example.myproducts.BuildConfig
import com.example.myproducts.data.remote.CatalogueApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** How the app talks to our backend. One instance of each for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // The server may add fields later; ignoring unknown keys means that never breaks the app.
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Our backend is on the same machine as the emulator: connecting should be instant.
        .connectTimeout(10, TimeUnit.SECONDS)
        // A user-experience limit first: nobody waits much longer than this on a phone.
        // It still sits deliberately ABOVE the backend's bounded worst case, so the user
        // gets the server's handled error (502 -> "Something went wrong.") rather than a
        // client-side timeout:
        //   featured / search / product: 1 upstream call               -> ~5s
        //   category page: 2 upstream calls run in parallel            -> ~5s
        //   enquiry (file transport, the default): lookup only         -> ~5s
        //   enquiry over optional SMTP: lookup 5s + SMTP stall 5s      -> ~10s
        // When the server's worst case was too slow, we made the server faster rather
        // than this timeout longer (see DECISIONS.md).
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideCatalogueApi(client: OkHttpClient, json: Json): CatalogueApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CatalogueApi::class.java)
}
