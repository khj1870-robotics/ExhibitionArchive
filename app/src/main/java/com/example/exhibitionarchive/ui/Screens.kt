package com.example.exhibitionarchive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.example.exhibitionarchive.data.*
import com.example.exhibitionarchive.util.AudioRecorder
import com.example.exhibitionarchive.util.ExhibitionImportInfo
import com.example.exhibitionarchive.util.SearchResultItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun ExhibitionArchiveRoot(vm: AppViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
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
                    ).forEach { (r, icon, label) ->
                        NavigationBarItem(selected = route == r, onClick = { nav.navigate(r) { launchSingleTop = true; popUpTo("home") { saveState = true }; restoreState = true } }, icon = { Icon(icon, null) }, label = { Text(label) })
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm, { nav.navigate("createExhibition") }, { nav.navigate("exhibition/$it") }, { nav.navigate("search") }, { id -> nav.navigate("artworkCreate/$id") }) }
            composable("calendar") { CalendarScreen(vm) { nav.navigate("exhibition/$it") } }
            composable("archive") { ArchiveScreen(vm) { nav.navigate("exhibition/$it") } }
            composable("settings") { SettingsScreen(vm) }
            composable("search") { SearchScreen(vm) { nav.navigate("exhibition/$it") } }
            composable("createExhibition") { ExhibitionCreateScreen(vm, { nav.popBackStack() }) { id -> nav.navigate("exhibition/$id") { popUpTo("createExhibition") { inclusive = true } } } }
            composable("exhibition/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                val id = back.arguments?.getLong("id") ?: return@composable
                ExhibitionDetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate("artworkCreate/$id") })
            }
            composable("artworkCreate/{exhibitionId}", arguments = listOf(navArgument("exhibitionId") { type = NavType.LongType })) { back ->
                ArtworkCreateScreen(vm, back.arguments!!.getLong("exhibitionId")) { nav.popBackStack() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: AppViewModel, onCreate: () -> Unit, onOpen: (Long) -> Unit, onSearch: () -> Unit, onAddArtwork: (Long) -> Unit) {
    val exhibitions by vm.exhibitions.collectAsStateWithLifecycle()
    val visits by vm.visits.collectAsStateWithLifecycle()
    var showFabMenu by remember { mutableStateOf(false) }
    var showAddArtworkChoice by remember { mutableStateOf(false) }
    var showExhibitionPicker by remember { mutableStateOf(false) }
    if (showAddArtworkChoice) {
        AlertDialog(
            onDismissRequest = { showAddArtworkChoice = false },
            title = { Text("작품 추가") },
            text = { Text("어떤 전시에 작품을 추가할까요?") },
            confirmButton = { TextButton(onClick = { showAddArtworkChoice = false; showExhibitionPicker = true }) { Text("기존 전시에 추가") } },
            dismissButton = { TextButton(onClick = { showAddArtworkChoice = false; onCreate() }) { Text("새 전시 만들면서 추가") } }
        )
    }
    if (showExhibitionPicker) ExhibitionPickerDialog(vm, onDismiss = { showExhibitionPicker = false }, onSelect = { id -> showExhibitionPicker = false; onAddArtwork(id) })
    Scaffold(
        topBar = { TopAppBar(title = { Text("전시기록") }, actions = { IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "검색") } }) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = true }) { Icon(Icons.Default.Add, "추가") }
                DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                    DropdownMenuItem(text = { Text("전시 추가") }, onClick = { showFabMenu = false; onCreate() })
                    DropdownMenuItem(text = { Text("작품 추가") }, onClick = { showFabMenu = false; showAddArtworkChoice = true })
                }
            }
        }
    ) { p ->
        LazyColumn(contentPadding = p, modifier = Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(20.dp)) {
                    Text("이번 달 ${visits.count { it.visitedAt.startsWith(YearMonth.now().toString()) }}회 관람", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp)); Text("포스터와 감상을 날짜별로 쌓는다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (exhibitions.isEmpty()) item { EmptyState("아직 기록한 전시가 없습니다.", "첫 전시를 등록하세요.", onCreate) }
            else {
                item { Text("최근 전시", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
                items(exhibitions, key = { it.id }) { ExhibitionRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun ExhibitionRow(item: ExhibitionEntity, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onOpen(item.id) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(item.posterPath, Modifier.size(76.dp, 104.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                item.venueName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item.description?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun Poster(path: String?, modifier: Modifier) {
    if (path == null) Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null) }
    else AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(10.dp)))
}

@Composable
private fun EmptyState(title: String, body: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Museum, null, modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold); Text(body); Spacer(Modifier.height(16.dp)); Button(onClick = onAction) { Text("전시 추가") }
    }
}

private data class PendingArtwork(val title: String, val artist: String?, val review: String?, val imagePath: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionCreateScreen(vm: AppViewModel, onBack: () -> Unit, onDone: (Long) -> Unit) {
    var title by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }; var oneLine by remember { mutableStateOf("") }; var detail by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var imagePath by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }; var startDate by remember { mutableStateOf("") }; var endDate by remember { mutableStateOf("") }; var officialUrl by remember { mutableStateOf("") }
    var importUrl by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }; var suppressSuggest by remember { mutableStateOf(false) }
    var pendingArtworks by remember { mutableStateOf<List<PendingArtwork>>(emptyList()) }; var showArtworkDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { imagePath = vm.fileStore.copyImage(it) } } }
    fun applyImport(info: ExhibitionImportInfo) {
        info.title?.let { suppressSuggest = true; title = it }
        info.venueName?.let { venue = it }; info.description?.let { description = it }
        info.startDate?.let { startDate = it }; info.endDate?.let { endDate = it }; info.officialUrl?.let { officialUrl = it }
        info.posterImageUrl?.let { url -> scope.launch { vm.fileStore.downloadImage(url)?.let { imagePath = it } } }
    }
    LaunchedEffect(title) {
        if (suppressSuggest) { suppressSuggest = false; suggestions = emptyList() }
        else {
            suggestions = emptyList()
            if (title.trim().length >= 2 && vm.hasNaverApiKeys()) {
                delay(600)
                vm.searchOnline(title) { suggestions = it.take(3) }
            }
        }
    }
    if (showArtworkDialog) ArtworkQuickAddDialog(vm, onDismiss = { showArtworkDialog = false }, onAdd = { pendingArtworks = pendingArtworks + it })
    Scaffold(topBar = { TopAppBar(title = { Text("전시 추가") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("온라인에서 가져오기", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(importUrl, { importUrl = it }, label = { Text("전시 링크") }, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.importExhibitionInfo(importUrl, ::applyImport) }) { Text("가져오기") }
                }
            }
            item { HorizontalDivider() }
            item { Box(Modifier.fillMaxWidth().height(220.dp).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(imagePath, Modifier.fillMaxSize()); if (imagePath == null) Text("포스터 선택") } }
            item { OutlinedTextField(title, { title = it }, label = { Text("전시명 *") }, modifier = Modifier.fillMaxWidth()) }
            if (suggestions.isNotEmpty()) {
                item {
                    Column {
                        Text("혹시 이 전시를 말씀하시나요?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        suggestions.forEach { s ->
                            ListItem(
                                headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(s.description, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable { suggestions = emptyList(); vm.importExhibitionInfo(s.link, ::applyImport) }
                            )
                        }
                    }
                }
            }
            item { OutlinedTextField(description, { description = it }, label = { Text("전시 설명") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startDate, { startDate = it }, label = { Text("전시 시작일") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(endDate, { endDate = it }, label = { Text("전시 종료일") }, modifier = Modifier.weight(1f))
                }
            }
            item { OutlinedTextField(date, { date = it }, label = { Text("관람일 (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(venue, { venue = it }, label = { Text("장소") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(officialUrl, { officialUrl = it }, label = { Text("공식 링크") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(oneLine, { oneLine = it }, label = { Text("한줄평") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(detail, { detail = it }, label = { Text("상세 감상") }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(tags, { tags = it }, label = { Text("태그, 쉼표로 구분") }, modifier = Modifier.fillMaxWidth()) }
            item { HorizontalDivider() }
            item { OutlinedButton(onClick = { showArtworkDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("작품 추가") } }
            if (pendingArtworks.isNotEmpty()) {
                item { Text("추가된 작품 (${pendingArtworks.size})", style = MaterialTheme.typography.titleSmall) }
                items(pendingArtworks) { pa ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Poster(pa.imagePath, Modifier.size(48.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) { Text(pa.title, fontWeight = FontWeight.SemiBold); pa.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                        IconButton(onClick = { pendingArtworks = pendingArtworks - pa }) { Icon(Icons.Default.Close, "삭제") }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        runCatching { LocalDate.parse(date) }.onSuccess {
                            vm.createExhibition(title, it, imagePath, venue, oneLine, detail, tags, description, startDate.ifBlank { null }, endDate.ifBlank { null }, officialUrl.ifBlank { null }, onDone = { id ->
                                pendingArtworks.forEach { pa -> vm.addArtwork(id, pa.title, pa.artist, pa.review, pa.imagePath) {} }
                                onDone(id)
                            })
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("저장") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkQuickAddDialog(vm: AppViewModel, onDismiss: () -> Unit, onAdd: (PendingArtwork) -> Unit) {
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }; var imagePath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { imagePath = vm.fileStore.copyImage(it) } } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).fillMaxWidth().imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("작품 추가", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().height(160.dp).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(imagePath, Modifier.fillMaxSize()); if (imagePath == null) Text("작품 사진 선택") }
                OutlinedTextField(title, { title = it }, label = { Text("작품명 *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("작가") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(review, { review = it }, label = { Text("내 감상") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = { if (title.isNotBlank()) { onAdd(PendingArtwork(title.trim(), artist.trim().ifBlank { null }, review.trim().ifBlank { null }, imagePath)); onDismiss() } }) { Text("추가") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionPickerDialog(vm: AppViewModel, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val exhibitions by vm.exhibitions.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("전시 선택", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    exhibitions.forEach { ex ->
                        ListItem(
                            headlineContent = { Text(ex.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = ex.venueName?.let { { Text(it) } },
                            modifier = Modifier.clickable { onSelect(ex.id) }
                        )
                    }
                    if (exhibitions.isEmpty()) Text("등록된 전시가 없습니다.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onAddArtwork: () -> Unit) {
    val exhibition by vm.exhibition(id).collectAsStateWithLifecycle(initialValue = null)
    val visits by vm.visitsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val artworks by vm.artworksFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val tags by vm.tagsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    Scaffold(topBar = { TopAppBar(title = { Text(exhibition?.title ?: "전시") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddArtwork, icon = { Icon(Icons.Default.AddPhotoAlternate, null) }, text = { Text("작품 추가") }) }) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(bottom = 100.dp)) {
            item { Poster(exhibition?.posterPath, Modifier.fillMaxWidth().height(300.dp)) }
            item { Column(Modifier.padding(20.dp)) {
                Text(exhibition?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                exhibition?.venueName?.let { Text(it) }
                visits.firstOrNull()?.let { v -> Spacer(Modifier.height(14.dp)); Text(v.visitedAt, color = MaterialTheme.colorScheme.primary); v.oneLineReview?.let { Text("“$it”", style = MaterialTheme.typography.titleMedium) }; v.detailedReview?.let { Text(it, Modifier.padding(top = 8.dp)) } }
                if (tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) { tags.take(4).forEach { AssistChip(onClick = {}, label = { Text("#${it.name}") }) } }
            } }
            item { Text("작품 ${artworks.size}", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }
            if (artworks.isEmpty()) item { Text("아직 등록한 작품이 없습니다.", Modifier.padding(horizontal = 20.dp)) }
            items(artworks, key = { it.artwork.id }) { card ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        Poster(card.images.firstOrNull()?.localPath, Modifier.size(90.dp))
                        Spacer(Modifier.width(12.dp)); Column { Text(card.artwork.title, fontWeight = FontWeight.Bold); card.artist?.let { Text(it.name) }; card.artwork.personalReview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkCreateScreen(vm: AppViewModel, exhibitionId: Long, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }; var imagePath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope(); val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { imagePath = vm.fileStore.copyImage(it) } } }
    Scaffold(topBar = { TopAppBar(title = { Text("작품 추가") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, null) } }) }) { p ->
        LazyColumn(Modifier.padding(p).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Box(Modifier.fillMaxWidth().height(240.dp).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(imagePath, Modifier.fillMaxSize()); if (imagePath == null) Text("작품 사진 선택") } }
            item { OutlinedTextField(title, { title = it }, label = { Text("작품명 *") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(artist, { artist = it }, label = { Text("작가") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(review, { review = it }, label = { Text("내 감상") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { Button(onClick = { vm.addArtwork(exhibitionId, title, artist, review, imagePath, onDone) }, modifier = Modifier.fillMaxWidth()) { Text("저장") } }
        }
    }
}

private const val CALENDAR_CENTER_PAGE = 5000
private const val CALENDAR_PAGE_COUNT = 10000

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalendarScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val visits by vm.visits.collectAsStateWithLifecycle(); val exhibitions by vm.exhibitions.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = CALENDAR_CENTER_PAGE) { CALENDAR_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val month = YearMonth.now().plusMonths((pagerState.currentPage - CALENDAR_CENTER_PAGE).toLong())
    Scaffold(topBar = { TopAppBar(title = { Text("${month.year}년 ${month.monthValue}월") }, navigationIcon = { IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) { Icon(Icons.Default.ChevronLeft, null) } }, actions = { IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) { Icon(Icons.Default.ChevronRight, null) } }) }) { p ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(p).fillMaxSize()) { page ->
            val pageMonth = YearMonth.now().plusMonths((page - CALENDAR_CENTER_PAGE).toLong())
            val first = pageMonth.atDay(1); val offset = first.dayOfWeek.value % 7; val cells = List(offset) { null } + (1..pageMonth.lengthOfMonth()).map { pageMonth.atDay(it) }
            LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(cells) { date ->
                    if (date == null) Spacer(Modifier.aspectRatio(0.72f)) else {
                        val dayVisits = visits.filter { it.visitedAt == date.toString() }; val ex = dayVisits.firstOrNull()?.let { v -> exhibitions.firstOrNull { it.id == v.exhibitionId } }
                        Card(Modifier.padding(2.dp).aspectRatio(0.72f).clickable(enabled = ex != null) { ex?.let { onOpen(it.id) } }) {
                            Column { Text(date.dayOfMonth.toString(), Modifier.padding(5.dp), style = MaterialTheme.typography.labelSmall); ex?.let { Poster(it.posterPath, Modifier.fillMaxWidth().weight(1f)); if (dayVisits.size > 1) Text("+${dayVisits.size - 1}", Modifier.padding(2.dp)) } }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val exhibitions by vm.exhibitions.collectAsStateWithLifecycle(); val artists by vm.artists.collectAsStateWithLifecycle(); val tags by vm.tags.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text("아카이브") }) }) { p ->
        Column(Modifier.padding(p)) {
            TabRow(pagerState.currentPage) { listOf("전시", "작가", "태그").forEachIndexed { i, s -> Tab(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } }, text = { Text(s) }) } }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> LazyColumn { items(exhibitions) { ExhibitionRow(it, onOpen) } }
                    1 -> LazyColumn { items(artists) { ListItem(headlineContent = { Text(it.name) }, supportingContent = { Text(it.nationality.orEmpty()) }, leadingContent = { Icon(Icons.Default.Person, null) }) } }
                    else -> LazyColumn { items(tags) { ListItem(headlineContent = { Text("#${it.name}") }, leadingContent = { Icon(Icons.Default.Tag, null) }) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    var q by remember { mutableStateOf("") }; val results by remember(q) { vm.searchExhibitions(q) }.collectAsStateWithLifecycle(initialValue = emptyList())
    Scaffold(topBar = { TopAppBar(title = { Text("검색") }) }) { p ->
        Column(Modifier.padding(p)) { OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("전시명, 장소, 감상 검색") }, modifier = Modifier.fillMaxWidth().padding(16.dp)); LazyColumn { items(results) { ExhibitionRow(it, onOpen) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var status by remember { mutableStateOf("") }
    var naverClientId by remember { mutableStateOf("") }; var naverClientSecret by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { scope.launch { runCatching { vm.exportBackup(it) }.onSuccess { status = "백업 완료" }.onFailure { e -> status = e.message ?: "백업 실패" } } } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { runCatching { vm.importBackup(it) }.onSuccess { status = "복원 완료" }.onFailure { e -> status = e.message ?: "복원 실패" } } } }
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }) }) { p ->
        Column(Modifier.padding(p).padding(20.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("데이터", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { exportLauncher.launch("exhibition_backup_${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("백업 파일 만들기") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("백업에서 복원") }
            if (status.isNotBlank()) Text(status)
            HorizontalDivider()
            Text("전시 정보 자동 가져오기", style = MaterialTheme.typography.titleLarge)
            Text("전시 링크·이름 검색으로 정보를 자동 입력하려면 네이버 오픈API 검색 키가 필요합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(naverClientId, { naverClientId = it }, label = { Text("네이버 Client ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(naverClientSecret, { naverClientSecret = it }, label = { Text("네이버 Client Secret") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.saveNaverApiKeys(naverClientId, naverClientSecret) }, modifier = Modifier.fillMaxWidth()) { Text("API 키 저장") }
            HorizontalDivider(); Text("이 버전은 로컬 저장이 기본이다. 전시 정보 자동 가져오기 기능만 인터넷 연결이 필요하다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
