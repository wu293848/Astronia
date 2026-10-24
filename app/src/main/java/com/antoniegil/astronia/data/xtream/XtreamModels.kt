package com.antoniegil.astronia.data.xtream

/** A saved source profile. Passwords are kept locally; never log this object. */
data class ProviderProfile(
    val id: String,
    val name: String,
    val type: ProviderType,
    val url: String,
    val username: String = "",
    val password: String = ""
)

enum class ProviderType { M3U, XTREAM }

enum class LibraryKind { LIVE, MOVIES, SERIES }

data class XtreamCategory(val id: String, val name: String)

data class XtreamItem(
    val id: String,
    val name: String,
    val streamUrl: String,
    val categoryId: String = "",
    val kind: LibraryKind,
    val containerExtension: String = "",
    val seriesId: String = "",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

data class XtreamEpisode(
    val id: String,
    val title: String,
    val streamUrl: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val containerExtension: String = ""
)
