package com.antoniegil.astronia.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antoniegil.astronia.data.xtream.*
import com.antoniegil.astronia.util.parser.M3ULoader
import com.antoniegil.astronia.util.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderLibraryPage(onBack: () -> Unit, onPlayUrl: (String, String) -> Unit) {
    val context = LocalContext.current
    val store = remember { ProviderStore(context) }
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(store.load()) }
    var showAdd by remember { mutableStateOf(false) }
    var providerName by remember { mutableStateOf("") }
    var providerUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProviderType.XTREAM) }
    var selected by remember { mutableStateOf<ProviderProfile?>(null) }
    var kind by remember { mutableStateOf(LibraryKind.LIVE) }
    var categories by remember { mutableStateOf(emptyList<XtreamCategory>()) }
    var items by remember { mutableStateOf(emptyList<XtreamItem>()) }
    var episodes by remember { mutableStateOf(emptyList<XtreamEpisode>()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSeries by remember { mutableStateOf<XtreamItem?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reloadLibrary(profile: ProviderProfile, libraryKind: LibraryKind, categoryId: String? = null) {
        selected = profile
        kind = libraryKind
        selectedCategory = categoryId
        selectedSeries = null
        episodes = emptyList()
        loading = true
        error = null
        scope.launch {
            try {
                val api = XtreamApi(profile)
                val result = withContext(Dispatchers.IO) {
                    when (libraryKind) {
                        LibraryKind.LIVE -> api.liveCategories() to api.liveStreams(categoryId)
                        LibraryKind.MOVIES -> api.movieCategories() to api.movies(categoryId)
                        LibraryKind.SERIES -> api.seriesCategories() to api.series(categoryId)
                    }
                }
                categories = result.first
                items = result.second
            } catch (e: Exception) {
                error = e.message ?: "Could not load provider library"
                categories = emptyList()
                items = emptyList()
            } finally { loading = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "IPTV providers" else selected!!.name) },
                navigationIcon = { TextButton(onClick = { if (selected != null) { selected = null; items = emptyList(); categories = emptyList() } else onBack() }) { Text("Back") } },
                actions = {
                    if (selected == null) IconButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "Add provider") }
                    else TextButton(onClick = { selected = null; items = emptyList(); categories = emptyList() }) { Text("Providers") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }

            if (selected == null) {
                Text("Add multiple M3U playlists or Xtream Codes accounts. Credentials are stored encrypted on this device.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                if (providers.isEmpty()) Text("No providers added yet. Tap + to add one.", modifier = Modifier.padding(top = 24.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(providers, key = { it.id }) { profile ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable {
                                    if (profile.type == ProviderType.XTREAM) reloadLibrary(profile, LibraryKind.LIVE)
                                    else {
                                        loading = true
                                        scope.launch {
                                            try {
                                                val result = withContext(Dispatchers.IO) { M3ULoader.parseM3U8FromUrl(profile.url) }
                                                when (result) {
                                                    is Result.Success -> {
                                                        if (result.data.isEmpty()) {
                                                            error = "The playlist loaded but contains no playable entries."
                                                        } else {
                                                            selected = profile
                                                            items = result.data.map { XtreamItem(it.id, it.name, it.url, it.group, LibraryKind.LIVE) }
                                                            categories = result.data.map { it.group }.filter { it.isNotBlank() }.distinct().map { XtreamCategory(it, it) }
                                                            selectedCategory = null
                                                            kind = LibraryKind.LIVE
                                                            error = null
                                                        }
                                                    }
                                                    is Result.Error -> error = result.message ?: result.exception.message ?: "Could not load M3U playlist"
                                                }
                                            } catch (e: Exception) {
                                                error = e.message ?: "Could not load M3U playlist"
                                            } finally {
                                                loading = false
                                            }
                                        }
                                    }
                                }) {
                                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                                    Text(if (profile.type == ProviderType.XTREAM) "Xtream Codes" else "M3U playlist", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    providers = providers.filterNot { it.id == profile.id }
                                    store.save(providers)
                                }) { Icon(Icons.Outlined.Delete, "Delete provider") }
                            }
                        }
                    }
                }
            } else {
                if (selected!!.type == ProviderType.XTREAM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        listOf(LibraryKind.LIVE, LibraryKind.MOVIES, LibraryKind.SERIES).forEach { library ->
                            FilterChip(selected = kind == library, onClick = { reloadLibrary(selected!!, library) }, label = { Text(when(library) { LibraryKind.LIVE -> "Live"; LibraryKind.MOVIES -> "Movies"; LibraryKind.SERIES -> "Series" }) })
                        }
                    }
                }
                if (categories.isNotEmpty()) {
                    Text("Categories", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.heightIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            AssistChip(onClick = { if (selected!!.type == ProviderType.M3U) selectedCategory = null else reloadLibrary(selected!!, kind, null) }, label = { Text("All categories") })
                        }
                        items(categories, key = { it.id }) { category ->
                            AssistChip(onClick = { if (selected!!.type == ProviderType.M3U) selectedCategory = category.id else reloadLibrary(selected!!, kind, category.id) }, label = { Text(category.name) })
                        }
                    }
                }
                if (selectedSeries != null) {
                    Text("Episodes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn {
                        items(episodes, key = { it.id }) { episode ->
                            ListItem(headlineContent = { Text("S${episode.seasonNumber} E${episode.episodeNumber} · ${episode.title}") }, modifier = Modifier.clickable { onPlayUrl(episode.streamUrl, episode.title) })
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(items.filter { selected!!.type != ProviderType.M3U || selectedCategory == null || it.categoryId == selectedCategory }, key = { "${it.id}:${it.streamUrl}" }) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = { if (item.categoryId.isNotBlank()) Text(categories.firstOrNull { it.id == item.categoryId }?.name.orEmpty()) },
                                trailingContent = { Icon(Icons.Outlined.PlayArrow, "Play") },
                                modifier = Modifier.clickable {
                                    if (kind == LibraryKind.SERIES && selected!!.type == ProviderType.XTREAM) {
                                        loading = true
                                        scope.launch {
                                            try {
                                                episodes = withContext(Dispatchers.IO) { XtreamApi(selected!!).episodes(item.id) }
                                                selectedSeries = item
                                            } catch (e: Exception) { error = e.message ?: "Could not load episodes" }
                                            finally { loading = false }
                                        }
                                    } else onPlayUrl(item.streamUrl, item.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add provider") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(providerName, { providerName = it }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == ProviderType.XTREAM, onClick = { type = ProviderType.XTREAM }, label = { Text("Xtream") })
                        FilterChip(selected = type == ProviderType.M3U, onClick = { type = ProviderType.M3U }, label = { Text("M3U URL") })
                    }
                    OutlinedTextField(providerUrl, { providerUrl = it }, label = { Text(if (type == ProviderType.XTREAM) "Server URL" else "M3U playlist URL") })
                    if (type == ProviderType.XTREAM) {
                        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = providerName.isNotBlank() && providerUrl.startsWith("http"), onClick = {
                    val profile = ProviderProfile(UUID.randomUUID().toString(), providerName.trim(), type, providerUrl.trim(), username.trim(), password)
                    showAdd = false
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            if (type == ProviderType.XTREAM && !withContext(Dispatchers.IO) { XtreamApi(profile).validateLogin() }) throw IllegalArgumentException("Login failed. Check server URL and credentials.")
                            if (type == ProviderType.M3U) {
                                val result = withContext(Dispatchers.IO) { M3ULoader.parseM3U8FromUrl(profile.url) }
                                if (result is Result.Error) throw IllegalArgumentException(result.message ?: result.exception.message ?: "Could not load M3U playlist")
                            }
                            providers = providers + profile
                            store.save(providers)
                            providerName = ""; providerUrl = ""; username = ""; password = ""
                        } catch (e: Exception) { error = e.message ?: "Could not add provider" }
                        finally { loading = false }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}
