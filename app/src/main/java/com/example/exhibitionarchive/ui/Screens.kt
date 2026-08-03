package com.example.exhibitionarchive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.example.exhibitionarchive.data.*
import com.example.exhibitionarchive.util.ExhibitionImportInfo
import com.example.exhibitionarchive.util.SearchResultItem
import com.example.exhibitionarchive.util.toImportInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun ExhibitionArchiveRoot(vm: AppViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val route = nav.currentBackStackEntryAsState().value?.destination?.route
            if (route in listOf("home", "calendar", "archive", "settings")) {
                NavigationBar {
                    listOf(
                        Triple("home", Icons.Default.Home, "홈"),
                        Triple("calendar", Icons.Default.CalendarMonth, "달력"),
                        Triple("archive", Icons.Default.CollectionsBookmark, "아카이브"),
                        Triple("settings", Icons.Default.Settings, "설정")
                    ).forEach { (target, icon, label) ->
                        NavigationBarItem(route == target, { nav.navigate(target) { launchSingleTop = true; popUpTo("home") { saveState = true }; restoreState = true } }, { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        }
    ) { padding ->
        NavHost(nav, "home", Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm, { nav.navigate("exhibition/new") }, { nav.navigate("exhibition/$it") }, { nav.navigate("search") }, { nav.navigate("exhibition/$it/artwork/new") }) }
            composable("calendar") { CalendarScreen(vm) { nav.navigate("exhibition/$it") } }
            composable("archive") { ArchiveScreen(vm) { nav.navigate("exhibition/$it") } }
            composable("settings") { SettingsScreen(vm) }
            composable("search") { SearchScreen(vm) { nav.navigate("exhibition/$it") } }
            composable("exhibition/new") { ExhibitionCreateScreen(vm, { nav.popBackStack() }) { id -> nav.navigate("exhibition/$id") { popUpTo("exhibition/new") { inclusive = true } } } }
            composable("exhibition/{id}", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                ExhibitionDetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate("exhibition/$id/edit") }, { nav.navigate("exhibition/$id/artwork/new") }, { nav.navigate("artwork/$it") })
            }
            composable("exhibition/{id}/edit", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ExhibitionEditScreen(vm, entry.arguments!!.getLong("id"), { nav.popBackStack() }) { nav.popBackStack("home", false) }
            }
            composable("exhibition/{id}/artwork/new", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ArtworkCreateScreen(vm, entry.arguments!!.getLong("id"), { nav.popBackStack() }) { artworkId -> nav.navigate("artwork/$artworkId") { popUpTo("exhibition/{id}/artwork/new") { inclusive = true } } }
            }
            composable("artwork/{id}", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments!!.getLong("id")
                ArtworkDetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate("artwork/$id/edit") }) { nav.popBackStack() }
            }
            composable("artwork/{id}/edit", listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ArtworkEditScreen(vm, entry.arguments!!.getLong("id")) { nav.popBackStack() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: AppViewModel, onCreate: () -> Unit, onOpen: (Long) -> Unit, onSearch: () -> Unit, onAddArtwork: (Long) -> Unit) {
    val exhibitions by vm.exhibitions.collectAsStateWithLifecycle(); val visits by vm.visits.collectAsStateWithLifecycle()
    var showFabMenu by remember { mutableStateOf(false) }; var showArtworkChoice by remember { mutableStateOf(false) }; var showExhibitionPicker by remember { mutableStateOf(false) }
    if (showArtworkChoice) AlertDialog(onDismissRequest = { showArtworkChoice = false }, title = { Text("작품 추가") }, text = { Text("어떤 전시에 작품을 추가할까요?") }, confirmButton = { TextButton({ showArtworkChoice = false; showExhibitionPicker = true }) { Text("기존 전시에 추가") } }, dismissButton = { TextButton({ showArtworkChoice = false; onCreate() }) { Text("새 전시 만들면서 추가") } })
    if (showExhibitionPicker) ExhibitionPickerDialog(vm, { showExhibitionPicker = false }) { id -> showExhibitionPicker = false; onAddArtwork(id) }
    Scaffold(topBar = { TopAppBar({ Text("전시기록") }, actions = { IconButton(onSearch) { Icon(Icons.Default.Search, "검색") } }) }, floatingActionButton = { Box { FloatingActionButton({ showFabMenu = true }) { Icon(Icons.Default.Add, "추가") }; DropdownMenu(showFabMenu, { showFabMenu = false }) { DropdownMenuItem({ Text("전시 추가") }, { showFabMenu = false; onCreate() }); DropdownMenuItem({ Text("작품 추가") }, { showFabMenu = false; showArtworkChoice = true }) } } }) { p ->
        LazyColumn(contentPadding = p, modifier = Modifier.fillMaxSize()) {
            item { Column(Modifier.padding(20.dp)) { Text("이번 달 ${visits.count { it.visitedAt.startsWith(YearMonth.now().toString()) }}회 관람", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("포스터와 감상을 날짜별로 쌓아보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (exhibitions.isEmpty()) item { EmptyState(onCreate) } else { item { SectionTitle("최근 전시") }; items(exhibitions, key = { it.id }) { ExhibitionRow(it, onOpen) } }
        }
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
@Composable private fun EmptyState(onAction: () -> Unit) { Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Museum, null, Modifier.size(56.dp)); Text("아직 기록한 전시가 없습니다.", fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); Button(onAction) { Text("첫 전시 추가") } } }

@Composable
private fun ExhibitionRow(item: ExhibitionEntity, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onOpen(item.id) }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Poster(item.posterPath, Modifier.size(76.dp, 104.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); item.venueName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
}

@Composable
private fun Poster(path: String?, modifier: Modifier) {
    if (path.isNullOrBlank()) Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null) }
    else AsyncImage(if (path.startsWith("http://") || path.startsWith("https://")) path else File(path), null, modifier.clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionCreateScreen(vm: AppViewModel, onBack: () -> Unit, onDone: (Long) -> Unit) {
    var title by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }; var oneLine by remember { mutableStateOf("") }; var detail by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now()) }; var poster by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }; var startDate by remember { mutableStateOf("") }; var endDate by remember { mutableStateOf("") }; var officialUrl by remember { mutableStateOf("") }; var importUrl by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }; var suppressSuggestions by remember { mutableStateOf(false) }; var dateDialog by remember { mutableStateOf(false) }
    var pendingArtworks by remember { mutableStateOf<List<PendingArtwork>>(emptyList()) }; var artworkDialog by remember { mutableStateOf(false) }
    val saving by vm.saving.collectAsStateWithLifecycle(); val scope = rememberCoroutineScope()
    val latestPoster by rememberUpdatedState(poster); val latestArtworks by rememberUpdatedState(pendingArtworks); val latestSaving by rememberUpdatedState(saving)
    DisposableEffect(Unit) { onDispose { if (!latestSaving) { vm.fileStore.deleteManagedFile(latestPoster); latestArtworks.forEach { vm.fileStore.deleteManagedFile(it.imagePath) } } } }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { runCatching { vm.fileStore.copyImage(it) }.onSuccess { path -> vm.fileStore.deleteManagedFile(poster); poster = path }.onFailure { e -> vm.showMessage(e.message ?: "이미지를 가져오지 못했습니다.") } } } }
    fun applyImport(info: ExhibitionImportInfo) {
        info.title?.let { suppressSuggestions = true; title = it }; info.venueName?.let { venue = it }; info.description?.let { description = it }; info.startDate?.let { startDate = it }; info.endDate?.let { endDate = it }; info.officialUrl?.let { officialUrl = it }
        info.posterImageUrl?.let { url -> scope.launch { vm.fileStore.downloadImage(url)?.let { path -> vm.fileStore.deleteManagedFile(poster); poster = path } } }
        val existing = pendingArtworks.map { it.importKey() }.toMutableSet()
        val imported = info.artworks.mapNotNull { artwork ->
            PendingArtwork(
                title = artwork.title,
                artist = artwork.artistName,
                review = null,
                imagePath = null,
                externalImageUrl = artwork.imageUrl,
                productionYear = artwork.productionYear,
                medium = artwork.medium,
                dimensions = artwork.dimensions,
                description = artwork.description
            ).takeIf { existing.add(it.importKey()) }
        }
        if (imported.isNotEmpty()) {
            pendingArtworks = pendingArtworks + imported
            vm.showMessage("공개된 작품 ${imported.size}개를 함께 가져왔습니다.")
        }
    }
    LaunchedEffect(title) {
        suggestions = emptyList()
        if (suppressSuggestions) suppressSuggestions = false
        else if (title.trim().length >= 2) { delay(600); vm.searchOnline(title) { suggestions = it.take(5) } }
    }
    if (artworkDialog) ArtworkQuickAddDialog(vm, { artworkDialog = false }) { pendingArtworks = pendingArtworks + it }
    Scaffold(topBar = { TopAppBar({ Text("전시 추가") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }) }) { p ->
        LazyColumn(Modifier.padding(p).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("온라인에서 가져오기", style = MaterialTheme.typography.titleMedium) }
            item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(importUrl, { importUrl = it }, Modifier.weight(1f), label = { Text("전시 링크") }); Button({ vm.importExhibitionInfo(importUrl, ::applyImport) }) { Text("가져오기") } } }
            item { Box(Modifier.fillMaxWidth().height(220.dp).clickable { imageLauncher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(poster, Modifier.fillMaxSize()); Text(if (poster == null) "포스터 선택" else "포스터 변경", Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)).padding(8.dp)) } }
            item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("전시명 *") }, isError = title.isBlank()) }
            if (suggestions.isNotEmpty()) item { Column { Text("검색된 전시", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); suggestions.forEach { result -> ListItem({ Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(result.description, maxLines = 2, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.clickable { suggestions = emptyList(); applyImport(result.toImportInfo()); if (result.link.isNotBlank()) vm.importExhibitionInfo(result.link, ::applyImport) }) } } }
            item { OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("전시 설명") }, minLines = 2) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(startDate, { startDate = it }, Modifier.weight(1f), label = { Text("전시 시작일") }); OutlinedTextField(endDate, { endDate = it }, Modifier.weight(1f), label = { Text("전시 종료일") }) } }
            item { OutlinedButton({ dateDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("관람일  $date") } }
            item { OutlinedTextField(venue, { venue = it }, Modifier.fillMaxWidth(), label = { Text("장소") }) }
            item { OutlinedTextField(officialUrl, { officialUrl = it }, Modifier.fillMaxWidth(), label = { Text("공식 링크") }) }
            item { OutlinedTextField(oneLine, { oneLine = it }, Modifier.fillMaxWidth(), label = { Text("한줄평") }) }
            item { OutlinedTextField(detail, { detail = it }, Modifier.fillMaxWidth(), label = { Text("상세 감상") }, minLines = 4) }
            item { OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("태그, 쉼표로 구분") }) }
            item { HorizontalDivider() }
            item { OutlinedButton({ artworkDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("작품도 함께 추가") } }
            if (pendingArtworks.isNotEmpty()) { item { Text("추가할 작품 ${pendingArtworks.size}개", style = MaterialTheme.typography.titleSmall) }; items(pendingArtworks) { artwork -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Poster(artwork.imagePath ?: artwork.externalImageUrl, Modifier.size(52.dp)); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(artwork.title, fontWeight = FontWeight.SemiBold); artwork.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }; IconButton({ vm.fileStore.deleteManagedFile(artwork.imagePath); pendingArtworks = pendingArtworks - artwork }) { Icon(Icons.Default.Close, "작품 제거") } } } }
            item { Button({ vm.createExhibition(title, date, poster, venue, oneLine, detail, tags, description, startDate.ifBlank { null }, endDate.ifBlank { null }, officialUrl.ifBlank { null }, pendingArtworks, onDone) }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && !saving) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("저장") } }
        }
    }
    if (dateDialog) AppDatePicker(date, { dateDialog = false }) { date = it; dateDialog = false }
}

@Composable
private fun ArtworkQuickAddDialog(vm: AppViewModel, onDismiss: () -> Unit, onAdd: (PendingArtwork) -> Unit) {
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }; var image by remember { mutableStateOf<String?>(null) }; val scope = rememberCoroutineScope()
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { runCatching { vm.fileStore.copyImage(it) }.onSuccess { path -> vm.fileStore.deleteManagedFile(image); image = path }.onFailure { e -> vm.showMessage(e.message ?: "이미지를 가져오지 못했습니다.") } } } }
    Dialog(onDismissRequest = { vm.fileStore.deleteManagedFile(image); onDismiss() }) { Surface(shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(20.dp).fillMaxWidth().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("작품 추가", style = MaterialTheme.typography.titleMedium); Box(Modifier.fillMaxWidth().height(160.dp).clickable { imageLauncher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(image, Modifier.fillMaxSize()); if (image == null) Text("작품 사진 선택") }; OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("작품명 *") }); OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(), label = { Text("작가") }); OutlinedTextField(review, { review = it }, Modifier.fillMaxWidth(), label = { Text("내 감상") }, minLines = 2); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ vm.fileStore.deleteManagedFile(image); onDismiss() }) { Text("취소") }; Button({ if (title.isNotBlank()) { onAdd(PendingArtwork(title.trim(), artist.trim().ifBlank { null }, review.trim().ifBlank { null }, image)); onDismiss() } }) { Text("추가") } } } } }
}

@Composable
private fun ExhibitionPickerDialog(vm: AppViewModel, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val exhibitions by vm.exhibitions.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) { Surface(shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(20.dp).fillMaxWidth()) { Text("전시 선택", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(12.dp)); Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { exhibitions.forEach { exhibition -> ListItem({ Text(exhibition.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = exhibition.venueName?.let { venue -> { Text(venue) } }, modifier = Modifier.clickable { onSelect(exhibition.id) }) }; if (exhibitions.isEmpty()) Text("등록된 전시가 없습니다.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; TextButton(onDismiss, Modifier.align(Alignment.End)) { Text("닫기") } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionEditScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onDeleted: () -> Unit) {
    val exhibition by vm.exhibition(id).collectAsStateWithLifecycle(null); val visits by vm.visitsFor(id).collectAsStateWithLifecycle(emptyList()); val tags by vm.tagsFor(id).collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    if (exhibition == null) { LoadingScreen(); return }
    ExhibitionFormScreen(vm, "전시 수정", exhibition, visits.firstOrNull(), tags, onBack, onDelete = { confirmDelete = true }) { title, date, poster, venue, oneLine, detail, tagText -> vm.updateExhibition(id, title, date, poster, venue, oneLine, detail, tagText, onBack) }
    if (confirmDelete) ConfirmDeleteDialog("전시를 삭제할까요?", "연결된 작품과 앱 내부 사진도 함께 삭제됩니다.", { confirmDelete = false }) { confirmDelete = false; vm.deleteExhibition(id, onDeleted) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionFormScreen(vm: AppViewModel, heading: String, exhibition: ExhibitionEntity?, visit: VisitEntity?, currentTags: List<TagEntity>, onBack: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String, LocalDate, String?, String, String, String, String) -> Unit) {
    var title by remember(exhibition) { mutableStateOf(exhibition?.title.orEmpty()) }; var venue by remember(exhibition) { mutableStateOf(exhibition?.venueName.orEmpty()) }
    var oneLine by remember(visit) { mutableStateOf(visit?.oneLineReview.orEmpty()) }; var detail by remember(visit) { mutableStateOf(visit?.detailedReview.orEmpty()) }; var tags by remember(currentTags) { mutableStateOf(currentTags.joinToString(", ") { it.name }) }
    var date by remember(visit) { mutableStateOf(runCatching { LocalDate.parse(visit?.visitedAt) }.getOrDefault(LocalDate.now())) }; var poster by remember(exhibition) { mutableStateOf(exhibition?.posterPath) }; var dateDialog by remember { mutableStateOf(false) }
    val saving by vm.saving.collectAsStateWithLifecycle(); val scope = rememberCoroutineScope()
    val latestPoster by rememberUpdatedState(poster); val latestSaving by rememberUpdatedState(saving)
    DisposableEffect(Unit) { onDispose { if (!latestSaving && latestPoster != exhibition?.posterPath) vm.fileStore.deleteManagedFile(latestPoster) } }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { runCatching { vm.fileStore.copyImage(it) }.onSuccess { path -> if (poster != exhibition?.posterPath) vm.fileStore.deleteManagedFile(poster); poster = path }.onFailure { e -> vm.showMessage(e.message ?: "이미지를 가져오지 못했습니다.") } } } }
    Scaffold(topBar = { TopAppBar({ Text(heading) }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }, actions = { onDelete?.let { IconButton(it) { Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error) } } }) }) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Box(Modifier.fillMaxWidth().height(220.dp).clickable { imageLauncher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(poster, Modifier.fillMaxSize()); Text(if (poster == null) "포스터 선택" else "포스터 변경", modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)).padding(8.dp)) } }
            item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("전시명 *") }, isError = title.isBlank()) }
            item { OutlinedButton({ dateDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("관람일  $date") } }
            item { OutlinedTextField(venue, { venue = it }, Modifier.fillMaxWidth(), label = { Text("장소") }) }
            item { OutlinedTextField(oneLine, { oneLine = it }, Modifier.fillMaxWidth(), label = { Text("한줄평") }) }
            item { OutlinedTextField(detail, { detail = it }, Modifier.fillMaxWidth(), label = { Text("상세 감상") }, minLines = 4) }
            item { OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("태그, 쉼표로 구분") }) }
            item { Button({ onSave(title, date, poster, venue, oneLine, detail, tags) }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && !saving) { if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("저장") } }
        }
    }
    if (dateDialog) AppDatePicker(date, { dateDialog = false }) { date = it; dateDialog = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDatePicker(date: LocalDate, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismiss, confirmButton = { TextButton({ state.selectedDateMillis?.let { onSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }) { Text("선택") } }, dismissButton = { TextButton(onDismiss) { Text("취소") } }) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onAddArtwork: () -> Unit, onOpenArtwork: (Long) -> Unit) {
    val exhibition by vm.exhibition(id).collectAsStateWithLifecycle(null); val visits by vm.visitsFor(id).collectAsStateWithLifecycle(emptyList()); val artworks by vm.artworksFor(id).collectAsStateWithLifecycle(emptyList()); val tags by vm.tagsFor(id).collectAsStateWithLifecycle(emptyList()); val audio by vm.audioFor(id).collectAsStateWithLifecycle(emptyList())
    Scaffold(topBar = { TopAppBar({ Text(exhibition?.title ?: "전시") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }, actions = { IconButton(onEdit) { Icon(Icons.Default.Edit, "수정") } }) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddArtwork, icon = { Icon(Icons.Default.AddPhotoAlternate, null) }, text = { Text("작품 추가") }) }) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(bottom = 100.dp)) {
            item { Poster(exhibition?.posterPath, Modifier.fillMaxWidth().height(300.dp)) }
            item { Column(Modifier.padding(20.dp)) { Text(exhibition?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); exhibition?.venueName?.let { Text(it) }; visits.firstOrNull()?.let { v -> Spacer(Modifier.height(12.dp)); Text(v.visitedAt, color = MaterialTheme.colorScheme.primary); v.oneLineReview?.let { Text("“$it”", style = MaterialTheme.typography.titleMedium) }; v.detailedReview?.let { Text(it, Modifier.padding(top = 8.dp)) } }; if (tags.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) { items(tags) { AssistChip({}, { Text("#${it.name}") }) } } } }
            item { VoiceMemoSection(vm, audio, exhibitionId = id) }
            item { SectionTitle("작품 ${artworks.size}") }
            if (artworks.isEmpty()) item { Text("아직 등록한 작품이 없습니다.", Modifier.padding(horizontal = 20.dp)) }
            items(artworks, key = { it.artwork.id }) { card -> ArtworkRow(card, onOpenArtwork) }
        }
    }
}

@Composable
private fun ArtworkRow(card: ArtworkCard, onOpen: (Long) -> Unit) { val image = card.images.firstOrNull { it.isRepresentative } ?: card.images.firstOrNull(); Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onOpen(card.artwork.id) }) { Row(Modifier.padding(12.dp)) { Poster(image?.localPath ?: image?.externalUrl, Modifier.size(90.dp)); Spacer(Modifier.width(12.dp)); Column { Text(card.artwork.title, fontWeight = FontWeight.Bold); card.artist?.let { Text(it.name) }; card.artwork.personalReview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkCreateScreen(vm: AppViewModel, exhibitionId: Long, onBack: () -> Unit, onDone: (Long) -> Unit) {
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }; var image by remember { mutableStateOf<String?>(null) }; val saving by vm.saving.collectAsStateWithLifecycle(); val scope = rememberCoroutineScope()
    val latestImage by rememberUpdatedState(image); val latestSaving by rememberUpdatedState(saving)
    DisposableEffect(Unit) { onDispose { if (!latestSaving) vm.fileStore.deleteManagedFile(latestImage) } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { runCatching { vm.fileStore.copyImage(it) }.onSuccess { path -> vm.fileStore.deleteManagedFile(image); image = path }.onFailure { e -> vm.showMessage(e.message ?: "이미지를 가져오지 못했습니다.") } } } }
    Scaffold(topBar = { TopAppBar({ Text("작품 빠른 추가") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.Close, "닫기") } }) }) { p -> LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Box(Modifier.fillMaxWidth().height(240.dp).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(image, Modifier.fillMaxSize()); if (image == null) Text("작품 사진 선택") } }; item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("작품명 *") }, isError = title.isBlank()) }; item { OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(), label = { Text("작가") }) }; item { OutlinedTextField(review, { review = it }, Modifier.fillMaxWidth(), label = { Text("내 감상") }, minLines = 3) }; item { Button({ vm.addArtwork(exhibitionId, title, artist, review, image, onDone) }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && !saving) { Text("저장") } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val card by vm.artwork(id).collectAsStateWithLifecycle(null); val audio by vm.audioForArtwork(id).collectAsStateWithLifecycle(emptyList()); var confirmDelete by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> uris.forEach { uri -> scope.launch { runCatching { vm.fileStore.copyImage(uri) }.onSuccess { vm.addArtworkImage(id, it) }.onFailure { e -> vm.showMessage(e.message ?: "이미지를 가져오지 못했습니다.") } } } }
    val item = card
    if (item == null) { LoadingScreen(); return }
    Scaffold(topBar = { TopAppBar({ Text(item.artwork.title) }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }, actions = { IconButton(onEdit) { Icon(Icons.Default.Edit, "수정") }; IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "삭제", tint = MaterialTheme.colorScheme.error) } }) }) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { if (item.images.isEmpty()) Poster(null, Modifier.fillMaxWidth().height(260.dp)) else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(item.images, key = { it.id }) { image -> Box { Poster(image.localPath ?: image.externalUrl, Modifier.size(240.dp)); IconButton({ vm.deleteArtworkImage(image.id) }, Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = .8f))) { Icon(Icons.Default.Delete, "사진 삭제") } } } } }
            item { OutlinedButton({ launcher.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("사진 추가") } }
            item { Text(item.artwork.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); item.artist?.let { Text(it.name, style = MaterialTheme.typography.titleMedium) } }
            item { ArtworkMetadata(item.artwork) }
            item.artwork.description?.let { value -> item { Text("작품 설명", fontWeight = FontWeight.Bold); Text(value) } }
            item.artwork.personalReview?.let { value -> item { Text("내 감상", fontWeight = FontWeight.Bold); Text(value) } }
            item { VoiceMemoSection(vm, audio, artworkId = id) }
        }
    }
    if (confirmDelete) ConfirmDeleteDialog("작품을 삭제할까요?", "등록한 사진도 앱 내부에서 함께 삭제됩니다.", { confirmDelete = false }) { confirmDelete = false; vm.deleteArtwork(id, onDeleted) }
}

@Composable
private fun VoiceMemoSection(vm: AppViewModel, records: List<AudioRecordEntity>, exhibitionId: Long? = null, artworkId: Long? = null) {
    val context = LocalContext.current
    var recordingPath by remember(exhibitionId, artworkId) { mutableStateOf<String?>(null) }
    var recordingStartedAt by remember(exhibitionId, artworkId) { mutableLongStateOf(0L) }
    var playingId by remember(exhibitionId, artworkId) { mutableStateOf<Long?>(null) }
    var player by remember(exhibitionId, artworkId) { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayback() {
        player?.let { current -> runCatching { current.stop() }; current.release() }
        player = null
        playingId = null
    }
    fun beginRecording() {
        stopPlayback()
        vm.startAudioRecording()?.let { path ->
            recordingPath = path
            recordingStartedAt = SystemClock.elapsedRealtime()
        }
    }
    fun play(record: AudioRecordEntity) {
        if (playingId == record.id) { stopPlayback(); return }
        val path = record.filePath
        if (path.isNullOrBlank() || !File(path).exists()) { vm.showMessage("음성 파일을 찾을 수 없습니다."); return }
        stopPlayback()
        val next = MediaPlayer()
        runCatching {
            next.setDataSource(path)
            next.setOnCompletionListener { completed ->
                completed.release()
                if (player === completed) player = null
                playingId = null
            }
            next.prepare()
            next.start()
            player = next
            playingId = record.id
        }.onFailure {
            next.release()
            vm.showMessage("음성 메모를 재생하지 못했습니다.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording() else vm.showMessage("음성 메모를 녹음하려면 마이크 권한이 필요합니다.")
    }
    val latestRecordingPath by rememberUpdatedState(recordingPath)
    val latestPlayer by rememberUpdatedState(player)
    DisposableEffect(exhibitionId, artworkId) {
        onDispose {
            latestPlayer?.release()
            latestRecordingPath?.let(vm::cancelAudioRecording)
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("음성 메모", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (recordingPath == null) {
                FilledTonalButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("녹음") }
            } else {
                Button(onClick = {
                    val path = recordingPath ?: return@Button
                    val duration = SystemClock.elapsedRealtime() - recordingStartedAt
                    vm.finishAudioRecording(exhibitionId, artworkId, path, duration)
                    recordingPath = null
                }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("저장") }
            }
        }
        if (recordingPath != null) Text("녹음 중입니다. 저장을 누르면 이 기록에 첨부됩니다.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (records.isEmpty() && recordingPath == null) Text("아직 녹음한 메모가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        records.forEach { record ->
            ListItem(
                headlineContent = { Text(record.title ?: "음성 메모") },
                supportingContent = { record.durationMillis?.let { Text(formatDuration(it)) } },
                leadingContent = { IconButton({ play(record) }, enabled = !record.filePath.isNullOrBlank()) { Icon(if (playingId == record.id) Icons.Default.Stop else Icons.Default.PlayArrow, if (playingId == record.id) "재생 중지" else "재생") } },
                trailingContent = { IconButton({ if (playingId == record.id) stopPlayback(); vm.deleteAudio(record.id) }) { Icon(Icons.Default.Delete, "음성 메모 삭제") } }
            )
        }
    }
}

@Composable private fun ArtworkMetadata(item: ArtworkEntity) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf("제작연도" to item.productionYear, "재료" to item.medium, "크기" to item.dimensions, "전시 섹션" to item.sectionName).forEach { (label, value) -> value?.let { Row { Text("$label  ", fontWeight = FontWeight.Bold); Text(it) } } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkEditScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val card by vm.artwork(id).collectAsStateWithLifecycle(null); val saving by vm.saving.collectAsStateWithLifecycle(); val current = card ?: return LoadingScreen()
    var title by remember(current.artwork) { mutableStateOf(current.artwork.title) }; var artist by remember(current.artist) { mutableStateOf(current.artist?.name.orEmpty()) }; var year by remember(current.artwork) { mutableStateOf(current.artwork.productionYear.orEmpty()) }; var medium by remember(current.artwork) { mutableStateOf(current.artwork.medium.orEmpty()) }; var dimensions by remember(current.artwork) { mutableStateOf(current.artwork.dimensions.orEmpty()) }; var section by remember(current.artwork) { mutableStateOf(current.artwork.sectionName.orEmpty()) }; var description by remember(current.artwork) { mutableStateOf(current.artwork.description.orEmpty()) }; var review by remember(current.artwork) { mutableStateOf(current.artwork.personalReview.orEmpty()) }
    Scaffold(topBar = { TopAppBar({ Text("작품 수정") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }) }) { p -> LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("작품명 *") }, isError = title.isBlank()) }; item { OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth(), label = { Text("작가") }) }; item { OutlinedTextField(year, { year = it }, Modifier.fillMaxWidth(), label = { Text("제작연도") }) }; item { OutlinedTextField(medium, { medium = it }, Modifier.fillMaxWidth(), label = { Text("재료") }) }; item { OutlinedTextField(dimensions, { dimensions = it }, Modifier.fillMaxWidth(), label = { Text("크기") }) }; item { OutlinedTextField(section, { section = it }, Modifier.fillMaxWidth(), label = { Text("전시 섹션") }) }; item { OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("작품 설명") }, minLines = 3) }; item { OutlinedTextField(review, { review = it }, Modifier.fillMaxWidth(), label = { Text("내 감상") }, minLines = 4) }; item { Button({ vm.updateArtwork(id, title, artist, year, medium, dimensions, section, description, review, onBack) }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && !saving) { Text("저장") } } } }
}

@Composable private fun LoadingScreen() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable private fun ConfirmDeleteDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismiss, { TextButton(onConfirm) { Text("삭제", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onDismiss) { Text("취소") } }, title = { Text(title) }, text = { Text(body) }) }

private fun PendingArtwork.importKey() = "${artist.orEmpty().trim().lowercase()}|${title.trim().lowercase()}"
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0) / 1_000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

private const val CALENDAR_CENTER_PAGE = 5000
private const val CALENDAR_PAGE_COUNT = 10000

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CalendarScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val visits by vm.visits.collectAsStateWithLifecycle(); val exhibitions by vm.exhibitions.collectAsStateWithLifecycle(); val pagerState = rememberPagerState(initialPage = CALENDAR_CENTER_PAGE) { CALENDAR_PAGE_COUNT }; val scope = rememberCoroutineScope(); val month = YearMonth.now().plusMonths((pagerState.currentPage - CALENDAR_CENTER_PAGE).toLong())
    Scaffold(topBar = { TopAppBar({ Text("${month.year}년 ${month.monthValue}월") }, navigationIcon = { IconButton({ scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) { Icon(Icons.Default.ChevronLeft, "이전 달") } }, actions = { IconButton({ scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) { Icon(Icons.Default.ChevronRight, "다음 달") } }) }) { p -> HorizontalPager(pagerState, Modifier.padding(p).fillMaxSize()) { page -> val pageMonth = YearMonth.now().plusMonths((page - CALENDAR_CENTER_PAGE).toLong()); val first = pageMonth.atDay(1); val cells = List(first.dayOfWeek.value % 7) { null } + (1..pageMonth.lengthOfMonth()).map { pageMonth.atDay(it) }; LazyVerticalGrid(GridCells.Fixed(7), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) { gridItems(cells) { date -> if (date == null) Spacer(Modifier.aspectRatio(.72f)) else { val dayVisits = visits.filter { it.visitedAt == date.toString() }; val ex = dayVisits.firstOrNull()?.let { v -> exhibitions.firstOrNull { it.id == v.exhibitionId } }; Card(Modifier.padding(2.dp).aspectRatio(.72f).clickable(enabled = ex != null) { ex?.let { onOpen(it.id) } }) { Column { Text(date.dayOfMonth.toString(), Modifier.padding(5.dp), style = MaterialTheme.typography.labelSmall); ex?.let { Poster(it.posterPath, Modifier.fillMaxWidth().weight(1f)); if (dayVisits.size > 1) Text("+${dayVisits.size - 1}", Modifier.padding(2.dp)) } } } } } } } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ArchiveScreen(vm: AppViewModel, onOpen: (Long) -> Unit) { val exhibitions by vm.exhibitions.collectAsStateWithLifecycle(); val artists by vm.artists.collectAsStateWithLifecycle(); val tags by vm.tags.collectAsStateWithLifecycle(); val pagerState = rememberPagerState { 3 }; val scope = rememberCoroutineScope(); Scaffold(topBar = { TopAppBar({ Text("아카이브") }) }) { p -> Column(Modifier.padding(p)) { TabRow(pagerState.currentPage) { listOf("전시", "작가", "태그").forEachIndexed { i, text -> Tab(pagerState.currentPage == i, { scope.launch { pagerState.animateScrollToPage(i) } }, text = { Text(text) }) } }; HorizontalPager(pagerState, Modifier.fillMaxSize()) { page -> when (page) { 0 -> LazyColumn { items(exhibitions) { ExhibitionRow(it, onOpen) } }; 1 -> LazyColumn { items(artists) { ListItem({ Text(it.name) }, leadingContent = { Icon(Icons.Default.Person, null) }) } }; else -> LazyColumn { items(tags) { ListItem({ Text("#${it.name}") }, leadingContent = { Icon(Icons.Default.Tag, null) }) } } } } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(vm: AppViewModel, onOpen: (Long) -> Unit) { var q by remember { mutableStateOf("") }; val results by remember(q) { vm.searchExhibitions(q) }.collectAsStateWithLifecycle(emptyList()); Scaffold(topBar = { TopAppBar({ Text("검색") }) }) { p -> Column(Modifier.padding(p)) { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("전시명, 장소, 감상 검색") }); LazyColumn { items(results) { ExhibitionRow(it, onOpen) } } } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val scope = rememberCoroutineScope(); var status by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { scope.launch { runCatching { vm.exportBackup(it) }.onSuccess { status = "백업 완료" }.onFailure { e -> status = e.message ?: "백업 실패" } } } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { runCatching { vm.importBackup(it) }.onSuccess { status = "복원 완료" }.onFailure { e -> status = e.message ?: "복원 실패" } } } }
    Scaffold(topBar = { TopAppBar({ Text("설정") }) }) { p -> Column(Modifier.padding(p).padding(20.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("데이터", style = MaterialTheme.typography.titleLarge); Button({ export.launch("exhibition_backup_${LocalDate.now()}.zip") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Text(" 백업 파일 만들기") }; OutlinedButton({ import.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Text(" 백업에서 복원") }; if (status.isNotBlank()) Text(status); HorizontalDivider(); Text("전시명 자동 검색", style = MaterialTheme.typography.titleLarge); Text("아트맵·네오룩·아트바바·국립현대미술관·대림미술관·리움미술관·서울시립미술관의 공개 전시 목록을 주기적으로 갱신해 검색합니다. 별도 API 키는 필요하지 않습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(); Text("개인 관람 기록·감상·사진은 기기에만 저장됩니다. 링크 가져오기와 공개 전시 목록 다운로드에만 인터넷을 사용하며, 입력한 검색어는 서버로 보내지 않습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}
