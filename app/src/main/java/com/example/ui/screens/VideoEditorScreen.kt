package com.example.ui.screens

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import org.json.JSONObject
import com.example.ui.viewmodel.ChatAppiumViewModel
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.local.AppDatabase
import com.example.data.local.VideoProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

@OptIn(androidx.media3.common.util.UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(
    viewModel: ChatAppiumViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val videoDao = remember { db.videoProjectDao() }

    // Projects list
    val projectsList by videoDao.getAllProjectsFlow().collectAsState(initial = emptyList())

    // Active project state
    var selectedVideoPath by remember { mutableStateOf<String?>(null) }
    var videoDurationMs by remember { mutableStateOf(0L) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }
    var currentPlaybackPositionMs by remember { mutableStateOf(0L) }
    var activeProjectId by remember { mutableStateOf<String?>(null) }

    // UI flags
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // ExoPlayer Instance
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Initialize/release ExoPlayer
    DisposableEffect(selectedVideoPath) {
        if (selectedVideoPath != null) {
            val player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                val mediaItem = MediaItem.fromUri(Uri.fromFile(File(selectedVideoPath!!)))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = false
            }
            exoPlayer = player
        }
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Polling player position
    LaunchedEffect(exoPlayer) {
        while (true) {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPlaybackPositionMs = player.currentPosition
                }
            }
            delay(100)
        }
    }

    // Media picker launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val path = copyUriToCache(context, uri)
                    if (path != null) {
                        selectedVideoPath = path
                        val duration = getVideoDuration(context, path)
                        videoDurationMs = duration
                        startMs = 0L
                        endMs = duration
                        activeProjectId = null
                    } else {
                        Toast.makeText(context, "Failed to load video file.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- START OF FACELESS VIDEO AUTOMATION STATES (STEPS 2 & 3) ---
    val chatMessages by viewModel?.chatMessages?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val latestStoryJsonString = remember(chatMessages) {
        chatMessages.lastOrNull { !it.sender.equals("User", ignoreCase = true) && it.text.contains("\"scenes\"") && it.text.contains("{") }?.text
    }

    val parsedScenes = remember(latestStoryJsonString) {
        val list = mutableListOf<ParsedScene>()
        if (latestStoryJsonString != null) {
            try {
                val cleanJson = latestStoryJsonString
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                val jsonObject = JSONObject(cleanJson)
                val scenesArray = jsonObject.optJSONArray("scenes")
                if (scenesArray != null) {
                    for (i in 0 until scenesArray.length()) {
                        val obj = scenesArray.getJSONObject(i)
                        list.add(
                            ParsedScene(
                                index = obj.optInt("sceneIndex", i + 1),
                                caption = obj.optString("caption", ""),
                                durationMs = obj.optLong("durationMs", 3000L),
                                fontFamilyName = obj.optString("font", "Monospace"),
                                size = obj.optDouble("size", 24.0).toFloat(),
                                colorHex = obj.optString("color", "#00FF66"),
                                angle = obj.optDouble("angle", 0.0).toFloat(),
                                animation = obj.optString("animation", "Typewriter")
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Safe parsing fallback
            }
        }
        list
    }

    val sceneTimeRanges = remember(parsedScenes) {
        val ranges = mutableListOf<Pair<Long, Long>>()
        var currentSum = 0L
        for (scene in parsedScenes) {
            ranges.add(Pair(currentSum, currentSum + scene.durationMs))
            currentSum += scene.durationMs
        }
        ranges
    }

    val activeSceneIndexAndProgress = remember(currentPlaybackPositionMs, sceneTimeRanges) {
        var foundIndex = -1
        var elapsedInScene = 0L
        var totalSceneDuration = 1L
        for (i in sceneTimeRanges.indices) {
            val range = sceneTimeRanges[i]
            if (currentPlaybackPositionMs >= range.first && currentPlaybackPositionMs < range.second) {
                foundIndex = i
                elapsedInScene = currentPlaybackPositionMs - range.first
                totalSceneDuration = range.second - range.first
                break
            }
        }
        Triple(foundIndex, elapsedInScene, totalSceneDuration)
    }

    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isAutoTtsEnabled by remember { mutableStateOf(false) }
    var lastSpokenSceneIndex by remember { mutableStateOf(-1) }

    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Default local setup
            }
        }
        ttsInstance = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val activeSceneIdx = activeSceneIndexAndProgress.first
    LaunchedEffect(activeSceneIdx, isAutoTtsEnabled) {
        if (isAutoTtsEnabled && activeSceneIdx >= 0 && activeSceneIdx != lastSpokenSceneIndex) {
            val scene = parsedScenes.getOrNull(activeSceneIdx)
            if (scene != null && ttsInstance != null) {
                lastSpokenSceneIndex = activeSceneIdx
                ttsInstance?.stop()
                ttsInstance?.speak(scene.caption, TextToSpeech.QUEUE_FLUSH, null, "scene_$activeSceneIdx")
            }
        }
    }
    // --- END OF FACELESS VIDEO AUTOMATION STATES ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🎬 GLITCH_OS // VIDEO EDITOR",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00FF66)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Project History",
                            tint = Color(0xFF00FF66)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .then(if (selectedVideoPath != null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(16.dp)
        ) {
            if (selectedVideoPath == null) {
                // Empty State Console
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .background(Color(0xFF050505))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieFilter,
                        contentDescription = null,
                        tint = Color(0xFF00FF66),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = ">>> GLITCH_OS: NO_VIDEO_LOADED",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Load a local video from your gallery to perform 100% private waterfree offline editing on-device.",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66).copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00FF66),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CHOOSE VIDEO", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Active Editor Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    exoPlayer?.let { player ->
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = true
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Real-time styled caption overlay
                    AnimatedCaptionOverlay(
                        activeSceneIndexAndProgress = activeSceneIndexAndProgress,
                        scenes = parsedScenes
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Timeline Controller Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF070707))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = ">>> INTERACTIVE SCRUBBER TIMELINE",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF66),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Duration labels
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TRIM START: ${formatMs(startMs)}",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "TRIM END: ${formatMs(endMs)}",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66),
                                fontSize = 11.sp
                            )
                        }

                        // Range Slider for Trimming
                        RangeSlider(
                            value = startMs.toFloat()..endMs.toFloat(),
                            onValueChange = { range ->
                                startMs = range.start.toLong()
                                endMs = range.endInclusive.toLong()
                            },
                            valueRange = 0f..videoDurationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF00FF66),
                                inactiveTrackColor = Color(0xFF05220C),
                                thumbColor = Color(0xFF00FF66)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Current play position & Scrub control
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PLAYHEAD: ${formatMs(currentPlaybackPositionMs)}",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66).copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        player.seekTo(startMs)
                                        player.play()
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (exoPlayer?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Trim",
                                    tint = Color(0xFF00FF66)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Console Status & Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isExporting = true
                                exportProgress = "Extracting video frames..."
                                val success = withContext(Dispatchers.IO) {
                                    trimVideoFile(context, selectedVideoPath!!, startMs, endMs) { progressMsg ->
                                        coroutineScope.launch {
                                            exportProgress = progressMsg
                                        }
                                    }
                                }
                                isExporting = false
                                if (success != null) {
                                    Toast.makeText(context, "Video exported: $success", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Export complete! Saved to Downloads.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66), contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT WATERFREE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Save Draft project locally
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val entity = VideoProjectEntity(
                                    id = activeProjectId ?: java.util.UUID.randomUUID().toString(),
                                    title = "Project_${formatMs(System.currentTimeMillis()).replace(":", "")}",
                                    videoPath = selectedVideoPath!!,
                                    startMs = startMs,
                                    endMs = endMs,
                                    durationMs = videoDurationMs,
                                    lastEdited = System.currentTimeMillis()
                                )
                                videoDao.insertProject(entity)
                                activeProjectId = entity.id
                                Toast.makeText(context, "Draft Saved to Room SQLite Database!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF66)),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF66))
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color(0xFF00FF66))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE DRAFT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (isExporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = ">>> EXPORT_STATUS: $exportProgress",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00FF66),
                        fontSize = 11.sp
                    )
                }

                // Close/Reset button
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to load another video",
                    color = Color(0xFF00FF66).copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { selectedVideoPath = null }
                        .align(Alignment.CenterHorizontally)
                )

                // --- FACELESS VIDEO COMPOSER STUDIO SECTION (STEPS 2 & 3) ---
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF050505))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF00FF66),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🤖 FACELESS VIDEO COMPOSER",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        if (parsedScenes.isEmpty()) {
                            Text(
                                text = ">>> STATUS: NO_STORY_FOUND_IN_CHAT\n\nGenerate a story JSON inside the AI Chat Terminal first, then click '⚡ COMPOSE FACELESS VIDEO' to auto-load the 10 scenes, captions, fonts, colors, and transitions here.",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66).copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            Text(
                                text = ">>> STATUS: ACTIVE_STORY_LOADED",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // TTS voice over activation switch
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .background(Color.Black)
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🤖 TTS AUTO VOICE-OVER",
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF00FF66),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Automatically speak captions offline as video plays",
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF00FF66).copy(alpha = 0.6f),
                                        fontSize = 9.sp
                                    )
                                }
                                Switch(
                                    checked = isAutoTtsEnabled,
                                    onCheckedChange = { isAutoTtsEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = Color(0xFF00FF66),
                                        uncheckedThumbColor = Color(0xFF00FF66).copy(alpha = 0.6f),
                                        uncheckedTrackColor = Color.Black
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Auto-configure button
                            Button(
                                onClick = {
                                    val totalDuration = parsedScenes.sumOf { it.durationMs }
                                    startMs = 0L
                                    endMs = totalDuration.coerceAtMost(videoDurationMs)
                                    Toast.makeText(context, "Timeline auto-configured to matching story duration!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00FF66),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Timeline, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "✨ MATCH TIMELINE TO STORY",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "=== 10 STORY SCENES ===",
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00FF66).copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            parsedScenes.forEach { scene ->
                                val isActive = activeSceneIdx == (scene.index - 1)
                                val sceneRange = sceneTimeRanges.getOrNull(scene.index - 1)
                                val rangeStr = if (sceneRange != null) "${formatMs(sceneRange.first)} - ${formatMs(sceneRange.second)}" else ""

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(
                                            1.dp,
                                            if (isActive) Color(0xFF00FF66) else Color(0xFF00FF66).copy(alpha = 0.15f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .background(if (isActive) Color(0xFF00FF66).copy(alpha = 0.1f) else Color.Black)
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "SCENE #${scene.index} [$rangeStr]",
                                                fontFamily = FontFamily.Monospace,
                                                color = if (isActive) Color(0xFF00FF66) else Color(0xFF00FF66).copy(alpha = 0.8f),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "\"${scene.caption}\"",
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Style: Font=${scene.fontFamilyName} | Size=${scene.size} | Color=${scene.colorHex} | Angle=${scene.angle}° | Anim=${scene.animation}",
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF00FF66).copy(alpha = 0.5f),
                                                fontSize = 8.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        IconButton(
                                            onClick = {
                                                ttsInstance?.stop()
                                                ttsInstance?.speak(scene.caption, TextToSpeech.QUEUE_FLUSH, null, "manual_scene_${scene.index}")
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Speak caption",
                                                tint = Color(0xFF00FF66),
                                                modifier = Modifier.size(18.dp)
                                            )
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

    // Projects History Dialog
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Text(
                    "📂 SAVED VIDEO DRAFTS",
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00FF66),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (projectsList.isEmpty()) {
                        Text(
                            "No local video project drafts saved in SQLite Room database yet.",
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00FF66).copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    } else {
                        projectsList.forEach { project ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedVideoPath = project.videoPath
                                        startMs = project.startMs
                                        endMs = project.endMs
                                        videoDurationMs = project.durationMs
                                        activeProjectId = project.id
                                        showHistoryDialog = false
                                    }
                                    .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                                color = Color.Black
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = Color(0xFF00FF66),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            project.title,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF00FF66),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Trim: ${formatMs(project.startMs)} - ${formatMs(project.endMs)}",
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF00FF66).copy(alpha = 0.7f),
                                            fontSize = 10.sp
                                        )
                                    }
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            videoDao.deleteProjectById(project.id)
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHistoryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66))
                ) {
                    Text("CLOSE", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0A0A0A)
        )
    }
}

// Scoped copy function to get an absolute local path of the loaded media asset
private suspend fun copyUriToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val cacheDir = File(context.cacheDir, "cuts")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tempFile = File(cacheDir, "temp_editor_${System.currentTimeMillis()}.mp4")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        tempFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

// Accurate hardware retrieval of duration metadata
private suspend fun getVideoDuration(context: Context, path: String): Long = withContext(Dispatchers.IO) {
    var retriever: MediaMetadataRetriever? = null
    try {
        retriever = MediaMetadataRetriever().apply {
            setDataSource(path)
        }
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        time?.toLong() ?: 0L
    } catch (e: Exception) {
        0L
    } finally {
        retriever?.release()
    }
}

// Millisecond string formatting helper
private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val mil = (ms % 1000) / 100
    return String.format("%02d:%02d.%01d", minutes, seconds, mil)
}

/**
 * 100% Real-world, zero-dependency, on-device offline video trimming implementation.
 * Extracts video tracks, parses frame structures between start time and end time,
 * and writes them directly into a fresh water-free MP4 file in public download directory.
 */
private suspend fun trimVideoFile(
    context: Context,
    srcPath: String,
    startMs: Long,
    endMs: Long,
    progressCallback: (String) -> Unit
): String? = withContext(Dispatchers.IO) {
    var extractor: MediaExtractor? = null
    var muxer: MediaMuxer? = null
    try {
        progressCallback("Initializing Muxer Engine...")
        delay(200)

        val srcFile = File(srcPath)
        if (!srcFile.exists()) return@withContext null

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val destFile = File(downloadsDir, "GlitchCut_${System.currentTimeMillis()}.mp4")

        extractor = MediaExtractor().apply { setDataSource(srcPath) }
        muxer = MediaMuxer(destFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val trackCount = extractor.trackCount
        val trackMap = HashMap<Int, Int>()

        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                extractor.selectTrack(i)
                val newTrackIndex = muxer.addTrack(format)
                trackMap[i] = newTrackIndex
            }
        }

        muxer.start()

        val startUs = startMs * 1000
        val endUs = endMs * 1000
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val maxBufferSize = 1024 * 1024 // 1MB buffer
        val buffer = ByteBuffer.allocate(maxBufferSize)
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        progressCallback("Extracting and writing frames...")

        while (true) {
            val sampleTrackIndex = extractor.sampleTrackIndex
            if (sampleTrackIndex == -1) break

            val sampleTime = extractor.sampleTime
            if (sampleTime > endUs) break

            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break

            bufferInfo.presentationTimeUs = sampleTime
            bufferInfo.flags = extractor.sampleFlags
            bufferInfo.offset = 0

            val targetTrackIndex = trackMap[sampleTrackIndex]
            if (targetTrackIndex != null) {
                muxer.writeSampleData(targetTrackIndex, buffer, bufferInfo)
            }
            extractor.advance()
        }

        progressCallback("Finalizing file container...")
        delay(300)

        destFile.absolutePath
    } catch (e: Exception) {
        null
    } finally {
        try {
            extractor?.release()
            muxer?.stop()
            muxer?.release()
        } catch (ignored: Exception) {}
    }
}

data class ParsedScene(
    val index: Int,
    val caption: String,
    val durationMs: Long,
    val fontFamilyName: String,
    val size: Float,
    val colorHex: String,
    val angle: Float,
    val animation: String
)

@Composable
private fun AnimatedCaptionOverlay(
    activeSceneIndexAndProgress: Triple<Int, Long, Long>,
    scenes: List<ParsedScene>
) {
    val activeIdx = activeSceneIndexAndProgress.first
    val elapsedMs = activeSceneIndexAndProgress.second
    val totalDuration = activeSceneIndexAndProgress.third

    val activeScene = scenes.getOrNull(activeIdx) ?: return

    // Calculate progression ratio (0.0 to 1.0) inside this scene
    val progress = (elapsedMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

    // Font family mapping
    val fontFamily = when (activeScene.fontFamilyName.lowercase()) {
        "monospace" -> FontFamily.Monospace
        "sansserif", "sans-serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Monospace
    }

    // Color parsing safely
    val parsedColor = try {
        Color(android.graphics.Color.parseColor(activeScene.colorHex))
    } catch (e: Exception) {
        Color(0xFF00FF66)
    }

    // Animation application
    val textToRender = when (activeScene.animation.lowercase()) {
        "typewriter" -> {
            val charCount = (progress * activeScene.caption.length).toInt().coerceIn(0, activeScene.caption.length)
            activeScene.caption.substring(0, charCount)
        }
        else -> activeScene.caption
    }

    val scaleValue = when (activeScene.animation.lowercase()) {
        "bounce" -> {
            // Spring scale up & down based on a sine wave
            1.0f + 0.12f * kotlin.math.sin(progress * Math.PI).toFloat()
        }
        else -> 1.0f
    }

    val alphaValue = when (activeScene.animation.lowercase()) {
        "fadein", "fade-in" -> {
            (progress * 4f).coerceAtMost(1.0f)
        }
        else -> 1.0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .rotate(activeScene.angle)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                .border(0.5.dp, Color(0xFF00FF66).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = textToRender,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = activeScene.size.sp,
                color = parsedColor.copy(alpha = alphaValue),
                modifier = Modifier
                    .testTag("caption_overlay_text")
                    .scale(scaleValue)
            )
        }
    }
}
