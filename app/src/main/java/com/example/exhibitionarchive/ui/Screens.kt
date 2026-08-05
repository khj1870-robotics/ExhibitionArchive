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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
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
            if (route in listOf("home", "calendar", "archive", "wishlist", "settings")) {
                NavigationBar {
                    listOf(
                        Triple("home", Icons.Default.Home, "홈"),
                        Triple("calendar", Icons.Default.CalendarMonth, "달력"),
                        Triple("archive", Icons.Default.CollectionsBookmark, "아카이브"),
                        Triple("wishlist", Icons.Default.FavoriteBorder, "위시리스트"),
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
            composable("calendar") {
                CalendarScreen(
                    vm,
                    { nav.navigate("exhibition/$it") },
                    { date -> nav.navigate(if (date != null) "createExhibition?date=$date" else "createExhibition") },
                    { id -> nav.navigate("artworkCreate/$id") }
                )
            }
            composable("archive") { ArchiveScreen(vm, { nav.navigate("exhibition/$it") }, { nav.navigate("artist/$it") }) { nav.navigate("tag/$it") } }
            composable("wishlist") { WishlistScreen(vm, { nav.navigate("exhibition/$it") }) { nav.navigate("wishlistCreate") } }
            composable("wishlistCreate") { WishlistCreateScreen(vm, { nav.popBackStack() }) { id -> nav.navigate("exhibition/$id") { popUpTo("wishlistCreate") { inclusive = true } } } }
            composable("settings") { SettingsScreen(vm) }
            composable("search") { SearchScreen(vm) { nav.navigate("exhibition/$it") } }
            composable(
                "createExhibition?date={date}",
                arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { back ->
                ExhibitionCreateScreen(vm, back.arguments?.getString("date"), { nav.popBackStack() }) { id -> nav.navigate("exhibition/$id") { popUpTo("createExhibition?date={date}") { inclusive = true } } }
            }
            composable("exhibition/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                val id = back.arguments?.getLong("id") ?: return@composable
                ExhibitionDetailScreen(vm, id, { nav.popBackStack() }, { nav.navigate("artworkCreate/$id") }, { nav.navigate("visitMode/$id") }, { artworkId -> nav.navigate("artwork/$artworkId") }, { nav.navigate("exhibitionEdit/$id") })
            }
            composable("exhibitionEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                ExhibitionEditScreen(vm, back.arguments!!.getLong("id")) { nav.popBackStack() }
            }
            composable("artworkCreate/{exhibitionId}", arguments = listOf(navArgument("exhibitionId") { type = NavType.LongType })) { back ->
                ArtworkCreateScreen(vm, back.arguments!!.getLong("exhibitionId")) { nav.popBackStack() }
            }
            composable("artwork/{artworkId}", arguments = listOf(navArgument("artworkId") { type = NavType.LongType })) { back ->
                ArtworkDetailScreen(vm, back.arguments!!.getLong("artworkId"), { nav.popBackStack() }) { id -> nav.navigate("artworkEdit/$id") }
            }
            composable("artworkEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                ArtworkEditScreen(vm, back.arguments!!.getLong("id")) { nav.popBackStack() }
            }
            composable("visitMode/{exhibitionId}", arguments = listOf(navArgument("exhibitionId") { type = NavType.LongType })) { back ->
                VisitModeScreen(vm, back.arguments!!.getLong("exhibitionId")) { nav.popBackStack() }
            }
            composable("artist/{artistId}", arguments = listOf(navArgument("artistId") { type = NavType.LongType })) { back ->
                ArtistDetailScreen(vm, back.arguments!!.getLong("artistId"), { nav.popBackStack() }) { artworkId -> nav.navigate("artwork/$artworkId") }
            }
            composable("tag/{tagId}", arguments = listOf(navArgument("tagId") { type = NavType.LongType })) { back ->
                TagDetailScreen(vm, back.arguments!!.getLong("tagId"), { nav.popBackStack() }) { artworkId -> nav.navigate("artwork/$artworkId") }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: AppViewModel, onCreate: () -> Unit, onOpen: (Long) -> Unit, onSearch: () -> Unit, onAddArtwork: (Long) -> Unit) {
    val exhibitions by vm.visitedExhibitions.collectAsStateWithLifecycle()
    val visits by vm.visits.collectAsStateWithLifecycle()
    val tagUsage by vm.tagUsage.collectAsStateWithLifecycle()
    val gridColumns by vm.gridColumns.collectAsStateWithLifecycle()
    val exhibitionTitleById = remember(exhibitions) { exhibitions.associate { it.id to it.title } }
    val randomReviewVisit = remember(visits) { visits.filter { !it.oneLineReview.isNullOrBlank() }.randomOrNull() }
    val topTag = remember(tagUsage) { tagUsage.maxByOrNull { it.count } }
    val topRatedVisit = remember(visits) { visits.filter { it.rating != null }.maxByOrNull { it.rating!! } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("전시기록") }, actions = { IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "검색") } }) },
        floatingActionButton = { AddMenuButton(vm, onCreateExhibition = onCreate, onAddArtwork = onAddArtwork) }
    ) { p ->
        LazyVerticalGrid(columns = GridCells.Fixed(gridColumns), contentPadding = p, modifier = Modifier.fillMaxSize()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(20.dp)) {
                    Text("이번 달 ${visits.count { it.visitedAt.startsWith(YearMonth.now().toString()) }}회 관람", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp)); Text("포스터와 감상을 날짜별로 쌓는다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (randomReviewVisit != null || topTag != null || topRatedVisit != null) {
                        Spacer(Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            randomReviewVisit?.let { v ->
                                Card(Modifier.fillMaxWidth().clickable { onOpen(v.exhibitionId) }) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("“${v.oneLineReview}”", style = MaterialTheme.typography.titleSmall)
                                        exhibitionTitleById[v.exhibitionId]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                                    }
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                topTag?.let { t -> AssistChip(onClick = {}, leadingIcon = { Icon(Icons.Default.Tag, null) }, label = { Text("가장 많이 쓴 태그 #${t.tag.name}") }) }
                                topRatedVisit?.let { v ->
                                    AssistChip(onClick = { onOpen(v.exhibitionId) }, leadingIcon = { Icon(Icons.Default.Star, null) }, label = { Text("최고 평점 ${exhibitionTitleById[v.exhibitionId] ?: ""} ${v.rating?.toInt()}점") })
                                }
                            }
                        }
                    }
                }
            }
            if (exhibitions.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("아직 기록한 전시가 없습니다.", "첫 전시를 등록하세요.", onCreate) }
            else {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("최근 전시", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
                items(exhibitions, key = { it.id }) { Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) { ExhibitionGridItem(it, onOpen) } }
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
private fun ExhibitionGridItem(item: ExhibitionEntity, onOpen: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(item.id) }) {
        Poster(item.posterPath, Modifier.fillMaxWidth().aspectRatio(3f / 4f))
        Spacer(Modifier.height(4.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.venueName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Poster(path: String?, modifier: Modifier) {
    if (path == null) Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, null) }
    else AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(10.dp)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        val initialMillis = value.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis -> onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    showPicker = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("취소") } }
        ) { DatePicker(state = state) }
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            if (value.isNotBlank()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Close, "지우기") }
            else Icon(Icons.Default.CalendarMonth, null)
        },
        modifier = modifier.clickable { showPicker = true }
    )
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

private class AudioRecordingController(val isRecording: Boolean, val toggle: () -> Unit)

@Composable
private fun rememberAudioRecording(vm: AppViewModel, onSaved: (path: String, durationMillis: Long) -> Unit, onError: (String) -> Unit = {}): AudioRecordingController {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder() }
    var isRecording by remember { mutableStateOf(false) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    val latestOnSaved by rememberUpdatedState(onSaved)
    val latestOnError by rememberUpdatedState(onError)

    fun startRecording() {
        val file = vm.fileStore.newAudioFile()
        runCatching { recorder.start(file) }
            .onSuccess { recordingFile = file; recordingStartedAt = System.currentTimeMillis(); isRecording = true }
            .onFailure { file.delete(); latestOnError(it.message ?: "녹음을 시작하지 못했습니다.") }
    }
    fun stopRecording() {
        if (!isRecording) return
        val file = recordingFile
        val duration = (System.currentTimeMillis() - recordingStartedAt).coerceAtLeast(0L)
        recorder.stopSafely()
        isRecording = false
        recordingFile = null
        recordingStartedAt = 0L
        if (file != null && file.exists() && file.length() > 0L) latestOnSaved(file.absolutePath, duration)
        else { file?.delete(); latestOnError("녹음 파일을 저장하지 못했습니다.") }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else latestOnError("마이크 권한이 필요합니다.")
    }
    val toggle: () -> Unit = {
        if (isRecording) stopRecording()
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val latestIsRecording by rememberUpdatedState(isRecording)
    val latestFile by rememberUpdatedState(recordingFile)
    val latestStartedAt by rememberUpdatedState(recordingStartedAt)
    DisposableEffect(Unit) {
        onDispose {
            if (latestIsRecording) {
                recorder.stopSafely()
                latestFile?.let { file ->
                    if (file.exists() && file.length() > 0L) latestOnSaved(file.absolutePath, (System.currentTimeMillis() - latestStartedAt).coerceAtLeast(0L))
                    else file.delete()
                }
            }
        }
    }
    return AudioRecordingController(isRecording, toggle)
}

@Composable
private fun AudioClipsEditor(recording: AudioRecordingController, clips: List<Pair<String, Long?>>, onRemove: (Pair<String, Long?>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = recording.toggle,
            colors = if (recording.isRecording) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(if (recording.isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
            Spacer(Modifier.width(6.dp))
            Text(if (recording.isRecording) "녹음 정지" else "음성 녹음")
        }
        clips.forEach { clip ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null)
                Spacer(Modifier.width(8.dp))
                Text(clip.second?.let { formatDuration(it) } ?: "음성 메모", Modifier.weight(1f))
                IconButton(onClick = { onRemove(clip) }) { Icon(Icons.Default.Close, "녹음 삭제") }
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

private data class PendingArtwork(val title: String, val artist: String?, val review: String?, val imagePaths: List<String>, val sourceUrl: String?, val medium: String?, val description: String?, val audioClips: List<Pair<String, Long?>>, val tags: String)

private val TAG_PRESET_CATEGORIES: List<Pair<String, List<String>>> = listOf(
    "사조" to listOf("다다이즘", "인상주의", "후기인상주의", "입체주의", "초현실주의", "표현주의", "추상표현주의", "미니멀리즘", "팝아트", "신고전주의", "낭만주의", "사실주의", "야수파", "미래주의", "구성주의", "개념미술", "단색화"),
    "양식" to listOf("아르누보", "아르데코", "바로크", "로코코"),
    "장르·기법" to listOf("키네틱아트", "옵아트", "대지미술", "설치미술", "미디어아트", "회화", "조각", "판화", "드로잉", "콜라주", "사진", "도자공예", "섬유공예", "건축", "그래피티", "일러스트레이션", "민화")
)

@Composable
private fun TagInputField(value: String, label: String = "태그, 쉼표로 구분", onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
        val selected = value.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        TAG_PRESET_CATEGORIES.forEach { (category, presets) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { preset ->
                        val already = selected.contains(preset.lowercase())
                        AssistChip(
                            onClick = { if (!already) onValueChange(if (value.isBlank()) preset else "$value, $preset") },
                            label = { Text(preset) },
                            enabled = !already
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionCreateScreen(vm: AppViewModel, initialDate: String? = null, onBack: () -> Unit, onDone: (Long) -> Unit) {
    var title by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }; var oneLine by remember { mutableStateOf("") }; var detail by remember { mutableStateOf("") }; var tags by remember { mutableStateOf("") }; var date by remember { mutableStateOf(initialDate ?: LocalDate.now().toString()) }; var imagePath by remember { mutableStateOf<String?>(null) }
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
                    DateField(startDate, { startDate = it }, "전시 시작일", Modifier.weight(1f))
                    DateField(endDate, { endDate = it }, "전시 종료일", Modifier.weight(1f))
                }
            }
            item { DateField(date, { date = it }, "관람일", Modifier.fillMaxWidth()) }
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
            item { TagInputField(tags) { tags = it } }
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
                                pendingArtworks.forEach { pa -> vm.addArtwork(id, pa.title, pa.artist, pa.review, pa.imagePaths, pa.sourceUrl, pa.medium, pa.description, pa.audioClips, pa.tags.split(',')) {} }
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
private fun ExhibitionEditScreen(vm: AppViewModel, id: Long, onDone: () -> Unit) {
    val exhibition by vm.exhibition(id).collectAsStateWithLifecycle(initialValue = null)
    val visits by vm.visitsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val tags by vm.tagsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    var initialized by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }; var startDate by remember { mutableStateOf("") }; var endDate by remember { mutableStateOf("") }; var officialUrl by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }; var imagePath by remember { mutableStateOf<String?>(null) }
    var date by remember { mutableStateOf("") }; var oneLine by remember { mutableStateOf("") }; var detail by remember { mutableStateOf("") }; var rating by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(exhibition, visits, tags) {
        val ex = exhibition ?: return@LaunchedEffect
        if (!initialized) {
            title = ex.title; venue = ex.venueName.orEmpty(); description = ex.description.orEmpty()
            startDate = ex.startDate.orEmpty(); endDate = ex.endDate.orEmpty(); officialUrl = ex.officialUrl.orEmpty()
            imagePath = ex.posterPath
            tagsText = tags.joinToString(", ") { it.name }
            visits.firstOrNull()?.let { v -> date = v.visitedAt; oneLine = v.oneLineReview.orEmpty(); detail = v.detailedReview.orEmpty(); rating = v.rating ?: 0f }
            initialized = true
        }
    }
    val scope = rememberCoroutineScope(); val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { imagePath = vm.fileStore.copyImage(it) } } }
    Scaffold(topBar = { TopAppBar(title = { Text("전시 수정") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, null) } }) }) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Box(Modifier.fillMaxWidth().height(220.dp).clickable { launcher.launch("image/*") }, contentAlignment = Alignment.Center) { Poster(imagePath, Modifier.fillMaxSize()); if (imagePath == null) Text("포스터 선택") } }
            item { OutlinedTextField(title, { title = it }, label = { Text("전시명 *") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(description, { description = it }, label = { Text("전시 설명") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(startDate, { startDate = it }, "전시 시작일", Modifier.weight(1f))
                    DateField(endDate, { endDate = it }, "전시 종료일", Modifier.weight(1f))
                }
            }
            item { OutlinedTextField(venue, { venue = it }, label = { Text("장소") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(officialUrl, { officialUrl = it }, label = { Text("공식 링크") }, modifier = Modifier.fillMaxWidth()) }
            item { TagInputField(tagsText) { tagsText = it } }
            if (visits.isNotEmpty()) {
                item { HorizontalDivider() }
                item { DateField(date, { date = it }, "관람일", Modifier.fillMaxWidth()) }
                item {
                    Column {
                        Text("별점", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        RatingBar(rating = rating, onRatingChange = { rating = it })
                    }
                }
                item { OutlinedTextField(oneLine, { oneLine = it }, label = { Text("한줄평") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(detail, { detail = it }, label = { Text("상세 감상") }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
            }
            item {
                Button(
                    onClick = {
                        val ex = exhibition ?: return@Button
                        val originalVisit = visits.firstOrNull()
                        val updated = ex.copy(title = title.trim(), posterPath = imagePath, venueName = venue.trim().ifBlank { null }, description = description.trim().ifBlank { null }, startDate = startDate.ifBlank { null }, endDate = endDate.ifBlank { null }, officialUrl = officialUrl.trim().ifBlank { null })
                        if (originalVisit != null) {
                            runCatching { LocalDate.parse(date) }.onSuccess { d ->
                                val updatedVisit = originalVisit.copy(visitedAt = d.toString(), oneLineReview = oneLine.trim().ifBlank { null }, detailedReview = detail.trim().ifBlank { null }, rating = rating.takeIf { r -> r > 0f })
                                vm.updateExhibition(updated, tagsText, updatedVisit, onDone = onDone)
                            }
                        } else {
                            vm.updateExhibition(updated, tagsText, null, onDone = onDone)
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
    var medium by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var audioClips by remember { mutableStateOf<List<Pair<String, Long?>>>(emptyList()) }; var tags by remember { mutableStateOf("") }
    var attachmentMessage by remember { mutableStateOf("") }
    val openGallery = rememberMultipleImagePicker(vm) { imagePaths = imagePaths + it }
    val openCamera = rememberCameraCapture(vm, onCaptured = { imagePaths = imagePaths + it }, onPermissionDenied = { attachmentMessage = "카메라 권한이 필요합니다." })
    val recording = rememberAudioRecording(vm, onSaved = { path, duration -> audioClips = audioClips + (path to duration) }, onError = { attachmentMessage = it })
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("작품 추가", style = MaterialTheme.typography.titleMedium)
                ArtworkImagesEditor(imagePaths, openGallery, openCamera) { path -> imagePaths = imagePaths - path }
                if (attachmentMessage.isNotBlank()) Text(attachmentMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, label = { Text("작품명 *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("작가") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(medium, { medium = it }, label = { Text("재료/기법") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("작품 설명") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(review, { review = it }, label = { Text("내 감상") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("출처/설명 링크") }, modifier = Modifier.fillMaxWidth())
                TagInputField(tags) { tags = it }
                AudioClipsEditor(recording, audioClips) { clip -> audioClips = audioClips - clip }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = { if (title.isNotBlank()) { onAdd(PendingArtwork(title.trim(), artist.trim().ifBlank { null }, review.trim().ifBlank { null }, imagePaths, sourceUrl.trim().ifBlank { null }, medium.trim().ifBlank { null }, description.trim().ifBlank { null }, audioClips, tags)); onDismiss() } }) { Text("추가") }
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

@Composable
private fun AddMenuButton(vm: AppViewModel, compact: Boolean = false, onCreateExhibition: () -> Unit, onAddArtwork: (Long) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var showAddArtworkChoice by remember { mutableStateOf(false) }
    var showExhibitionPicker by remember { mutableStateOf(false) }
    if (showAddArtworkChoice) {
        AlertDialog(
            onDismissRequest = { showAddArtworkChoice = false },
            title = { Text("작품 추가") },
            text = { Text("어떤 전시에 작품을 추가할까요?") },
            confirmButton = { TextButton(onClick = { showAddArtworkChoice = false; showExhibitionPicker = true }) { Text("기존 전시에 추가") } },
            dismissButton = { TextButton(onClick = { showAddArtworkChoice = false; onCreateExhibition() }) { Text("새 전시 만들면서 추가") } }
        )
    }
    if (showExhibitionPicker) ExhibitionPickerDialog(vm, onDismiss = { showExhibitionPicker = false }, onSelect = { id -> showExhibitionPicker = false; onAddArtwork(id) })
    Box {
        if (compact) IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Add, "추가") }
        else FloatingActionButton(onClick = { showMenu = true }) { Icon(Icons.Default.Add, "추가") }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text("전시 추가") }, onClick = { showMenu = false; onCreateExhibition() })
            DropdownMenuItem(text = { Text("작품 추가") }, onClick = { showMenu = false; showAddArtworkChoice = true })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExhibitionDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onAddArtwork: () -> Unit, onVisitMode: () -> Unit, onOpenArtwork: (Long) -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val exhibition by vm.exhibition(id).collectAsStateWithLifecycle(initialValue = null)
    val visits by vm.visitsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val artworks by vm.artworksFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val tags by vm.tagsFor(id).collectAsStateWithLifecycle(initialValue = emptyList())
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    var showMarkVisited by remember { mutableStateOf(false) }
    val isWishlist = visits.isEmpty()
    zoomImagePath?.let { ZoomableImageDialog(it) { zoomImagePath = null } }
    if (showMarkVisited) MarkVisitedDialog(vm, id, onDismiss = { showMarkVisited = false }, onDone = { showMarkVisited = false })
    Scaffold(
        topBar = { TopAppBar(title = { Text(exhibition?.title ?: "전시") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "수정") }; IconButton(onClick = onVisitMode) { Icon(Icons.Default.Visibility, "관람 모드") } }) },
        floatingActionButton = {
            if (isWishlist) ExtendedFloatingActionButton(onClick = { showMarkVisited = true }, icon = { Icon(Icons.Default.CheckCircle, null) }, text = { Text("다녀왔어요") })
            else ExtendedFloatingActionButton(onClick = onAddArtwork, icon = { Icon(Icons.Default.AddPhotoAlternate, null) }, text = { Text("작품 추가") })
        }
    ) { p ->
        LazyColumn(Modifier.padding(p), contentPadding = PaddingValues(bottom = 100.dp)) {
            item { Box(Modifier.clickable(enabled = exhibition?.posterPath != null) { exhibition?.posterPath?.let { zoomImagePath = it } }) { Poster(exhibition?.posterPath, Modifier.fillMaxWidth().height(300.dp)) } }
            item { Column(Modifier.padding(20.dp)) {
                Text(exhibition?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                exhibition?.venueName?.let { Text(it) }
                listOfNotNull(exhibition?.startDate, exhibition?.endDate).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" ~ "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                exhibition?.description?.let { Text(it, Modifier.padding(top = 4.dp)) }
                exhibition?.officialUrl?.let { url ->
                    Row(Modifier.padding(top = 4.dp).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(url, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                visits.firstOrNull()?.let { v -> Spacer(Modifier.height(14.dp)); Text(v.visitedAt, color = MaterialTheme.colorScheme.primary); v.rating?.let { RatingBar(rating = it) }; v.oneLineReview?.let { Text("“$it”", style = MaterialTheme.typography.titleMedium) }; v.detailedReview?.let { Text(it, Modifier.padding(top = 8.dp)) } }
                if (tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) { tags.take(4).forEach { AssistChip(onClick = {}, label = { Text("#${it.name}") }) } }
            } }
            item { Text("작품 ${artworks.size}", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge) }
            if (artworks.isEmpty()) item { Text("아직 등록한 작품이 없습니다.", Modifier.padding(horizontal = 20.dp)) }
            items(artworks, key = { it.artwork.id }) { card ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onOpenArtwork(card.artwork.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable(enabled = card.images.firstOrNull()?.localPath != null) { card.images.firstOrNull()?.localPath?.let { zoomImagePath = it } }) {
                            Poster(card.images.firstOrNull()?.localPath, Modifier.size(90.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(card.artwork.title, fontWeight = FontWeight.Bold); card.artist?.let { Text(it.name) }; card.artwork.personalReview?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
                        if (card.artwork.sourceUrl != null) Icon(Icons.Default.Link, "출처 링크 있음", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                        if (card.audio.isNotEmpty()) Icon(Icons.Default.Mic, "음성 기록 있음", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkVisitedDialog(vm: AppViewModel, exhibitionId: Long, onDismiss: () -> Unit, onDone: () -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var oneLine by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var rating by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("다녀왔어요", style = MaterialTheme.typography.titleMedium)
                DateField(date, { date = it }, "관람일", Modifier.fillMaxWidth())
                Column {
                    Text("별점", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    RatingBar(rating = rating, onRatingChange = { rating = it })
                }
                OutlinedTextField(oneLine, { oneLine = it }, label = { Text("한줄평") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(detail, { detail = it }, label = { Text("상세 감상") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = {
                        runCatching { LocalDate.parse(date) }.onSuccess {
                            vm.markVisited(exhibitionId, it, oneLine.trim().ifBlank { null }, detail.trim().ifBlank { null }, rating.takeIf { r -> r > 0f }, onDone = onDone)
                        }
                    }) { Text("저장") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkCreateScreen(vm: AppViewModel, exhibitionId: Long, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }; var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }; var sourceUrl by remember { mutableStateOf("") }
    var medium by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var audioClips by remember { mutableStateOf<List<Pair<String, Long?>>>(emptyList()) }; var tags by remember { mutableStateOf("") }
    var attachmentMessage by remember { mutableStateOf("") }
    var savedCount by remember { mutableIntStateOf(0) }
    val openGallery = rememberMultipleImagePicker(vm) { imagePaths = imagePaths + it }
    val openCamera = rememberCameraCapture(vm, onCaptured = { imagePaths = imagePaths + it }, onPermissionDenied = { attachmentMessage = "카메라 권한이 필요합니다." })
    val recording = rememberAudioRecording(vm, onSaved = { path, duration -> audioClips = audioClips + (path to duration) }, onError = { attachmentMessage = it })
    fun resetForm() {
        title = ""; artist = ""; review = ""; imagePaths = emptyList(); sourceUrl = ""; medium = ""; description = ""; audioClips = emptyList(); tags = ""
    }
    Scaffold(topBar = { TopAppBar(title = { Text("작품 추가") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, "완료") } }) }) { p ->
        LazyColumn(Modifier.padding(p).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (savedCount > 0) item { Text("이번에 ${savedCount}개 저장했습니다. 계속 추가하거나 완료를 눌러 나가세요.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            item { ArtworkImagesEditor(imagePaths, openGallery, openCamera) { path -> imagePaths = imagePaths - path } }
            if (attachmentMessage.isNotBlank()) item { Text(attachmentMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(title, { title = it }, label = { Text("작품명 *") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(artist, { artist = it }, label = { Text("작가") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(medium, { medium = it }, label = { Text("재료/기법") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(description, { description = it }, label = { Text("작품 설명") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(review, { review = it }, label = { Text("내 감상") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("출처/설명 링크") }, modifier = Modifier.fillMaxWidth()) }
            item { TagInputField(tags) { tags = it } }
            item { AudioClipsEditor(recording, audioClips) { clip -> audioClips = audioClips - clip } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { if (title.isNotBlank()) vm.addArtwork(exhibitionId, title, artist, review, imagePaths, sourceUrl, medium, description, audioClips, tags.split(',')) { onDone() } else onDone() },
                        modifier = Modifier.weight(1f)
                    ) { Text("완료") }
                    Button(
                        onClick = { vm.addArtwork(exhibitionId, title, artist, review, imagePaths, sourceUrl, medium, description, audioClips, tags.split(',')) { savedCount++; resetForm() } },
                        modifier = Modifier.weight(1f)
                    ) { Text("저장하고 계속 추가") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkEditScreen(vm: AppViewModel, id: Long, onDone: () -> Unit) {
    val card by vm.artwork(id).collectAsStateWithLifecycle(initialValue = null)
    val artworkTags by vm.tagsForArtwork(id).collectAsStateWithLifecycle(initialValue = emptyList())
    var initialized by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }; var artist by remember { mutableStateOf("") }; var review by remember { mutableStateOf("") }
    var medium by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var sourceUrl by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var newImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var newAudioClips by remember { mutableStateOf<List<Pair<String, Long?>>>(emptyList()) }
    var attachmentMessage by remember { mutableStateOf("") }
    LaunchedEffect(card, artworkTags) {
        val c = card ?: return@LaunchedEffect
        if (!initialized) {
            title = c.artwork.title; artist = c.artist?.name.orEmpty(); review = c.artwork.personalReview.orEmpty()
            medium = c.artwork.medium.orEmpty(); description = c.artwork.description.orEmpty(); sourceUrl = c.artwork.sourceUrl.orEmpty()
            tagsText = artworkTags.joinToString(", ") { it.name }
            initialized = true
        }
    }
    val openGallery = rememberMultipleImagePicker(vm) { newImagePaths = newImagePaths + it }
    val openCamera = rememberCameraCapture(vm, onCaptured = { newImagePaths = newImagePaths + it }, onPermissionDenied = { attachmentMessage = "카메라 권한이 필요합니다." })
    val recording = rememberAudioRecording(vm, onSaved = { path, duration -> newAudioClips = newAudioClips + (path to duration) }, onError = { attachmentMessage = it })
    Scaffold(topBar = { TopAppBar(title = { Text("작품 수정") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, null) } }) }) { p ->
        val artwork = card
        if (artwork == null) {
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.padding(p).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("기존 사진", style = MaterialTheme.typography.titleSmall) }
                if (artwork.images.isEmpty()) {
                    item { Text("사진이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            artwork.images.forEach { img ->
                                Box(Modifier.size(104.dp)) {
                                    Poster(img.localPath, Modifier.fillMaxSize())
                                    IconButton(onClick = { vm.deleteArtworkImage(img.id) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(16.dp))) { Icon(Icons.Default.Close, "사진 삭제") }
                                }
                            }
                        }
                    }
                }
                item { ArtworkImagesEditor(newImagePaths, openGallery, openCamera) { path -> newImagePaths = newImagePaths - path } }
                if (attachmentMessage.isNotBlank()) item { Text(attachmentMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                item { OutlinedTextField(title, { title = it }, label = { Text("작품명 *") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(artist, { artist = it }, label = { Text("작가") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(medium, { medium = it }, label = { Text("재료/기법") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(description, { description = it }, label = { Text("작품 설명") }, minLines = 2, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(review, { review = it }, label = { Text("내 감상") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("출처/설명 링크") }, modifier = Modifier.fillMaxWidth()) }
                item { TagInputField(tagsText) { tagsText = it } }
                if (artwork.audio.isNotEmpty()) {
                    item { Text("기존 음성", style = MaterialTheme.typography.titleSmall) }
                    items(artwork.audio, key = { "existing-audio-${it.id}" }) { audio ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null)
                            Spacer(Modifier.width(8.dp))
                            Text(audio.title ?: "음성 기록", Modifier.weight(1f))
                            IconButton(onClick = { vm.deleteAudio(audio.id) }) { Icon(Icons.Default.Close, "삭제") }
                        }
                    }
                }
                item { AudioClipsEditor(recording, newAudioClips) { clip -> newAudioClips = newAudioClips - clip } }
                item {
                    Button(
                        onClick = {
                            val updated = artwork.artwork.copy(title = title.trim(), medium = medium.trim().ifBlank { null }, description = description.trim().ifBlank { null }, personalReview = review.trim().ifBlank { null }, sourceUrl = sourceUrl.trim().ifBlank { null })
                            vm.updateArtwork(updated, artist, newImagePaths, newAudioClips, tagsText.split(','), onDone = onDone)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("저장") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkDetailScreen(vm: AppViewModel, artworkId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val card by vm.artwork(artworkId).collectAsStateWithLifecycle(initialValue = null)
    val tags by vm.tagsForArtwork(artworkId).collectAsStateWithLifecycle(initialValue = emptyList())
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    zoomImagePath?.let { ZoomableImageDialog(it) { zoomImagePath = null } }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var playingAudioId by remember { mutableStateOf<Long?>(null) }
    fun stopPlayback() {
        player.value?.runCatching { stop() }
        player.value?.release()
        player.value = null
        playingAudioId = null
    }
    fun togglePlayback(audio: AudioRecordEntity) {
        if (playingAudioId == audio.id) { stopPlayback(); return }
        stopPlayback()
        val path = audio.filePath ?: return
        runCatching {
            MediaPlayer().apply { setDataSource(path); setOnCompletionListener { stopPlayback() }; prepare(); start() }
        }.onSuccess { player.value = it; playingAudioId = audio.id }
    }
    DisposableEffect(Unit) { onDispose { player.value?.release(); player.value = null } }
    Scaffold(topBar = { TopAppBar(title = { Text(card?.artwork?.title ?: "작품") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { onEdit(artworkId) }) { Icon(Icons.Default.Edit, "수정") } }) }) { p ->
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
                    if (tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) { tags.take(4).forEach { AssistChip(onClick = {}, label = { Text("#${it.name}") }) } }
                    if (artwork.audio.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("음성 기록", style = MaterialTheme.typography.titleSmall)
                        artwork.audio.forEach { audio ->
                            Card(Modifier.fillMaxWidth().clickable { togglePlayback(audio) }) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (playingAudioId == audio.id) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Column { Text(audio.title ?: "음성 기록", fontWeight = FontWeight.SemiBold); Text(audio.durationMillis?.let(::formatDuration) ?: "길이 정보 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun ArtworkAssignDialog(vm: AppViewModel, exhibitionId: Long, onDismiss: () -> Unit, onAssigned: (Long) -> Unit) {
    val artworks by vm.artworksFor(exhibitionId).collectAsStateWithLifecycle(initialValue = emptyList())
    var newTitle by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("작품에 추가", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    artworks.forEach { card ->
                        ListItem(
                            headlineContent = { Text(card.artwork.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = card.artist?.let { artist -> { Text(artist.name) } },
                            leadingContent = { Poster(card.images.firstOrNull()?.localPath, Modifier.size(40.dp)) },
                            modifier = Modifier.clickable { onAssigned(card.artwork.id) }
                        )
                    }
                    if (artworks.isEmpty()) Text("등록된 작품이 없습니다. 아래에서 새로 만들 수 있습니다.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("새 작품으로 추가", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newTitle, { newTitle = it }, label = { Text("작품명") }, modifier = Modifier.weight(1f))
                    Button(onClick = { if (newTitle.isNotBlank()) vm.addArtwork(exhibitionId, newTitle, null, null) { id -> onAssigned(id) } }, enabled = newTitle.isNotBlank()) { Text("만들기") }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("취소") }
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
    val notes by vm.visitNotesFor(exhibitionId).collectAsStateWithLifecycle(initialValue = emptyList())
    val audioRecords by vm.audioFor(exhibitionId).collectAsStateWithLifecycle(initialValue = emptyList())
    val keyboard = LocalSoftwareKeyboardController.current
    var memo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(1500); toast = null } }
    var zoomImagePath by remember { mutableStateOf<String?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    var playingAudioId by remember { mutableStateOf<Long?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectedAudioIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showAssignDialog by remember { mutableStateOf(false) }

    zoomImagePath?.let { ZoomableImageDialog(it) { zoomImagePath = null } }
    if (showAssignDialog) {
        ArtworkAssignDialog(
            vm, exhibitionId,
            onDismiss = { showAssignDialog = false },
            onAssigned = { artworkId ->
                vm.assignVisitItemsToArtwork(artworkId, selectedNoteIds.toList(), selectedAudioIds.toList()) {
                    selectMode = false; selectedNoteIds = emptySet(); selectedAudioIds = emptySet(); showAssignDialog = false
                }
            }
        )
    }

    val openGallery = rememberMultipleImagePicker(vm) { paths ->
        paths.forEach { path -> vm.addVisitNote(exhibitionId, path, null) }
        toast = "사진 추가 완료"
    }
    val openCamera = rememberCameraCapture(
        vm,
        onCaptured = { path -> vm.addVisitNote(exhibitionId, path, null); toast = "사진 촬영 완료" },
        onPermissionDenied = { status = "카메라 권한이 필요합니다." }
    )
    val recording = rememberAudioRecording(
        vm,
        onSaved = { path, duration -> vm.saveAudio(exhibitionId, path, durationMillis = duration); toast = "음성 메모 저장 완료" },
        onError = { status = it }
    )

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
    DisposableEffect(Unit) { onDispose { player.value?.release(); player.value = null } }

    val selectedCount = selectedNoteIds.size + selectedAudioIds.size
    val timeline: List<VisitTimelineItem> = (notes.map { VisitTimelineItem.Note(it) } + audioRecords.map { VisitTimelineItem.Audio(it) }).sortedByDescending { it.createdAt }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectMode) "${selectedCount}개 선택됨" else "관람 모드") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectMode) { selectMode = false; selectedNoteIds = emptySet(); selectedAudioIds = emptySet() } else onDone()
                    }) { Icon(if (selectMode) Icons.Default.Close else Icons.Default.ArrowBack, null) }
                },
                actions = {
                    if (timeline.isNotEmpty()) {
                        IconButton(onClick = {
                            selectMode = !selectMode
                            if (!selectMode) { selectedNoteIds = emptySet(); selectedAudioIds = emptySet() }
                        }) { Icon(Icons.Default.Checklist, "정리") }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectMode && selectedCount > 0) {
                ExtendedFloatingActionButton(onClick = { showAssignDialog = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("작품에 추가 ($selectedCount)") })
            }
        }
    ) { p ->
        Box(Modifier.fillMaxSize().padding(p)) {
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!selectMode) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = openCamera, modifier = Modifier.weight(1f).height(72.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PhotoCamera, null); Text("촬영") } }
                        OutlinedButton(onClick = openGallery, modifier = Modifier.weight(1f).height(72.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.PhotoLibrary, null); Text("사진 첨부") } }
                    }
                }
                item {
                    Button(onClick = recording.toggle, colors = if (recording.isRecording) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth().height(64.dp)) {
                        Icon(if (recording.isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (recording.isRecording) "녹음 정지" else "녹음 시작")
                    }
                }
                if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.error) }
                item {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(memo, { memo = it }, label = { Text("빠른 메모") }, minLines = 2, modifier = Modifier.weight(1f))
                        Button(onClick = { vm.addVisitNote(exhibitionId, null, memo) { memo = ""; toast = "메모 추가 완료"; keyboard?.hide() } }, enabled = memo.isNotBlank()) { Text("추가") }
                    }
                }
            }
            item { HorizontalDivider(); Text("관람 기록", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            if (timeline.isEmpty()) item { Text("아직 관람 중 기록이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(timeline, key = { item -> when (item) { is VisitTimelineItem.Note -> "note-${item.value.id}"; is VisitTimelineItem.Audio -> "audio-${item.value.id}" } }) { item ->
                when (item) {
                    is VisitTimelineItem.Note -> {
                        val note = item.value
                        val selected = selectedNoteIds.contains(note.id)
                        fun toggleNote() { selectedNoteIds = if (selected) selectedNoteIds - note.id else selectedNoteIds + note.id }
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            if (selectMode) Checkbox(checked = selected, onCheckedChange = { toggleNote() })
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                note.photoPath?.let { path ->
                                    Card(Modifier.fillMaxWidth().clickable { if (selectMode) toggleNote() else zoomImagePath = path }) { AsyncImage(model = File(path), contentDescription = "관람 사진", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp)) }
                                }
                                note.text?.let { text -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable(enabled = selectMode) { toggleNote() }) { Text(text, Modifier.padding(14.dp)) } }
                            }
                        }
                    }
                    is VisitTimelineItem.Audio -> {
                        val audio = item.value
                        val selected = selectedAudioIds.contains(audio.id)
                        fun toggleAudio() { selectedAudioIds = if (selected) selectedAudioIds - audio.id else selectedAudioIds + audio.id }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (selectMode) Checkbox(checked = selected, onCheckedChange = { toggleAudio() })
                            Card(Modifier.weight(1f).clickable { if (selectMode) toggleAudio() else togglePlayback(audio) }) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (!selectMode && playingAudioId == audio.id) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Column { Text(audio.title ?: "음성 기록", fontWeight = FontWeight.SemiBold); Text(audio.durationMillis?.let(::formatDuration) ?: "길이 정보 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }
        }
        toast?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            ) { Text(message, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall) }
        }
        }
    }
}

private const val CALENDAR_CENTER_PAGE = 5000
private const val CALENDAR_PAGE_COUNT = 10000

private val WISHLIST_DOT_COLORS = listOf(
    Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784), Color(0xFFFFB74D),
    Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFF06292), Color(0xFFA1887F)
)

private fun wishlistIncludes(w: ExhibitionEntity, date: LocalDate): Boolean {
    val start = w.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    val end = w.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return !date.isBefore(start) && !date.isAfter(end)
}

private fun wishlistOverlapsMonth(w: ExhibitionEntity, month: YearMonth): Boolean {
    val start = w.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    val end = w.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
    return !start.isAfter(month.atEndOfMonth()) && !end.isBefore(month.atDay(1))
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalendarScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onCreateExhibition: (String?) -> Unit, onAddArtwork: (Long) -> Unit) {
    val visits by vm.visits.collectAsStateWithLifecycle(); val exhibitions by vm.exhibitions.collectAsStateWithLifecycle()
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    val wishlistColors = remember(wishlist) { wishlist.mapIndexed { i, w -> w.id to WISHLIST_DOT_COLORS[i % WISHLIST_DOT_COLORS.size] }.toMap() }
    val pagerState = rememberPagerState(initialPage = CALENDAR_CENTER_PAGE) { CALENDAR_PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val month = YearMonth.now().plusMonths((pagerState.currentPage - CALENDAR_CENTER_PAGE).toLong())
    val monthWishlist = remember(wishlist, month) { wishlist.filter { w -> wishlistOverlapsMonth(w, month) } }
    val monthExhibitions = remember(visits, exhibitions, month) {
        visits.filter { it.visitedAt.startsWith(month.toString()) }
            .sortedBy { it.visitedAt }
            .mapNotNull { v -> exhibitions.firstOrNull { it.id == v.exhibitionId } }
            .distinctBy { it.id }
    }
    selectedDate?.let { d ->
        DayDetailDialog(
            vm, d, wishlistColors,
            onDismiss = { selectedDate = null },
            onOpen = { id -> selectedDate = null; onOpen(id) },
            onCreateExhibition = { selectedDate = null; onCreateExhibition(d.toString()) },
            onAddArtwork = { id -> selectedDate = null; onAddArtwork(id) }
        )
    }
    val sheetState = rememberBottomSheetScaffoldState()
    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 56.dp,
        topBar = { TopAppBar(title = { Text("${month.year}년 ${month.monthValue}월") }, navigationIcon = { IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) { Icon(Icons.Default.ChevronLeft, null) } }, actions = { IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) { Icon(Icons.Default.ChevronRight, null) } }) },
        sheetContent = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                if (monthWishlist.isNotEmpty()) {
                    Text("위시리스트", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    monthWishlist.forEach { w ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(w.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(wishlistColors[w.id] ?: Color.Gray))
                            Spacer(Modifier.width(8.dp))
                            Text(w.title, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text("${month.monthValue}월 전시", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                if (monthExhibitions.isEmpty()) {
                    Text("이 달에 관람한 전시가 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    monthExhibitions.forEach { ex -> ExhibitionRow(ex, onOpen) }
                }
            }
        }
    ) { p ->
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
                                val dayVisits = visits.filter { it.visitedAt == date.toString() }
                                val ex = dayVisits.firstOrNull()?.let { v -> exhibitions.firstOrNull { it.id == v.exhibitionId } }
                                val dayWishlistIds = wishlist.filter { w -> wishlistIncludes(w, date) }.map { it.id }
                                Card(Modifier.weight(1f).fillMaxHeight().padding(2.dp).clickable { selectedDate = date }) {
                                    Box(Modifier.fillMaxSize()) {
                                        if (ex?.posterPath != null) {
                                            Poster(ex.posterPath, Modifier.fillMaxSize())
                                            Text(
                                                dayVisits.size.toString(),
                                                Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        } else {
                                            Text(date.dayOfMonth.toString(), Modifier.padding(5.dp), style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (dayWishlistIds.isNotEmpty()) {
                                            Row(Modifier.align(Alignment.BottomEnd).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                dayWishlistIds.take(4).forEach { id -> Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(wishlistColors[id] ?: Color.Gray)) }
                                            }
                                        }
                                    }
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
private fun DayDetailDialog(
    vm: AppViewModel,
    date: LocalDate,
    wishlistColors: Map<Long, Color>,
    onDismiss: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreateExhibition: () -> Unit,
    onAddArtwork: (Long) -> Unit
) {
    val visits by vm.visits.collectAsStateWithLifecycle()
    val exhibitions by vm.exhibitions.collectAsStateWithLifecycle()
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    val dayVisitExhibitionIds = visits.filter { it.visitedAt == date.toString() }.map { it.exhibitionId }.distinct()
    val dayExhibitions = exhibitions.filter { it.id in dayVisitExhibitionIds }
    val dayWishlist = wishlist.filter { w -> wishlistIncludes(w, date) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp).fillMaxWidth().heightIn(max = 480.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${date.monthValue}월 ${date.dayOfMonth}일", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    AddMenuButton(vm, compact = true, onCreateExhibition = onCreateExhibition, onAddArtwork = onAddArtwork)
                }
                if (dayExhibitions.isEmpty() && dayWishlist.isEmpty()) {
                    Text("등록된 일정이 없습니다.", Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        dayExhibitions.forEach { ex ->
                            ListItem(
                                headlineContent = { Text(ex.title) },
                                supportingContent = { Text("관람") },
                                leadingContent = { Poster(ex.posterPath, Modifier.size(40.dp)) },
                                modifier = Modifier.clickable { onOpen(ex.id) }
                            )
                        }
                        dayWishlist.forEach { w ->
                            ListItem(
                                headlineContent = { Text(w.title) },
                                supportingContent = { Text("위시리스트") },
                                leadingContent = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(wishlistColors[w.id] ?: Color.Gray)) },
                                modifier = Modifier.clickable { onOpen(w.id) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
            }
        }
    }
}

private enum class ExhibitionSort(val label: String) { RECENT("최신순"), NAME("이름순"), RATING("별점순") }
private enum class ArtistSort(val label: String) { NAME("이름순"), COUNT("작품수순") }
private enum class TagSort(val label: String) { NAME("이름순"), COUNT("사용많은순") }

@Composable
private fun <T> SortChipRow(options: List<T>, selected: T, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt -> FilterChip(selected = opt == selected, onClick = { onSelect(opt) }, label = { Text(labelOf(opt)) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onOpenArtist: (Long) -> Unit, onOpenTag: (Long) -> Unit) {
    val exhibitions by vm.visitedExhibitions.collectAsStateWithLifecycle(); val artists by vm.artists.collectAsStateWithLifecycle(); val tags by vm.tags.collectAsStateWithLifecycle()
    val visits by vm.visits.collectAsStateWithLifecycle()
    val artistUsage by vm.artistUsage.collectAsStateWithLifecycle()
    val tagUsage by vm.tagUsage.collectAsStateWithLifecycle()
    val gridColumns by vm.gridColumns.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var exhibitionSort by remember { mutableStateOf(ExhibitionSort.RECENT) }
    var artistSort by remember { mutableStateOf(ArtistSort.NAME) }
    var tagSort by remember { mutableStateOf(TagSort.NAME) }
    val q = query.trim()
    val filteredExhibitions = if (q.isEmpty()) exhibitions else exhibitions.filter { it.title.contains(q, ignoreCase = true) || it.venueName?.contains(q, ignoreCase = true) == true }
    val filteredArtists = if (q.isEmpty()) artists else artists.filter { it.name.contains(q, ignoreCase = true) }
    val filteredTags = if (q.isEmpty()) tags else tags.filter { it.name.contains(q, ignoreCase = true) }
    val ratingByExhibition = remember(visits) { visits.filter { it.rating != null }.groupBy { it.exhibitionId }.mapValues { (_, vs) -> vs.first().rating!! } }
    val artistCountById = remember(artistUsage) { artistUsage.associate { it.artist.id to it.count } }
    val tagCountById = remember(tagUsage) { tagUsage.associate { it.tag.id to it.count } }
    val sortedExhibitions = remember(filteredExhibitions, exhibitionSort, ratingByExhibition) {
        when (exhibitionSort) {
            ExhibitionSort.RECENT -> filteredExhibitions
            ExhibitionSort.NAME -> filteredExhibitions.sortedBy { it.title }
            ExhibitionSort.RATING -> filteredExhibitions.sortedByDescending { ratingByExhibition[it.id] ?: -1f }
        }
    }
    val sortedArtists = remember(filteredArtists, artistSort, artistCountById) {
        when (artistSort) {
            ArtistSort.NAME -> filteredArtists.sortedBy { it.name }
            ArtistSort.COUNT -> filteredArtists.sortedByDescending { artistCountById[it.id] ?: 0 }
        }
    }
    val sortedTags = remember(filteredTags, tagSort, tagCountById) {
        when (tagSort) {
            TagSort.NAME -> filteredTags.sortedBy { it.name }
            TagSort.COUNT -> filteredTags.sortedByDescending { tagCountById[it.id] ?: 0 }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("아카이브") }) }) { p ->
        Column(Modifier.padding(p).imePadding()) {
            OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("아카이브 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            TabRow(pagerState.currentPage) { listOf("전시", "작가", "태그").forEachIndexed { i, s -> Tab(selected = pagerState.currentPage == i, onClick = { scope.launch { pagerState.animateScrollToPage(i) } }, text = { Text(s) }) } }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> Column(Modifier.fillMaxSize()) {
                        SortChipRow(ExhibitionSort.entries, exhibitionSort, { it.label }) { exhibitionSort = it }
                        LazyVerticalGrid(columns = GridCells.Fixed(gridColumns), modifier = Modifier.fillMaxWidth().weight(1f)) { items(sortedExhibitions, key = { it.id }) { Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) { ExhibitionGridItem(it, onOpen) } } }
                    }
                    1 -> Column(Modifier.fillMaxSize()) {
                        SortChipRow(ArtistSort.entries, artistSort, { it.label }) { artistSort = it }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) { items(sortedArtists) { ListItem(headlineContent = { Text(it.name) }, supportingContent = { Text(artistCountById[it.id]?.let { c -> "작품 ${c}개" } ?: it.nationality.orEmpty()) }, leadingContent = { Icon(Icons.Default.Person, null) }, modifier = Modifier.clickable { onOpenArtist(it.id) }) } }
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        SortChipRow(TagSort.entries, tagSort, { it.label }) { tagSort = it }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) { items(sortedTags) { ListItem(headlineContent = { Text("#${it.name}") }, supportingContent = tagCountById[it.id]?.let { c -> { Text("${c}회 사용") } }, leadingContent = { Icon(Icons.Default.Tag, null) }, modifier = Modifier.clickable { onOpenTag(it.id) }) } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtworkListScreen(title: String, artworks: List<ArtworkCard>, onBack: () -> Unit, onOpenArtwork: (Long) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
        if (artworks.isEmpty()) {
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("등록된 작품이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(Modifier.padding(p).fillMaxSize()) {
                items(artworks, key = { it.artwork.id }) { card ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onOpenArtwork(card.artwork.id) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Poster(card.images.firstOrNull()?.localPath, Modifier.size(76.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(card.artwork.title, fontWeight = FontWeight.Bold); card.artist?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailScreen(vm: AppViewModel, artistId: Long, onBack: () -> Unit, onOpenArtwork: (Long) -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val artworks by vm.artworksForArtist(artistId).collectAsStateWithLifecycle(initialValue = emptyList())
    ArtworkListScreen(artists.firstOrNull { it.id == artistId }?.name ?: "작가", artworks, onBack, onOpenArtwork)
}

@Composable
private fun TagDetailScreen(vm: AppViewModel, tagId: Long, onBack: () -> Unit, onOpenArtwork: (Long) -> Unit) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    val artworks by vm.artworksForTag(tagId).collectAsStateWithLifecycle(initialValue = emptyList())
    ArtworkListScreen(tags.firstOrNull { it.id == tagId }?.name?.let { "#$it" } ?: "태그", artworks, onBack, onOpenArtwork)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistScreen(vm: AppViewModel, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    val wishlist by vm.wishlist.collectAsStateWithLifecycle()
    val gridColumns by vm.gridColumns.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("위시리스트") }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "가고싶은 전시 추가") } }
    ) { p ->
        if (wishlist.isEmpty()) {
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("가고싶은 전시가 없습니다.", "다녀오고 싶은 전시를 저장해보세요.", onAdd)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(gridColumns), contentPadding = p, modifier = Modifier.fillMaxSize()) {
                items(wishlist, key = { it.id }) { Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) { ExhibitionGridItem(it, onOpen) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistCreateScreen(vm: AppViewModel, onBack: () -> Unit, onDone: (Long) -> Unit) {
    var title by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }; var endDate by remember { mutableStateOf("") }; var officialUrl by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }; var imagePath by remember { mutableStateOf<String?>(null) }; var importUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope(); val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { imagePath = vm.fileStore.copyImage(it) } } }
    fun applyImport(info: ExhibitionImportInfo) {
        info.title?.let { title = it }
        info.venueName?.let { venue = it }; info.description?.let { description = it }
        info.startDate?.let { startDate = it }; info.endDate?.let { endDate = it }; info.officialUrl?.let { officialUrl = it }
        info.posterImageUrl?.let { url -> scope.launch { vm.fileStore.downloadImage(url)?.let { imagePath = it } } }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("가고싶은 전시 추가") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
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
                    DateField(startDate, { startDate = it }, "전시 시작일", Modifier.weight(1f))
                    DateField(endDate, { endDate = it }, "전시 종료일", Modifier.weight(1f))
                }
            }
            item { OutlinedTextField(venue, { venue = it }, label = { Text("장소") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(officialUrl, { officialUrl = it }, label = { Text("공식 링크") }, modifier = Modifier.fillMaxWidth()) }
            item { TagInputField(tags) { tags = it } }
            item {
                Button(
                    onClick = { vm.createWishlist(title, imagePath, venue, tags, description, startDate.ifBlank { null }, endDate.ifBlank { null }, officialUrl.ifBlank { null }, onDone = onDone) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("저장") }
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
    val gridColumns by vm.gridColumns.collectAsStateWithLifecycle()
    var sliderValue by remember(fontScale) { mutableFloatStateOf(fontScale) }
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }) }) { p ->
        Column(Modifier.padding(p).padding(20.dp).imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("화면", style = MaterialTheme.typography.titleLarge)
            Text("글자 크기 (%.2f배)".format(sliderValue), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = sliderValue, onValueChange = { sliderValue = it }, onValueChangeFinished = { vm.setFontScale(sliderValue) }, valueRange = 0.85f..2f)
            TextButton(onClick = { sliderValue = 1f; vm.setFontScale(1f) }) { Text("기본값으로") }
            Text("한 줄에 표시할 전시 수", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { n -> FilterChip(selected = gridColumns == n, onClick = { vm.setGridColumns(n) }, label = { Text("${n}열") }) }
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
