package com.example.exhibitionarchive.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val fontScale by vm.fontScale.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale)) {
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
                ExhibitionDetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate("artworkCreate/$id") }, { nav.navigate("visitMode/$id") }, { artworkId -> nav.navigate("artwork/$artworkId") })
            }
            composable("artworkCreate/{exhibitionId}", arguments = listOf(navArgument("exhibitionId") { type = NavType.LongType })) { back ->
                ArtworkCreateScreen(vm, back.arguments!!.getLong("exhibitionId")) { nav.popBackStack() }
            }
            composable("artwork/{artworkId}", arguments = listOf(navArgument("artworkId") { type = NavType.LongType })) { back ->
                ArtworkDetailScreen(vm, back.arguments!!.getLong("artworkId")) { nav.popBackStack() }
            }
            composable("visitMode/{exhibitionId}", arguments = listOf(navArgument("exhibitionId") { type = NavType.LongType })) { back ->
                VisitModeScreen(vm, back.arguments!!.getLong("exhibitionId")) { nav.popBackStack() }
            }
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
private fun rememberMultipleImagePicker(vm: AppViewModel, onPicked: (List<String>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            val paths = uris.mapNotNull { uri -> runCatching { vm.fileStore.copyImage(uri) }.getOrNull() }
            if (paths.isNotEmpty()) currentOnPicked(paths)
        }
    }
    return { launcher.launch("image/*") }
}

@Composable
private fun rememberCameraCapture(vm: AppViewModel, onCaptured: (String) -> Unit, onPermissionDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnPermissionDenied by rememberUpdatedState(onPermissionDenied)
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pendingFile?.let { file ->
            if (saved) currentOnCaptured(file.absolutePath) else file.delete()
        }
        pendingFile = null
    }
    val startCapture: () -> Unit = {
        val file = vm.fileStore.newImageFile()
        pendingFile = file
        takePicture.launch(vm.fileStore.uriFor(file))
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCapture() else currentOnPermissionDenied()
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCapture()
        else requestPermission.launch(Manifest.permission.CAMERA)
    }
}

@Composable
private fun ArtworkImagesEditor(imagePaths: List<String>, onGallery: () -> Unit, onCamera: () -> Unit, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("촬영") }
            OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("사진 선택") }
        }
        if (imagePaths.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("작품 사진을 여러 장 첨부할 수 있습니다.") }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                imagePaths.forEach { path ->
                    Box(Modifier.size(104.dp)) {
                        Poster(path, Modifier.fillMaxSize())
                        IconButton(onClick = { onRemove(path) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(16.dp))) { Icon(Icons.Default.Close, "사진 삭제") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageDialog(path: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                    }
                }
        ) {
            AsyncImage(
                model = File(path), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Default.Close, "닫기", tint = Color.White) }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Museum, null, modifier = Modifier.size(56.dp)); Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold); Text(body); Spacer(Modifier.height(16.dp)); Button(onClick = onAction) { Text("전시 추가") }
    }
}

@Composable
private fun RatingBar(rating: Float, onRatingChange: ((Float) -> Unit)? = null) {
    Row {
        (1..5).forEach { star ->
            Icon(
                if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "별점 $star",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (onRatingChange != null) 36.dp else 20.dp)
                    .let { m -> if (onRatingChange != null) m.clickable { onRatingChange(if (rating == star.toFloat()) 0f else star.toFloat()) } else m }
            )
        }
    }
}

private data class PendingArtwork(val title: String, val artist: String?, val review: String?, val imagePaths: List<String>, val sourceUrl: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionCreateScreen(vm: AppViewModel, onBack: () -> Unit, onDone: (Long) -> Unit) {
    var title by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }; var oneLine by remember { mutableStateOf("") }; var detail by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var imagePath by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }; var startDate by remember { mutableStateOf("") }; var endDate by remember { mutableStateOf("") }; var officialUrl by remember { mutableStateOf("") }
    var importUrl by remember { mutableStateOf("") }; var rating by remember { mutableFloatStateOf(0f) }
    var pendingArtworks by remember { mutableStateOf<List<PendingArtwork>>(emptyList()) }; var showArtworkDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope(); val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { imagePath = vm.fileStore.copyImage(it) } } }
    fun applyImport(info: ExhibitionImportInfo) {
        info.title?.let { title = it }
        info.venueName?.let { venue = it }; info.description?.let { description = it }
        info.startDate?.let { startDate = it }; info.endDate?.let { endDate = it }; info.officialUrl?.let { officialUrl = it }
        info.posterImageUrl?.let { url -> scope.launch { vm.fileStore.downloadImage(url)?.let { imagePath = it } } }
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
            item {
                Column {
                    Text("별점", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    RatingBar(rating = rating, onRatingChange = { rating = it })
                }
            }
            item { OutlinedTextField(oneLine, { oneLine = it }, label = { Text("한줄평") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(detail, { detail = it }, label = { Text("상세 감상") }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(tags, { tags = it }, label = { Text("태그, 쉼표로 구분") }, modifier = Modifier.fillMaxWidth()) }
            item { HorizontalDivider() }
            item { OutlinedButton(onClick = { showArtworkDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("작품 추가") } }
            if (pendingArtworks.isNotEmpty()) {
                item { Text("추가된 작품 (${pendingArtworks.size})", style = MaterialTheme.typography.titleSmall) }
                items(pendingArtworks) { pa ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Poster(pa.imagePaths.firstOrNull(), Modifier.size(48.dp))
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
                            vm.createExhibition(title, it, imagePath, venue, oneLine, detail, tags, description, startDate.ifBlank { null }, endDate.ifBlank { null }, officialUrl.ifBlank { null }, rating.takeIf { r -> r > 0f }, onDone = { id ->
                                pendingArtworks.forEach { pa -> vm.addArtwork(id, pa.title, pa.artist, pa.review, pa.imagePaths, pa.sourceUrl) {} }
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
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }; var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }; var sourceUrl by remember { mutableStateOf("") }
    var attachmentMessage by remember { mutableStateOf("") }
    val openGallery = rememberMultipleImagePicker(vm) { imagePaths = imagePaths + it }
    val openCamera = rememberCameraCapture(vm, onCaptured = { imagePaths = imagePaths + it }, onPermissionDenied = { attachmentMessage = "카메라 권한이 필요합니다." })
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("작품 추가", style = MaterialTheme.typography.titleMedium)
                ArtworkImagesEditor(imagePaths, openGallery, openCamera) { path -> imagePaths = imagePaths - path }
                if (attachmentMessage.isNotBlank()) Text(attachmentMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, label = { Text("작품명 *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("작가") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(review, { review = it }, label = { Text("내 감상") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("출처/설명 링크") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = { if (title.isNotBlank()) { onAdd(PendingArtwork(title.trim(), artist.trim().ifBlank { null }, review.trim().ifBlank { null }, imagePaths, sourceUrl.trim().ifBlank { null })); onDismiss() } }) { Text("추가") }
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
private fun ExhibitionDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onAddArtwork: () -> Unit, onVisitMode: () -> Unit, onOpenArtwork: (Long) -> Unit) {
    val exhibition by vm.exhibition(id).collectAsStateWithLifecycle(initialValue = null)
    val visits by vm.visitsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val artworks by vm.artworksFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val tags by vm.tagsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    zoomImagePath?.let { ZoomableImageDialog(it) { zoomImagePath = null } }
    Scaffold(topBar = { TopAppBar(title = { Text(exhibition?.title ?: "전시") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = onVisitMode) { Icon(Icons.Default.Visibility, "관람 모드") } }) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddArtwork, icon = { Icon(Icons.Default.AddPhotoAlternate, null) }, text = { Text("작품 추가") }) }) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(bottom = 100.dp)) {
            item { Box(Modifier.clickable(enabled = exhibition?.posterPath != null) { exhibition?.posterPath?.let { zoomImagePath = it } }) { Poster(exhibition?.posterPath, Modifier.fillMaxWidth().height(300.dp)) } }
            item { Column(Modifier.padding(20.dp)) {
                Text(exhibition?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                exhibition?.venueName?.let { Text(it) }
                visits.firstOrNull()?.let { v -> Spacer(Modifier.height(14.dp)); Text(v.visitedAt, color = MaterialTheme.colorScheme.primary); v.rating?.let { RatingBar(rating = it) }; v.oneLineReview?.let { Text("“$it”", style = MaterialTheme.typography.titleMedium) }; v.detailedReview?.let { Text(it, Modifier.padding(top = 8.dp)) } }
                if (tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) { tags.take(4).forEach { AssistChip(onClick = {}, label = { Text("#${it.name}") }) } }
            } }
            item { Text("작품 ${artworks.size}", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }
            if (artworks.isEmpty()) item { Text("아직 등록한 작품이 없습니다.", Modifier.padding(horizontal = 20.dp)) }
            items(artworks, key = { it.artwork.id }) { card ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onOpenArtwork(card.artwork.id) }) {
                    Row(Modifier.padding(12.dp)) {
                        Box(Modifier.clickable(enabled = card.images.firstOrNull()?.localPath != null) { card.images.firstOrNull()?.localPath?.let { zoomImagePath = it } }) {
                            Poster(card.images.firstOrNull()?.localPath, Modifier.size(90.dp))
                        }
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
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }; var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }; var sourceUrl by remember { mutableStateOf("") }
    var attachmentMessage by remember { mutableStateOf("") }
    val openGallery = rememberMultipleImagePicker(vm) { imagePaths = imagePaths + it }
    val openCamera = rememberCameraCapture(vm, onCaptured = { imagePaths = imagePaths + it }, onPermissionDenied = { attachmentMessage = "카메라 권한이 필요합니다." })
    Scaffold(topBar = { TopAppBar(title = { Text("작품 추가") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, null) } }) }) { p ->
        LazyColumn(Modifier.padding(p).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ArtworkImagesEditor(imagePaths, openGallery, openCamera) { path -> imagePaths = imagePaths - path } }
            if (attachmentMessage.isNotBlank()) item { Text(attachmentMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(title, { title = it }, label = { Text("작품명 *") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(artist, { artist = it }, label = { Text("작가") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(review, { review = it }, label = { Text("내 감상") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("출처/설명 링크") }, modifier = Modifier.fillMaxWidth()) }
            item { Button(onClick = { vm.addArtwork(exhibitionId, title, artist, review, imagePaths, sourceUrl, onDone) }, modifier = Modifier.fillMaxWidth()) { Text("저장") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkDetailScreen(vm: AppViewModel, artworkId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val card by vm.artwork(artworkId).collectAsStateWithLifecycle(initialValue = null)
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    zoomImagePath?.let { ZoomableImageDialog(it) { zoomImagePath = null } }
    Scaffold(topBar = { TopAppBar(title = { Text(card?.artwork?.title ?: "작품") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
        val artwork = card
        if (artwork == null) {
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.padding(p).fillMaxSize().verticalScroll(rememberScrollState())) {
                if (artwork.images.isEmpty()) {
                    Poster(null, Modifier.fillMaxWidth().height(320.dp))
                } else {
                    val pagerState = rememberPagerState(pageCount = { artwork.images.size })
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(320.dp)) { page ->
                        val path = artwork.images[page].localPath
                        Box(Modifier.fillMaxSize().clickable(enabled = path != null) { path?.let { zoomImagePath = it } }) {
                            Poster(path, Modifier.fillMaxSize())
                        }
                    }
                    if (artwork.images.size > 1) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                            repeat(artwork.images.size) { i ->
                                Box(Modifier.padding(3.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                            }
                        }
                    }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(artwork.artwork.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    artwork.artist?.let { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    listOfNotNull(artwork.artwork.productionYear, artwork.artwork.medium, artwork.artwork.dimensions).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    artwork.artwork.description?.let { Text(it) }
                    artwork.artwork.personalReview?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge) }
                    artwork.artwork.sourceUrl?.let { url ->
                        Row(Modifier.padding(top = 8.dp).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(url, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private sealed interface VisitTimelineItem {
    val createdAt: Long
    data class Note(val value: VisitNoteEntity) : VisitTimelineItem { override val createdAt = value.createdAt }
    data class Audio(val value: AudioRecordEntity) : VisitTimelineItem { override val createdAt = value.recordedAt }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitModeScreen(vm: AppViewModel, exhibitionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val notes by vm.visitNotesFor(exhibitionId).collectAsStateWithLifecycle(initialValue = emptyList())
    val audioRecords by vm.audioFor(exhibitionId).collectAsStateWithLifecycle(initialValue = emptyList())
    val recorder = remember { AudioRecorder() }
    var memo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var playingAudioId by remember { mutableStateOf<Long?>(null) }

    zoomImagePath?.let { ZoomableImageDialog(it) { zoomImagePath = null } }

    val openGallery = rememberMultipleImagePicker(vm) { paths ->
        paths.forEach { path -> vm.addVisitNote(exhibitionId, path, null) }
    }
    val openCamera = rememberCameraCapture(
        vm,
        onCaptured = { path -> vm.addVisitNote(exhibitionId, path, null) },
        onPermissionDenied = { status = "카메라 권한이 필요합니다." }
    )

    val startRecording: () -> Unit = {
        val file = vm.fileStore.newAudioFile()
        runCatching { recorder.start(file) }
            .onSuccess {
                recordingFile = file
                recordingStartedAt = System.currentTimeMillis()
                isRecording = true
                status = "녹음 중"
            }
            .onFailure {
                file.delete()
                status = it.message ?: "녹음을 시작하지 못했습니다."
            }
    }
    val stopRecording: () -> Unit = {
        if (isRecording) {
            val file = recordingFile
            val duration = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
            recorder.stopSafely()
            isRecording = false
            recordingFile = null
            recordingStartedAt = 0L
            if (file != null && file.exists() && file.length() > 0L) {
                vm.saveAudio(exhibitionId, file.absolutePath, durationMillis = duration)
                status = "음성 메모 저장 완료"
            } else {
                file?.delete()
                status = "녹음 파일을 저장하지 못했습니다."
            }
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else status = "마이크 권한이 필요합니다."
    }
    val toggleRecording: () -> Unit = {
        if (isRecording) stopRecording()
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopPlayback() {
        player.value?.runCatching { stop() }
        player.value?.release()
        player.value = null
        playingAudioId = null
    }
    fun togglePlayback(audio: AudioRecordEntity) {
        if (playingAudioId == audio.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val path = audio.filePath ?: return
        runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
        }.onSuccess {
            player.value = it
            playingAudioId = audio.id
        }.onFailure { status = it.message ?: "음성을 재생하지 못했습니다." }
    }

    val latestIsRecording by rememberUpdatedState(isRecording)
    val latestRecordingFile by rememberUpdatedState(recordingFile)
    val latestRecordingStartedAt by rememberUpdatedState(recordingStartedAt)
    DisposableEffect(Unit) {
        onDispose {
            if (latestIsRecording) {
                recorder.stopSafely()
                latestRecordingFile?.let { file ->
                    if (file.exists() && file.length() > 0L) {
                        vm.saveAudio(exhibitionId, file.absolutePath, durationMillis = (System.currentTimeMillis() - latestRecordingStartedAt).coerceAtLeast(0L))
                    } else file.delete()
                }
            }
            player.value?.release()
            player.value = null
        }
    }

    val timeline: List<VisitTimelineItem> = (notes.map { VisitTimelineItem.Note(it) } + audioRecords.map { VisitTimelineItem.Audio(it) }).sortedByDescending { it.createdAt }
    Scaffold(topBar = { TopAppBar(title = { Text("관람 모드") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = openCamera, modifier = Modifier.weight(1f).height(72.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PhotoCamera, null); Text("촬영") } }
                    OutlinedButton(onClick = openGallery, modifier = Modifier.weight(1f).height(72.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PhotoLibrary, null); Text("사진 첨부") } }
                }
            }
            item {
                Button(onClick = toggleRecording, colors = if (isRecording) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRecording) "녹음 정지" else "녹음 시작")
                }
            }
            if (status.isNotBlank()) item { Text(status, color = if (status.contains("필요") || status.contains("못했습니다")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            item {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(memo, { memo = it }, label = { Text("빠른 메모") }, minLines = 2, modifier = Modifier.weight(1f))
                    Button(onClick = { vm.addVisitNote(exhibitionId, null, memo) { memo = "" } }, enabled = memo.isNotBlank()) { Text("추가") }
                }
            }
            item { HorizontalDivider(); Text("관람 기록", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            if (timeline.isEmpty()) item { Text("아직 관람 중 기록이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(timeline, key = { item -> when (item) { is VisitTimelineItem.Note -> "note-${item.value.id}"; is VisitTimelineItem.Audio -> "audio-${item.value.id}" } }) { item ->
                when (item) {
                    is VisitTimelineItem.Note -> {
                        val note = item.value
                        note.photoPath?.let { path ->
                            Card(Modifier.fillMaxWidth().clickable { zoomImagePath = path }) { AsyncImage(model = File(path), contentDescription = "관람 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp)) }
                        }
                        note.text?.let { text -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Text(text, Modifier.padding(14.dp)) } }
                    }
                    is VisitTimelineItem.Audio -> {
                        val audio = item.value
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { togglePlayback(audio) }) { Icon(if (playingAudioId == audio.id) Icons.Default.Stop else Icons.Default.PlayArrow, if (playingAudioId == audio.id) "재생 정지" else "재생") }
                                Column { Text(audio.title ?: "음성 기록", fontWeight = FontWeight.SemiBold); Text(audio.durationMillis?.let(::formatDuration) ?: "길이 정보 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
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
            val first = pageMonth.atDay(1); val offset = first.dayOfWeek.value % 7
            val cells = List(offset) { null } + (1..pageMonth.lengthOfMonth()).map { pageMonth.atDay(it) }
            val weeks = cells.chunked(7).map { it + List(7 - it.size) { null } }
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                        Text(day, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        week.forEach { date ->
                            if (date == null) Spacer(Modifier.weight(1f).fillMaxHeight()) else {
                                val dayVisits = visits.filter { it.visitedAt == date.toString() }; val ex = dayVisits.firstOrNull()?.let { v -> exhibitions.firstOrNull { it.id == v.exhibitionId } }
                                Card(Modifier.weight(1f).fillMaxHeight().padding(2.dp).clickable(enabled = ex != null) { ex?.let { onOpen(it.id) } }) {
                                    Column { Text(date.dayOfMonth.toString(), Modifier.padding(5.dp), style = MaterialTheme.typography.labelMedium); ex?.let { Poster(it.posterPath, Modifier.fillMaxWidth().weight(1f)); if (dayVisits.size > 1) Text("+${dayVisits.size - 1}", Modifier.padding(2.dp)) } }
                                }
                            }
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
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filteredExhibitions = if (q.isEmpty()) exhibitions else exhibitions.filter { it.title.contains(q, ignoreCase = true) || it.venueName?.contains(q, ignoreCase = true) == true }
    val filteredArtists = if (q.isEmpty()) artists else artists.filter { it.name.contains(q, ignoreCase = true) }
    val filteredTags = if (q.isEmpty()) tags else tags.filter { it.name.contains(q, ignoreCase = true) }
    Scaffold(topBar = { TopAppBar(title = { Text("아카이브") }) }) { p ->
        Column(Modifier.padding(p).imePadding()) {
            OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("아카이브 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            TabRow(pagerState.currentPage) { listOf("전시", "작가", "태그").forEachIndexed { i, s -> Tab(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } }, text = { Text(s) }) } }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> LazyColumn(Modifier.fillMaxSize()) { items(filteredExhibitions) { ExhibitionRow(it, onOpen) } }
                    1 -> LazyColumn(Modifier.fillMaxSize()) { items(filteredArtists) { ListItem(headlineContent = { Text(it.name) }, supportingContent = { Text(it.nationality.orEmpty()) }, leadingContent = { Icon(Icons.Default.Person, null) }) } }
                    else -> LazyColumn(Modifier.fillMaxSize()) { items(filteredTags) { ListItem(headlineContent = { Text("#${it.name}") }, leadingContent = { Icon(Icons.Default.Tag, null) }) } }
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
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { scope.launch { runCatching { vm.exportBackup(it) }.onSuccess { status = "백업 완료" }.onFailure { e -> status = e.message ?: "백업 실패" } } } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { scope.launch { runCatching { vm.importBackup(it) }.onSuccess { status = "복원 완료" }.onFailure { e -> status = e.message ?: "복원 실패" } } } }
    val fontScale by vm.fontScale.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }) }) { p ->
        Column(Modifier.padding(p).padding(20.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("화면", style = MaterialTheme.typography.titleLarge)
            Text("글자 크기", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("작게" to 0.85f, "보통" to 1f, "크게" to 1.15f).forEach { (label, scale) ->
                    FilterChip(selected = fontScale == scale, onClick = { vm.setFontScale(scale) }, label = { Text(label) })
                }
            }
            HorizontalDivider()
            Text("데이터", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { exportLauncher.launch("exhibition_backup_${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("백업 파일 만들기") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("백업에서 복원") }
            if (status.isNotBlank()) Text(status)
            HorizontalDivider(); Text("이 버전은 로컬 저장이 기본이다. 전시 링크 가져오기 기능만 인터넷 연결이 필요하다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
