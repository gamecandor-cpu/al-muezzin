package com.example.data.remote

import com.example.data.model.NominatimResponse
import com.example.data.model.NominatimSearchItem
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NominatimApiService {
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("accept-language") acceptLanguage: String = "ar",
        @Header("User-Agent") userAgent: String = "AlMuezzinAndroidApp/2.0"
    ): NominatimResponse

    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("accept-language") acceptLanguage: String = "ar",
        @Header("User-Agent") userAgent: String = "AlMuezzinAndroidApp/2.0"
    ): List<NominatimSearchItem>

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/"

        fun create(): NominatimApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(NominatimApiService::class.java)
        }
    }
}
