package com.bravoscribe.android.data.remote.api

import com.bravoscribe.android.data.remote.dto.CreateEntryRequest
import com.bravoscribe.android.data.remote.dto.CreateTagRequest
import com.bravoscribe.android.data.remote.dto.JournalEntryResponse
import com.bravoscribe.android.data.remote.dto.PageResponse
import com.bravoscribe.android.data.remote.dto.StatsResponse
import com.bravoscribe.android.data.remote.dto.TagResponse
import com.bravoscribe.android.data.remote.dto.UpdateEntryRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface JournalApi {
    @GET("api/journal/entries")
    suspend fun listEntries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 31,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("q") q: String? = null,
    ): PageResponse<JournalEntryResponse>

    @GET("api/journal/entries/{id}")
    suspend fun getEntry(@Path("id") id: String): JournalEntryResponse

    @GET("api/journal/entries/date/{date}")
    suspend fun getEntryByDate(@Path("date") date: String): JournalEntryResponse

    @GET("api/journal/entries/dates")
    suspend fun getEntryDates(@Query("from") from: String, @Query("to") to: String): List<String>

    @POST("api/journal/entries")
    suspend fun createEntry(@Body body: CreateEntryRequest): JournalEntryResponse

    @PUT("api/journal/entries/{id}")
    suspend fun updateEntry(@Path("id") id: String, @Body body: UpdateEntryRequest): JournalEntryResponse

    @DELETE("api/journal/entries/{id}")
    suspend fun deleteEntry(@Path("id") id: String)

    @Streaming
    @GET("api/journal/entries/export")
    suspend fun exportEntries(@Query("from") from: String, @Query("to") to: String): ResponseBody

    @GET("api/journal/stats")
    suspend fun getStats(): StatsResponse

    @GET("api/journal/tags")
    suspend fun listTags(): List<TagResponse>

    @POST("api/journal/tags")
    suspend fun createTag(@Body body: CreateTagRequest): TagResponse

    @DELETE("api/journal/tags/{id}")
    suspend fun deleteTag(@Path("id") id: String)
}
