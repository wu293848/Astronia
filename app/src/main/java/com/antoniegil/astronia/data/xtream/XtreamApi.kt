package com.antoniegil.astronia.data.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal Xtream Codes API client. It intentionally ignores EPG and channel-logo fields. */
class XtreamApi(private val profile: ProviderProfile) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String = profile.url.trim().trimEnd('/')

    private fun apiUrl(action: String? = null, extra: Map<String, String> = emptyMap()): String {
        val parsed = ("$baseUrl/player_api.php").toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid provider URL")
        val builder = parsed.newBuilder()
            .addQueryParameter("username", profile.username)
            .addQueryParameter("password", profile.password)
        if (!action.isNullOrBlank()) builder.addQueryParameter("action", action)
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private suspend fun getJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Provider returned HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Empty provider response")
        }
    }

    suspend fun validateLogin(): Boolean {
        val root = JSONObject(getJson(apiUrl()))
        val info = root.optJSONObject("user_info") ?: return false
        return info.optString("auth") == "1" && info.optString("status").lowercase() == "active"
    }

    suspend fun liveCategories(): List<XtreamCategory> = categories("get_live_categories")
    suspend fun movieCategories(): List<XtreamCategory> = categories("get_vod_categories")
    suspend fun seriesCategories(): List<XtreamCategory> = categories("get_series_categories")

    private suspend fun categories(action: String): List<XtreamCategory> =
        parseArray(getJson(apiUrl(action))) { obj ->
            XtreamCategory(obj.optString("category_id"), obj.optString("category_name"))
        }

    suspend fun liveStreams(categoryId: String? = null): List<XtreamItem> =
        streams("get_live_streams", categoryId, LibraryKind.LIVE)

    suspend fun movies(categoryId: String? = null): List<XtreamItem> =
        streams("get_vod_streams", categoryId, LibraryKind.MOVIES)

    suspend fun series(categoryId: String? = null): List<XtreamItem> =
        streams("get_series", categoryId, LibraryKind.SERIES)

    private suspend fun streams(action: String, categoryId: String?, kind: LibraryKind): List<XtreamItem> {
        val params = if (categoryId.isNullOrBlank()) emptyMap() else mapOf("category_id" to categoryId)
        return parseArray(getJson(apiUrl(action, params))) { obj ->
            val id = when (kind) {
                LibraryKind.LIVE -> obj.optString("stream_id")
                LibraryKind.MOVIES -> obj.optString("stream_id")
                LibraryKind.SERIES -> obj.optString("series_id")
            }
            val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
            val url = when (kind) {
                LibraryKind.LIVE -> "$baseUrl/live/${profile.username}/${profile.password}/$id.ts"
                LibraryKind.MOVIES -> "$baseUrl/movie/${profile.username}/${profile.password}/$id.$ext"
                LibraryKind.SERIES -> ""
            }
            XtreamItem(
                id = id,
                name = obj.optString("name", obj.optString("title", "Untitled")),
                streamUrl = url,
                categoryId = obj.optString("category_id"),
                kind = kind,
                containerExtension = ext,
                seriesId = if (kind == LibraryKind.SERIES) id else ""
            )
        }
    }

    suspend fun episodes(seriesId: String): List<XtreamEpisode> {
        val root = JSONObject(getJson(apiUrl("get_series_info", mapOf("series_id" to seriesId))))
        val episodes = root.optJSONObject("episodes") ?: return emptyList()
        val result = mutableListOf<XtreamEpisode>()
        val seasons = episodes.keys()
        while (seasons.hasNext()) {
            val seasonKey = seasons.next()
            val season = seasonKey.toIntOrNull() ?: 0
            val list = episodes.optJSONArray(seasonKey) ?: continue
            for (i in 0 until list.length()) {
                val ep = list.optJSONObject(i) ?: continue
                val id = ep.optString("id")
                val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                result += XtreamEpisode(
                    id = id,
                    title = ep.optString("title", "Episode ${i + 1}"),
                    streamUrl = "$baseUrl/series/${profile.username}/${profile.password}/$id.$ext",
                    seasonNumber = season,
                    episodeNumber = ep.optInt("episode_num", i + 1),
                    containerExtension = ext
                )
            }
        }
        return result.sortedWith(compareBy<XtreamEpisode> { it.seasonNumber }.thenBy { it.episodeNumber })
    }

    private inline fun <T> parseArray(json: String, map: (JSONObject) -> T): List<T> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(map(obj))
            }
        }
    }
}
