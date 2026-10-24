package com.antoniegil.astronia.data.xtream

import com.antoniegil.astronia.util.common.Result
import com.antoniegil.astronia.util.parser.M3U8Channel
import com.antoniegil.astronia.util.parser.M3ULoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class LibraryData(
    val categories: List<XtreamCategory>,
    val items: List<XtreamItem>
)

/**
 * Loads and caches Live / Movies / Shows / Radio content for a saved provider.
 * Xtream accounts use the player API; M3U playlists are split into the four sections
 * from their group titles and stream URLs. Logos and EPG data are never read.
 */
object LibraryRepository {
    private val cache = ConcurrentHashMap<String, LibraryData>()

    private val seriesWords = Regex("""\b(series|shows?|tv shows?|seasons?)\b""")
    private val movieWords = Regex("""\b(movies?|films?|vod|cinema)\b""")

    private fun key(profile: ProviderProfile, kind: LibraryKind) = "${profile.id}:${kind.name}"

    fun clear(profileId: String) {
        cache.keys.removeAll { it.startsWith("$profileId:") }
    }

    suspend fun load(profile: ProviderProfile, kind: LibraryKind, force: Boolean = false): LibraryData =
        withContext(Dispatchers.IO) {
            if (!force) cache[key(profile, kind)]?.let { return@withContext it }
            if (profile.type == ProviderType.XTREAM) loadXtream(profile, kind) else loadM3u(profile)
            cache[key(profile, kind)] ?: LibraryData(emptyList(), emptyList())
        }

    suspend fun episodes(profile: ProviderProfile, seriesId: String): List<XtreamEpisode> =
        withContext(Dispatchers.IO) { XtreamApi(profile).episodes(seriesId) }

    private suspend fun loadXtream(profile: ProviderProfile, kind: LibraryKind) {
        val api = XtreamApi(profile)
        when (kind) {
            LibraryKind.MOVIES ->
                cache[key(profile, kind)] = LibraryData(api.movieCategories(), api.movies())
            LibraryKind.SERIES ->
                cache[key(profile, kind)] = LibraryData(api.seriesCategories(), api.series())
            LibraryKind.LIVE, LibraryKind.RADIO -> {
                val categories = api.liveCategories()
                val streams = api.liveStreams()
                val radioCategoryIds = categories
                    .filter { it.name.contains("radio", ignoreCase = true) }
                    .map { it.id }
                    .toSet()
                val isRadio: (XtreamItem) -> Boolean = { item ->
                    item.categoryId in radioCategoryIds ||
                        (item.categoryId.isBlank() && item.name.contains("radio", ignoreCase = true))
                }
                cache[key(profile, LibraryKind.LIVE)] = LibraryData(
                    categories.filter { it.id !in radioCategoryIds },
                    streams.filterNot(isRadio)
                )
                cache[key(profile, LibraryKind.RADIO)] = LibraryData(
                    categories.filter { it.id in radioCategoryIds },
                    streams.filter(isRadio).map { it.copy(kind = LibraryKind.RADIO) }
                )
            }
        }
    }

    private suspend fun loadM3u(profile: ProviderProfile) {
        val channels = when (val result = M3ULoader.parseM3U8FromUrl(profile.url)) {
            is Result.Success -> result.data
            is Result.Error -> throw IllegalStateException(
                result.message ?: result.exception.message ?: "Could not load M3U playlist"
            )
        }
        if (channels.isEmpty()) {
            throw IllegalStateException("The playlist loaded but contains no playable entries.")
        }
        val grouped = channels.groupBy { classify(it) }
        LibraryKind.values().forEach { kind ->
            val list = grouped[kind].orEmpty()
            cache[key(profile, kind)] = LibraryData(
                categories = list.map { it.group }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .map { XtreamCategory(it, it) },
                items = list.map {
                    XtreamItem(
                        id = it.id,
                        name = it.name,
                        streamUrl = it.url,
                        categoryId = it.group,
                        kind = kind
                    )
                }
            )
        }
    }

    private fun classify(channel: M3U8Channel): LibraryKind {
        val group = channel.group.lowercase()
        val url = channel.url.lowercase()
        return when {
            "/series/" in url -> LibraryKind.SERIES
            "/movie/" in url -> LibraryKind.MOVIES
            group.contains("radio") -> LibraryKind.RADIO
            seriesWords.containsMatchIn(group) -> LibraryKind.SERIES
            movieWords.containsMatchIn(group) -> LibraryKind.MOVIES
            else -> LibraryKind.LIVE
        }
    }
}
