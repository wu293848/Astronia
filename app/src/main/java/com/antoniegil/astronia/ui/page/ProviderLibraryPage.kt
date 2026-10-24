package com.antoniegil.astronia.ui.page

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antoniegil.astronia.data.xtream.*
import com.antoniegil.astronia.ui.component.BackButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

private const val UI_PREFS = "library_ui"
private const val KEY_ACTIVE_PROVIDER = "active_provider"
private const val KEY_LAST_TAB = "last_tab"

private fun LibraryKind.label(): String = when (this) {
    LibraryKind.LIVE -> "Live"
    LibraryKind.MOVIES -> "Movies"
    LibraryKind.SERIES -> "Shows"
    LibraryKind.RADIO -> "Radio"
}

private fun LibraryKind.icon(): ImageVector = when (this) {
    LibraryKind.LIVE -> Icons.Outlined.LiveTv
    LibraryKind.MOVIES -> Icons.Outlined.Movie
    LibraryKind.SERIES -> Icons.Outlined.Tv
    LibraryKind.RADIO -> Icons.Outlined.Radio
}

/**
 * Startup page. Shows the active source with four sections: Live, Movies, Shows and Radio.
 * Channel logos and the TV guide are intentionally not part of this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderLibraryPage(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLink: () -> Unit,
    onPlayUrl: (String, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    val store = remember { ProviderStore(context) }
    val scope = rememberCoroutineScope()

    var providers by remember { mutableStateOf(store.load()) }
    var activeId by remember { mutableStateOf(prefs.getString(KEY_ACTIVE_PROVIDER, null)) }
    val active = providers.firstOrNull { it.id == activeId } ?: providers.firstOrNull()
    var tab by remember {
        mutableStateOf(
            LibraryKind.values().firstOrNull { it.name == prefs.getString(KEY_LAST_TAB, null) }
                ?: LibraryKind.LIVE
        )
    }

    var data by remember { mutableStateOf<LibraryData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var forceNext by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var openSeries by remember { mutableStateOf<XtreamItem?>(null) }
    var episodes by remember { mutableStateOf(emptyList<XtreamEpisode>()) }

    var showSources by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(active?.id, tab, reloadToken) {
        val profile = active ?: return@LaunchedEffect
        val force = forceNext
        forceNext = false
        category = null
        query = ""
        openSeries = null
        episodes = emptyList()
        loading = true
        error = null
        try {
            data = LibraryRepository.load(profile, tab, force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            data = null
            error = e.message ?: "Could not load this source"
        } finally {
            loading = false
        }
    }

    BackHandler(enabled = openSeries != null) {
        openSeries = null
        episodes = emptyList()
    }

    val categoryNames = remember(data) {
        data?.categories.orEmpty().associate { it.id to it.name }
    }
    val shown = remember(data, category, query) {
        data?.items.orEmpty().filter { item ->
            (category == null || item.categoryId == category) &&
                (query.isBlank() || item.name.contains(query, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = openSeries?.name ?: active?.name ?: "Astronia",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (openSeries != null) BackButton {
                        openSeries = null
                        episodes = emptyList()
                    }
                },
                actions = {
                    if (active != null && openSeries == null) {
                        IconButton(onClick = {
                            forceNext = true
                            reloadToken++
                        }) { Icon(Icons.Outlined.Refresh, "Refresh") }
                        IconButton(onClick = { showSources = true }) {
                            Icon(Icons.Outlined.SwapHoriz, "Sources")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Play a link") },
                                leadingIcon = { Icon(Icons.Outlined.Link, null) },
                                onClick = { menuOpen = false; onOpenLink() }
                            )
                            DropdownMenuItem(
                                text = { Text("History") },
                                leadingIcon = { Icon(Icons.Outlined.Subscriptions, null) },
                                onClick = { menuOpen = false; onOpenHistory() }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menuOpen = false; onOpenSettings() }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (active != null && openSeries == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    LibraryKind.values().forEach { kind ->
                        NavigationBarItem(
                            selected = tab == kind,
                            onClick = {
                                tab = kind
                                prefs.edit().putString(KEY_LAST_TAB, kind.name).apply()
                            },
                            icon = { Icon(kind.icon(), kind.label()) },
                            label = { Text(kind.label()) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (active == null) {
                EmptyLibrary(onAdd = { showAdd = true }, onOpenLink = onOpenLink)
            } else {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (openSeries != null) {
                    EpisodeList(episodes, loading, error) { episode ->
                        onPlayUrl(episode.streamUrl, episode.title)
                    }
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Search ${tab.label().lowercase()}") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    val categories = data?.categories.orEmpty()
                    if (categories.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = category == null,
                                    onClick = { category = null },
                                    label = { Text("All") }
                                )
                            }
                            items(categories) { cat ->
                                FilterChip(
                                    selected = category == cat.id,
                                    onClick = { category = cat.id },
                                    label = { Text(cat.name, maxLines = 1) }
                                )
                            }
                        }
                    }
                    when {
                        error != null -> MessageState(error ?: "", "Retry") {
                            forceNext = true
                            reloadToken++
                        }
                        !loading && shown.isEmpty() -> MessageState(
                            if (query.isNotBlank()) "Nothing matches your search."
                            else "No ${tab.label().lowercase()} found in this source.",
                            null
                        ) {}
                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(shown, key = { index, item -> "$index:${item.id}" }) { _, item ->
                                val categoryName = categoryNames[item.categoryId]
                                val opensEpisodes = tab == LibraryKind.SERIES &&
                                    active.type == ProviderType.XTREAM && item.seriesId.isNotBlank()
                                ListItem(
                                    headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = if (categoryName.isNullOrBlank()) null else ({ Text(categoryName, maxLines = 1) }),
                                    leadingContent = { Icon(tab.icon(), null, tint = MaterialTheme.colorScheme.primary) },
                                    trailingContent = {
                                        Icon(
                                            if (opensEpisodes) Icons.Outlined.ChevronRight else Icons.Outlined.PlayArrow,
                                            if (opensEpisodes) "Open" else "Play"
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable {
                                        if (opensEpisodes) {
                                            openSeries = item
                                            episodes = emptyList()
                                            loading = true
                                            error = null
                                            scope.launch {
                                                try {
                                                    episodes = LibraryRepository.episodes(active, item.seriesId)
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    error = e.message ?: "Could not load episodes"
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        } else {
                                            onPlayUrl(item.streamUrl, item.name)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text("Sources") },
            text = {
                LazyColumn {
                    items(providers, key = { it.id }) { profile ->
                        ListItem(
                            headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(if (profile.type == ProviderType.XTREAM) "Xtream Codes" else "M3U playlist")
                            },
                            leadingContent = { RadioButton(selected = profile.id == active?.id, onClick = null) },
                            trailingContent = {
                                IconButton(onClick = {
                                    providers = providers.filterNot { it.id == profile.id }
                                    store.save(providers)
                                    LibraryRepository.clear(profile.id)
                                    if (activeId == profile.id) {
                                        activeId = null
                                        prefs.edit().remove(KEY_ACTIVE_PROVIDER).apply()
                                        data = null
                                    }
                                    if (providers.isEmpty()) showSources = false
                                }) { Icon(Icons.Outlined.Delete, "Delete source") }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                activeId = profile.id
                                prefs.edit().putString(KEY_ACTIVE_PROVIDER, profile.id).apply()
                                showSources = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSources = false; showAdd = true }) { Text("Add source") } },
            dismissButton = { TextButton(onClick = { showSources = false }) { Text("Close") } }
        )
    }

    if (showAdd) {
        AddSourceDialog(
            onDismiss = { showAdd = false },
            onAdd = { profile ->
                // Validate before saving so a bad URL or login is reported inside the dialog.
                if (profile.type == ProviderType.XTREAM) {
                    if (!XtreamApi(profile).validateLogin()) {
                        throw IllegalArgumentException("Login failed. Check server URL, username and password.")
                    }
                } else {
                    LibraryRepository.load(profile, LibraryKind.LIVE, force = true)
                }
                providers = providers + profile
                store.save(providers)
                activeId = profile.id
                tab = LibraryKind.LIVE
                prefs.edit()
                    .putString(KEY_ACTIVE_PROVIDER, profile.id)
                    .putString(KEY_LAST_TAB, LibraryKind.LIVE.name)
                    .apply()
                showAdd = false
            }
        )
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: suspend (ProviderProfile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(ProviderType.XTREAM) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val ready = url.isNotBlank() &&
        (type == ProviderType.M3U || (username.isNotBlank() && password.isNotBlank()))

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Add source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == ProviderType.XTREAM,
                        onClick = { type = ProviderType.XTREAM },
                        label = { Text("Xtream Codes") }
                    )
                    FilterChip(
                        selected = type == ProviderType.M3U,
                        onClick = { type = ProviderType.M3U },
                        label = { Text("M3U playlist") }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(if (type == ProviderType.XTREAM) "Server URL (http://host:port)" else "M3U playlist URL") },
                    maxLines = 3
                )
                if (type == ProviderType.XTREAM) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = ready && !busy, onClick = {
                val cleanUrl = url.trim().let { if (it.startsWith("http", ignoreCase = true)) it else "http://$it" }
                val profile = ProviderProfile(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifBlank { if (type == ProviderType.XTREAM) "Xtream" else "Playlist" },
                    type = type,
                    url = cleanUrl,
                    username = username.trim(),
                    password = password
                )
                scope.launch {
                    busy = true
                    error = null
                    try {
                        onAdd(profile)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "Could not add source"
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit, onOpenLink: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.LiveTv,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Welcome to Astronia", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add an Xtream Codes account or an M3U playlist to browse Live, Movies, Shows and Radio.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) { Text("Add source") }
        TextButton(onClick = onOpenLink) { Text("Play a link instead") }
    }
}

@Composable
private fun MessageState(message: String, actionLabel: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<XtreamEpisode>,
    loading: Boolean,
    error: String?,
    onPlay: (XtreamEpisode) -> Unit
) {
    when {
        error != null -> MessageState(error, null) {}
        !loading && episodes.isEmpty() -> MessageState("No episodes found.", null) {}
        else -> {
            val seasons = remember(episodes) { episodes.groupBy { it.seasonNumber } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                seasons.forEach { (season, list) ->
                    item(key = "season:$season") {
                        Text(
                            if (season == 0) "Specials" else "Season $season",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(list, key = { "ep:$season:${it.episodeNumber}:${it.id}" }) { episode ->
                        ListItem(
                            headlineContent = { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("Episode ${episode.episodeNumber}") },
                            trailingContent = { Icon(Icons.Outlined.PlayArrow, "Play") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPlay(episode) }
                        )
                    }
                }
            }
        }
    }
}
