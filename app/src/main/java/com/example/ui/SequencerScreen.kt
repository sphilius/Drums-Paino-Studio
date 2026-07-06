package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.AudioEngine
import com.example.audio.DrumSound
import com.example.audio.DrumVoiceParams
import com.example.audio.MusicTheory
import com.example.audio.ScaleType
import com.example.audio.Waveform
import com.example.database.ProjectEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// High-fidelity Frosted Glass Theme Palette
val StudioDarkBg = Color(0xFF0F1115) // Deep Slate-Black
val StudioCardBg = Color(0x3D1E293B) // slate-800/24 Translucent Glass
val CyberPink = Color(0xFFF43F5E) // Frosted Rose Pink
val CyberBlue = Color(0xFF6366F1) // Frosted Indigo Blue
val CyberGreen = Color(0xFF10B981) // Frosted Emerald Green
val CyberOrange = Color(0xFFF59E0B) // Frosted Amber Yellow
val DarkGrey = Color(0x1F94A3B8) // Translucent Slate-Gray

// Glassmorphism Extension Modifier
fun Modifier.frostedGlass(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp
): Modifier = this
    .background(StudioCardBg, shape)
    .border(borderWidth, Color(0x1AFFFFFF), shape)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequencerScreen(viewModel: SequencerViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val bpm by viewModel.bpm.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isRecordingSeq by viewModel.isRecordingSeq.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val currentProjectName by viewModel.currentProjectName.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()

    // Save project dialog
    var showSaveDialog by remember { mutableStateOf(false) }
    var projectNameInput by remember { mutableStateOf("") }

    // Display Toast on status update
    LaunchedEffect(exportStatus) {
        exportStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

    // Adaptive layout: tablets, foldables and Chromebook/desktop windows get a side
    // NavigationRail instead of a bottom bar so the tactile controls stay reachable.
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 700

    val tabs = listOf(
        NavigationItem("Pads", Icons.Default.GridOn, 0),
        NavigationItem("Seq", Icons.Default.LinearScale, 1),
        NavigationItem("Piano", Icons.Default.Piano, 2),
        NavigationItem("Vocal", Icons.Default.Mic, 3),
        NavigationItem("VST FX", Icons.Default.Tune, 4),
        NavigationItem("Mixer", Icons.Default.Equalizer, 5),
        NavigationItem("Sync", Icons.Default.SyncAlt, 6),
        NavigationItem("Library", Icons.Default.LibraryMusic, 7)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "BEATCRAFT WORKSTATION",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CyberBlue,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Active: $currentProjectName",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Save Button
                    IconButton(onClick = {
                        projectNameInput = currentProjectName
                        showSaveDialog = true
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Project", tint = Color.White)
                    }

                    // Clear Sequencer Button
                    IconButton(onClick = { viewModel.clearGrid() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear Sequence", tint = CyberPink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xCC0F172A), // slate-900/80 header glass
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            if (!isWideScreen) {
                // Elegant Navigation bar for studio tabs (phones/portrait tablets)
                NavigationBar(
                    containerColor = Color(0xCC0F1115), // slate-950/80 bottom glass
                    tonalElevation = 8.dp,
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = Color(0x1AFFFFFF), // white with 10% opacity top border
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 2f
                        )
                    }
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab.index,
                            onClick = { viewModel.selectTab(tab.index) },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberBlue,
                                selectedTextColor = CyberBlue,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = StudioCardBg
                            )
                        )
                    }
                }
            }
        },
        containerColor = StudioDarkBg
    ) { innerPadding ->

        Row(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isWideScreen) {
                // Desktop/tablet workstation layout: a persistent side rail keeps every
                // studio module one tap away, matching how a DAW keeps its module switcher visible.
                NavigationRail(
                    containerColor = Color(0xCC0F1115),
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = Color(0x1AFFFFFF),
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2f
                        )
                    }
                ) {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = activeTab == tab.index,
                            onClick = { viewModel.selectTab(tab.index) },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = CyberBlue,
                                selectedTextColor = CyberBlue,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = StudioCardBg
                            )
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioDarkBg)
            ) {
                // Master Clock & Transport Bar
                TransportBar(
                    bpm = bpm,
                    isPlaying = isPlaying,
                    isRecording = isRecordingSeq,
                    currentStep = currentStep,
                    maxSteps = viewModel.getBarCount() * 16,
                    onPlayToggle = { viewModel.togglePlayback() },
                    onRecordToggle = { viewModel.toggleRecording() },
                    onBpmChange = { viewModel.updateBpm(it) },
                    onExportWav = { viewModel.exportToDeviceWav(context) },
                    onExportStems = { viewModel.exportStems(context) },
                    onExportMidi = { viewModel.exportMidiFile(context) }
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

                // Dynamic Tab Workspace
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        0 -> DrumPadsView(viewModel)
                        1 -> DrumSequencerView(viewModel)
                        2 -> PianoRollView(viewModel)
                        3 -> VocalRecorderView(viewModel)
                        4 -> VstFxRackView(viewModel)
                        5 -> MixerConsoleView(viewModel)
                        6 -> CloudSyncView(viewModel)
                        7 -> CommunityLibraryView(viewModel)
                    }
                }
            }
        }
    }

    // Save Project Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Sequencer Project", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = projectNameInput,
                    onValueChange = { projectNameInput = it },
                    label = { Text("Project Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyberBlue,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (projectNameInput.isNotBlank()) {
                            viewModel.saveProject(projectNameInput)
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = StudioCardBg
        )
    }
}

data class NavigationItem(val title: String, val icon: ImageVector, val index: Int)

/**
 * High-fidelity Master Transport Controls Row
 */
@Composable
fun TransportBar(
    bpm: Int,
    isPlaying: Boolean,
    isRecording: Boolean,
    currentStep: Int,
    maxSteps: Int,
    onPlayToggle: () -> Unit,
    onRecordToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onExportWav: () -> Unit,
    onExportStems: () -> Unit,
    onExportMidi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC0F172A)) // slate-900/80 header glass
            .drawBehind {
                drawLine(
                    color = Color(0x1AFFFFFF), // white with 10% opacity border
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Play & Record Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) CyberGreen.copy(alpha = 0.2f) else DarkGrey)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = if (isPlaying) CyberGreen else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onRecordToggle,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) CyberPink.copy(alpha = 0.2f) else DarkGrey)
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = "Record notes",
                        tint = if (isRecording) CyberPink else Color.LightGray,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Realtime Step Pointer LED Display
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .background(Color.Black, RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("STEP %02d/%02d", currentStep + 1, maxSteps),
                    color = CyberOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Real-time Export Menu: full mix WAV, isolated stems, or a Standard MIDI File
            Box {
                var showExportMenu by remember { mutableStateOf(false) }
                Button(
                    onClick = { showExportMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, CyberBlue),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Download, contentDescription = "Export", tint = CyberBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXPORT", color = CyberBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
                DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Full Mix (WAV)") },
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        onClick = { showExportMenu = false; onExportWav() }
                    )
                    DropdownMenuItem(
                        text = { Text("Stems (Drums/Synth/Vocal)") },
                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
                        onClick = { showExportMenu = false; onExportStems() }
                    )
                    DropdownMenuItem(
                        text = { Text("MIDI File (.mid)") },
                        leadingIcon = { Icon(Icons.Default.Piano, contentDescription = null) },
                        onClick = { showExportMenu = false; onExportMidi() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // BPM Slider Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TEMPO: $bpm BPM",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(110.dp)
            )

            Slider(
                value = bpm.toFloat(),
                onValueChange = { onBpmChange(it.toInt()) },
                valueRange = 60f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = CyberBlue,
                    activeTrackColor = CyberBlue,
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Tab 0: 4x4 Velocity Drum Pads View
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DrumPadsView(viewModel: SequencerViewModel) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var padVelocity by remember { mutableStateOf(1.0f) }
    var soundDesignSound by remember { mutableStateOf<DrumSound?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "MPC LIVE PERFORMANCE PADS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Custom 4x4 Drum Grid Layout
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val drumPads = listOf(
                DrumSound.KICK, DrumSound.SNARE, DrumSound.HIHAT_CLOSED, DrumSound.HIHAT_OPEN,
                DrumSound.CLAP, DrumSound.TOM, DrumSound.KICK, DrumSound.SNARE, // repeat or placeholders for custom
                DrumSound.HIHAT_CLOSED, DrumSound.HIHAT_OPEN, DrumSound.CLAP, DrumSound.TOM,
                DrumSound.KICK, DrumSound.SNARE, DrumSound.HIHAT_CLOSED, DrumSound.HIHAT_OPEN
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(drumPads.zip(0 until 16)) { (sound, index) ->
                    val colorAccent = when (sound) {
                        DrumSound.KICK -> CyberPink
                        DrumSound.SNARE -> CyberBlue
                        DrumSound.HIHAT_CLOSED, DrumSound.HIHAT_OPEN -> CyberGreen
                        DrumSound.CLAP, DrumSound.TOM -> CyberOrange
                    }

                    // Pad tap file launcher
                    val samplePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri ->
                        uri?.let { viewModel.importSampleForPad(context, sound, it) }
                    }

                    // Tactile feedback: pads shrink slightly and buzz on tap, like hardware MPC pads
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val padScale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "padScale")

                    Box(
                        modifier = Modifier
                            .aspectRatio(1.0f)
                            .graphicsLayer { scaleX = padScale; scaleY = padScale }
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioCardBg)
                            .border(1.dp, colorAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.playDrumPad(sound)
                                },
                                onLongClick = { samplePickerLauncher.launch("audio/*") }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Album,
                                contentDescription = "Pad",
                                tint = colorAccent.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sound.displayName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PAD ${index + 1}",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Sound Design: tune this pad's procedural synthesis (tune/decay/tone)
                        IconButton(
                            onClick = { soundDesignSound = sound },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Sound design for ${sound.displayName}",
                                tint = Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Velocity Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioCardBg, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Waves, contentDescription = "Velocity", tint = CyberPink, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = String.format("PAD VELOCITY: %d%%", (padVelocity * 100).toInt()),
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(140.dp)
            )
            Slider(
                value = padVelocity,
                onValueChange = { padVelocity = it },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = CyberPink,
                    activeTrackColor = CyberPink,
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.weight(1f)
            )
        }
        
        Text(
            text = "Tip: Long-press a pad to import a custom sample, tap the tune icon to sound-design it.",
            color = Color.Gray,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    soundDesignSound?.let { sound ->
        SoundDesignDialog(
            sound = sound,
            initialParams = viewModel.getDrumVoiceParams(sound),
            viewModel = viewModel,
            onDismiss = { soundDesignSound = null }
        )
    }
}

/**
 * Sound design panel for a single drum pad: tune/decay/tone map onto that voice's
 * procedural synthesis recipe and preview live, so shaping a kit is a hands-on,
 * hear-it-as-you-turn-the-knob workflow rather than picking from a static sample list.
 */
@Composable
fun SoundDesignDialog(
    sound: DrumSound,
    initialParams: DrumVoiceParams,
    viewModel: SequencerViewModel,
    onDismiss: () -> Unit
) {
    var tune by remember { mutableStateOf(initialParams.tune) }
    var decay by remember { mutableStateOf(initialParams.decay) }
    var tone by remember { mutableStateOf(initialParams.tone) }

    fun push(newTune: Float = tune, newDecay: Float = decay, newTone: Float = tone) {
        viewModel.updateDrumVoiceParams(sound, DrumVoiceParams(newTune, newDecay, newTone))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound Design: ${sound.displayName}", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Shape this pad's synthesis — every change previews instantly.",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Tune: ${String.format("%.1f", tune)} st", color = CyberBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = tune,
                    onValueChange = { tune = it; push(newTune = it) },
                    valueRange = -12f..12f,
                    colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
                )

                Text("Decay: ${String.format("%.2f", decay)}x", color = CyberPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = decay,
                    onValueChange = { decay = it; push(newDecay = it) },
                    valueRange = 0.3f..2.5f,
                    colors = SliderDefaults.colors(thumbColor = CyberPink, activeTrackColor = CyberPink)
                )

                Text("Tone: ${String.format("%.2f", tone)}", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = tone,
                    onValueChange = { tone = it; push(newTone = it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(thumbColor = CyberGreen, activeTrackColor = CyberGreen)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.playDrumPad(sound) },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGrey)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Preview Pad", color = Color.White)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = CyberBlue, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = {
                tune = 0f; decay = 1f; tone = 0.5f
                push(0f, 1f, 0.5f)
            }) { Text("Reset", color = Color.Gray) }
        },
        containerColor = StudioCardBg
    )
}

/**
 * Tab 1: Multi-track Drum Step Sequencer Grid
 */
@Composable
fun DrumSequencerView(viewModel: SequencerViewModel) {
    val barCount = viewModel.getBarCount()
    val totalSteps = barCount * 16

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Bar selector header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DRUM GRID SEQUENCER",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(1, 2, 4).forEach { bars ->
                    Button(
                        onClick = { viewModel.updateBarCount(bars) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (barCount == bars) CyberBlue else DarkGrey
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(28.dp)
                    ) {
                        Text(
                            "${bars}B",
                            color = if (barCount == bars) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Rapid workflow: clone the previous bar forward instead of re-tapping every step
                IconButton(
                    onClick = { viewModel.duplicateLastBar() },
                    enabled = barCount > 1,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Duplicate previous bar",
                        tint = if (barCount > 1) CyberOrange else Color.DarkGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal scrolling grid
        val state = rememberScrollState()
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(state)
        ) {
            Column {
                // Steps label header (1..16/32/64)
                Row(modifier = Modifier.padding(bottom = 6.dp)) {
                    Box(modifier = Modifier.width(122.dp)) // spacer for sound name + mute/randomize column
                    for (step in 0 until totalSteps) {
                        val activeStep by viewModel.currentStep.collectAsStateWithLifecycle()
                        val isPlayhead = activeStep == step
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(28.dp)
                                .background(
                                    if (isPlayhead) CyberGreen.copy(alpha = 0.3f) else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (step + 1).toString(),
                                color = if (isPlayhead) CyberGreen else Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Grid tracks
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DrumSound.entries) { sound ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Track sound label + rapid-workflow mute/randomize actions
                            val isMuted = viewModel.isDrumMuted(sound)
                            Row(
                                modifier = Modifier
                                    .width(122.dp)
                                    .height(34.dp)
                                    .background(StudioCardBg, RoundedCornerShape(4.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleDrumMute(sound) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = if (isMuted) "Unmute ${sound.displayName}" else "Mute ${sound.displayName}",
                                        tint = if (isMuted) CyberPink else Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = sound.displayName,
                                    color = if (isMuted) Color.Gray else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.randomizeDrumTrack(sound) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = "Randomize ${sound.displayName}",
                                        tint = CyberGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Steps row
                            for (step in 0 until totalSteps) {
                                val isActive = viewModel.isDrumStepActive(sound, step)
                                val currentStepVal by viewModel.currentStep.collectAsStateWithLifecycle()
                                val isPlayhead = currentStepVal == step

                                val borderClr = if (isPlayhead) CyberGreen else Color.DarkGray
                                val bgClr = when {
                                    isActive -> CyberBlue
                                    isPlayhead -> Color.DarkGray.copy(alpha = 0.5f)
                                    else -> StudioCardBg
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(28.dp, 34.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(bgClr)
                                        .border(1.dp, borderClr, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.toggleDrumStep(sound, step) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: Melodic Piano Roll Sequencer
 */
@Composable
fun PianoRollView(viewModel: SequencerViewModel) {
    val synthWave by viewModel.synthWave.collectAsStateWithLifecycle()
    val scaleRoot by viewModel.scaleRoot.collectAsStateWithLifecycle()
    val scaleType by viewModel.scaleType.collectAsStateWithLifecycle()
    val scaleLockEnabled by viewModel.scaleLockEnabled.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val totalSteps = viewModel.getBarCount() * 16

    // MIDI Notes array: C4 to C5 (notes 60 to 72, backwards)
    val notes = (72 downTo 60).toList()
    val noteNames = mapOf(
        72 to "C5", 71 to "B4", 70 to "A#4", 69 to "A4", 68 to "G#4",
        67 to "G4", 66 to "F#4", 65 to "F4", 64 to "E4", 63 to "D#4",
        62 to "D4", 61 to "C#4", 60 to "C4"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Waveform selector header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioCardBg, RoundedCornerShape(6.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OSC WAVEFORM",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Row {
                Waveform.entries.forEach { wave ->
                    Button(
                        onClick = { viewModel.changeSynthWave(wave) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (synthWave == wave) CyberPink else DarkGrey
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(28.dp)
                    ) {
                        Text(
                            wave.name,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Melody assistance: pick a key/scale to highlight in-key notes, optionally lock to them
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioCardBg, RoundedCornerShape(6.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            var showKeyMenu by remember { mutableStateOf(false) }
            var showScaleMenu by remember { mutableStateOf(false) }

            Box {
                TextButton(onClick = { showKeyMenu = true }) {
                    Text("Key: ${MusicTheory.pitchClassNames[scaleRoot]}", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showKeyMenu, onDismissRequest = { showKeyMenu = false }) {
                    MusicTheory.pitchClassNames.forEachIndexed { index, name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { viewModel.setScaleRoot(index); showKeyMenu = false })
                    }
                }
            }

            Box {
                TextButton(onClick = { showScaleMenu = true }) {
                    Text(scaleType.displayName, color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showScaleMenu, onDismissRequest = { showScaleMenu = false }) {
                    ScaleType.entries.forEach { type ->
                        DropdownMenuItem(text = { Text(type.displayName) }, onClick = { viewModel.setScaleType(type); showScaleMenu = false })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scale Lock", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Switch(
                    checked = scaleLockEnabled,
                    onCheckedChange = { viewModel.toggleScaleLock() },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberGreen, checkedTrackColor = CyberGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Piano keyboard and grid space
        val scrollStateHorizontal = rememberScrollState()
        val scrollStateVertical = rememberScrollState()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row {
                // Vertical piano roll keys on the left (Stays anchored!)
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .verticalScroll(scrollStateVertical)
                ) {
                    Spacer(modifier = Modifier.height(30.dp)) // offset for step headers
                    notes.forEach { note ->
                        val isBlackKey = noteNameNames(note).contains("#")
                        val inScale = viewModel.isNoteInScale(note)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(if (isBlackKey) Color.Black else Color.White)
                                .border(if (inScale) 2.dp else 1.dp, if (inScale) CyberGreen else Color.Gray)
                                .clickable {
                                    AudioEngine.triggerSynthKey(note, 1.0f)
                                    // auto-release piano keys
                                    coroutineScope.launch {
                                        delay(200)
                                        AudioEngine.releaseSynthKey(note)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = noteNames[note] ?: "",
                                color = if (isBlackKey) Color.White else Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Interactive steps matrix
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollStateHorizontal)
                ) {
                    Column(modifier = Modifier.verticalScroll(scrollStateVertical)) {
                        // Steps header
                        Row(modifier = Modifier.height(30.dp)) {
                            for (step in 0 until totalSteps) {
                                val currentStepVal by viewModel.currentStep.collectAsStateWithLifecycle()
                                val isPlayhead = currentStepVal == step
                                Box(
                                    modifier = Modifier
                                        .width(32.dp)
                                        .fillMaxHeight()
                                        .background(
                                            if (isPlayhead) CyberGreen.copy(alpha = 0.25f) else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (step + 1).toString(),
                                        color = if (isPlayhead) CyberGreen else Color.Gray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Matrix grid
                        notes.forEach { note ->
                            val inScale = viewModel.isNoteInScale(note)
                            Row {
                                for (step in 0 until totalSteps) {
                                    val isActive = viewModel.isSynthStepActive(step, note)
                                    val currentStepVal by viewModel.currentStep.collectAsStateWithLifecycle()
                                    val isPlayhead = currentStepVal == step

                                    val bgClr = when {
                                        isActive -> CyberPink
                                        isPlayhead -> Color.DarkGray.copy(alpha = 0.4f)
                                        inScale -> StudioCardBg
                                        else -> StudioDarkBg
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(bgClr)
                                            .border(0.5.dp, Color.DarkGray)
                                            .clickable { viewModel.toggleSynthStep(step, note) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Synth ADSR sliders
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioCardBg),
            border = BorderStroke(1.dp, Color(0x1AFFFFFF)), // frosted glass border
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("SYNTH DECAY ENVELOPE (ADSR)", color = CyberBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdsrDial("A: ${String.format("%.2fs", AudioEngine.synthAttack)}", AudioEngine.synthAttack, 0.01f..1.0f) { AudioEngine.synthAttack = it }
                    AdsrDial("D: ${String.format("%.2fs", AudioEngine.synthDecay)}", AudioEngine.synthDecay, 0.05f..1.5f) { AudioEngine.synthDecay = it }
                    AdsrDial("S: ${String.format("%.1f", AudioEngine.synthSustain)}", AudioEngine.synthSustain, 0.0f..1.0f) { AudioEngine.synthSustain = it }
                    AdsrDial("R: ${String.format("%.2fs", AudioEngine.synthRelease)}", AudioEngine.synthRelease, 0.05f..2.0f) { AudioEngine.synthRelease = it }
                }
            }
        }
    }
}

private fun noteNameNames(note: Int): String {
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return names[note % 12]
}

@Composable
fun RowScope.AdsrDial(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(StudioDarkBg, RoundedCornerShape(4.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CyberPink,
                activeTrackColor = CyberPink,
                inactiveTrackColor = Color.DarkGray
            ),
            modifier = Modifier.height(18.dp)
        )
    }
}

/**
 * Tab 3: Realtime Vocal Track Microphone Recorder
 */
@Composable
fun VocalRecorderView(viewModel: SequencerViewModel) {
    val context = LocalContext.current
    var isRecordingMic by remember { mutableStateOf(false) }
    var micPermissionGranted by remember { mutableStateOf(false) }

    val recordLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
        if (!granted) {
            Toast.makeText(context, "Microphone permission is required to record vocals!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        micPermissionGranted = permissionCheck == PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "MULTITRACK MICROPHONE SAMPLER",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Record vocals or acoustic sounds over your beat sequences.",
                color = Color.Gray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Live visual equalizer wave
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .border(1.dp, if (isRecordingMic) CyberPink else Color.DarkGray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isRecordingMic) {
                // Recording animation waves
                val infiniteTransition = rememberInfiniteTransition(label = "wave")
                val waveHeight1 by infiniteTransition.animateFloat(
                    initialValue = 10f, targetValue = 90f,
                    animationSpec = infiniteRepeatable(animation = twinSpec(150), repeatMode = RepeatMode.Reverse),
                    label = "w1"
                )
                val waveHeight2 by infiniteTransition.animateFloat(
                    initialValue = 25f, targetValue = 130f,
                    animationSpec = infiniteRepeatable(animation = twinSpec(230), repeatMode = RepeatMode.Reverse),
                    label = "w2"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(waveHeight1, waveHeight2, waveHeight1 * 1.2f, waveHeight2 * 0.7f, waveHeight1 * 0.8f).forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(h.dp)
                                .background(CyberPink, RoundedCornerShape(4.dp))
                        )
                    }
                }
            } else {
                if (AudioEngine.isVocalEnabled) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, contentDescription = "Vocal recorded", tint = CyberGreen, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Vocal stem track loaded into memory!", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MicNone, contentDescription = "Mic idle", tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("MIC CAPTURE IDLE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Big Record Button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = {
                    if (!micPermissionGranted) {
                        recordLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        if (!isRecordingMic) {
                            isRecordingMic = true
                            AudioEngine.startVocalRecording()
                        } else {
                            isRecordingMic = false
                            AudioEngine.stopVocalRecording()
                        }
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (isRecordingMic) CyberPink.copy(alpha = 0.2f) else DarkGrey)
                    .border(2.dp, if (isRecordingMic) CyberPink else Color.LightGray, CircleShape)
            ) {
                Icon(
                    if (isRecordingMic) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Trigger Mic Record",
                    tint = if (isRecordingMic) CyberPink else Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRecordingMic) "TAP TO STOP RECORDING" else "TAP TO START RECORDING",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Playback Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Vocal channel active in mix", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Switch(
                checked = AudioEngine.isVocalEnabled,
                onCheckedChange = { AudioEngine.isVocalEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberGreen,
                    checkedTrackColor = CyberGreen.copy(alpha = 0.4f)
                )
            )
        }
    }
}

private fun twinSpec(duration: Int): TweenSpec<Float> {
    return tween(durationMillis = duration, easing = LinearEasing)
}

/**
 * Tab 4: VST FX Rack View (Effects Processor Nodes)
 */
@Composable
fun VstFxRackView(viewModel: SequencerViewModel) {
    var drumOverdriveMix by remember { mutableStateOf(AudioEngine.drumOverdrive.mix) }
    var drumOverdriveValue by remember { mutableStateOf(AudioEngine.drumOverdrive.drive) }
    var drumDelayWet by remember { mutableStateOf(AudioEngine.drumDelay.wet) }
    var drumReverbWet by remember { mutableStateOf(AudioEngine.drumReverb.wet) }

    var synthCutoff by remember { mutableStateOf(AudioEngine.synthFilter.cutoff) }
    var synthResonance by remember { mutableStateOf(AudioEngine.synthFilter.resonance) }
    var synthDelayWet by remember { mutableStateOf(AudioEngine.synthDelay.wet) }
    var synthReverbWet by remember { mutableStateOf(AudioEngine.synthReverb.wet) }

    val vstStatus by viewModel.vstStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VESSEL VST FX RACK",
                    color = CyberBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .background(CyberBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(vstStatus, color = CyberBlue, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Drum Effects Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DRUM CHANNEL VST CHAIN", color = CyberPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = AudioEngine.drumOverdrive.isEnabled,
                            onCheckedChange = {
                                AudioEngine.drumOverdrive.isEnabled = it
                                AudioEngine.drumDelay.isEnabled = it
                                AudioEngine.drumReverb.isEnabled = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyberPink, checkedTrackColor = CyberPink.copy(alpha = 0.4f))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tube Saturation Overdrive
                    Text("Overdrive Mix: ${(drumOverdriveMix * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = drumOverdriveMix,
                        onValueChange = {
                            drumOverdriveMix = it
                            AudioEngine.drumOverdrive.mix = it
                        },
                        colors = SliderDefaults.colors(thumbColor = CyberPink, activeTrackColor = CyberPink)
                    )

                    Text("Saturation Drive: ${String.format("%.1f", drumOverdriveValue)}x", color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = drumOverdriveValue,
                        onValueChange = {
                            drumOverdriveValue = it
                            AudioEngine.drumOverdrive.drive = it
                        },
                        valueRange = 1.0f..8.0f,
                        colors = SliderDefaults.colors(thumbColor = CyberPink, activeTrackColor = CyberPink)
                    )

                    // Delay / Reverb wet
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delay Wet: ${(drumDelayWet * 100).toInt()}%", color = Color.White, fontSize = 10.sp)
                            Slider(
                                value = drumDelayWet,
                                onValueChange = {
                                    drumDelayWet = it
                                    AudioEngine.drumDelay.wet = it
                                },
                                colors = SliderDefaults.colors(thumbColor = CyberPink, activeTrackColor = CyberPink)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reverb Wet: ${(drumReverbWet * 100).toInt()}%", color = Color.White, fontSize = 10.sp)
                            Slider(
                                value = drumReverbWet,
                                onValueChange = {
                                    drumReverbWet = it
                                    AudioEngine.drumReverb.wet = it
                                },
                                colors = SliderDefaults.colors(thumbColor = CyberPink, activeTrackColor = CyberPink)
                            )
                        }
                    }
                }
            }
        }

        // Synth Effects Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SYNTH ROLL VST CHAIN", color = CyberBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = AudioEngine.synthFilter.isEnabled,
                            onCheckedChange = {
                                AudioEngine.synthFilter.isEnabled = it
                                AudioEngine.synthDelay.isEnabled = it
                                AudioEngine.synthReverb.isEnabled = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyberBlue, checkedTrackColor = CyberBlue.copy(alpha = 0.4f))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Moog Resonant Low pass
                    Text("Lowpass Cutoff: ${synthCutoff.toInt()} Hz", color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = synthCutoff,
                        onValueChange = {
                            synthCutoff = it
                            AudioEngine.synthFilter.cutoff = it
                        },
                        valueRange = 100f..18000f,
                        colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
                    )

                    Text("Resonance (Q): ${String.format("%.1f", synthResonance)}", color = Color.White, fontSize = 11.sp)
                    Slider(
                        value = synthResonance,
                        onValueChange = {
                            synthResonance = it
                            AudioEngine.synthFilter.resonance = it
                        },
                        valueRange = 0.5f..5.0f,
                        colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
                    )

                    // Delay / Reverb wet
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Delay Wet: ${(synthDelayWet * 100).toInt()}%", color = Color.White, fontSize = 10.sp)
                            Slider(
                                value = synthDelayWet,
                                onValueChange = {
                                    synthDelayWet = it
                                    AudioEngine.synthDelay.wet = it
                                },
                                colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reverb Wet: ${(synthReverbWet * 100).toInt()}%", color = Color.White, fontSize = 10.sp)
                            Slider(
                                value = synthReverbWet,
                                onValueChange = {
                                    synthReverbWet = it
                                    AudioEngine.synthReverb.wet = it
                                },
                                colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 5: Professional Mixing Console View
 */
@Composable
fun MixerConsoleView(viewModel: SequencerViewModel) {
    var masterVol by remember { mutableStateOf(AudioEngine.masterVolume) }
    var drumVol by remember { mutableStateOf(AudioEngine.drumVolume) }
    var synthVol by remember { mutableStateOf(AudioEngine.synthVolume) }
    var vocalVol by remember { mutableStateOf(AudioEngine.vocalVolume) }

    // Dynamic level meter values triggered by background thread
    var peakL by remember { mutableStateOf(0f) }
    var peakR by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            peakL = AudioEngine.masterLevelL
            peakR = AudioEngine.masterLevelR
            delay(50) // refresh meter at 20fps for performance
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "STUDIO MIXING CONSOLE",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Channel 1: Drums
            MixerFaderColumn("DRUMS", drumVol, CyberPink) {
                drumVol = it
                AudioEngine.drumVolume = it
            }

            // Channel 2: Synth
            MixerFaderColumn("SYNTH", synthVol, CyberBlue) {
                synthVol = it
                AudioEngine.synthVolume = it
            }

            // Channel 3: Vocals
            MixerFaderColumn("VOCAL", vocalVol, CyberGreen) {
                vocalVol = it
                AudioEngine.vocalVolume = it
            }

            // Master Peak Level Output Meters
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .background(Color.Black, RoundedCornerShape(6.dp))
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("METERS", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // L Meter
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(8.dp)
                            .background(Color.DarkGray)
                            .drawBehind {
                                val height = size.height * peakL.coerceIn(0f, 1f)
                                drawRect(
                                    color = CyberGreen,
                                    topLeft = Offset(0f, size.height - height),
                                    size = Size(size.width, height)
                                )
                            }
                    )

                    // R Meter
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(8.dp)
                            .background(Color.DarkGray)
                            .drawBehind {
                                val height = size.height * peakR.coerceIn(0f, 1f)
                                drawRect(
                                    color = CyberGreen,
                                    topLeft = Offset(0f, size.height - height),
                                    size = Size(size.width, height)
                                )
                            }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("L / R", color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }

            // Channel 4: Master Output
            MixerFaderColumn("MASTER", masterVol, CyberOrange) {
                masterVol = it
                AudioEngine.masterVolume = it
            }
        }
    }
}

@Composable
fun RowScope.MixerFaderColumn(label: String, valFader: Float, colorTheme: Color, onValueChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 6.dp)
            .frostedGlass(RoundedCornerShape(8.dp))
            .border(1.dp, colorTheme.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colorTheme,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        // Custom Vertical Slider representation using Box and gesture detection
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Slider channel path line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color.Black, RoundedCornerShape(2.dp))
            )

            // Actual Compose vertical slider slider
            Slider(
                value = valFader,
                onValueChange = onValueChange,
                valueRange = 0f..1.2f, // permit subtle master gain overhead!
                colors = SliderDefaults.colors(
                    thumbColor = colorTheme,
                    activeTrackColor = colorTheme,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .asVerticalSlider() // Rotates the normal horizontal Material Slider vertical!
            )
        }

        Text(
            text = "${(valFader * 100).toInt()}%",
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Rotates a horizontal Material Slider into a vertical mixer fader: measures the slider
 * with swapped width/height constraints (so its drag track runs the fader's full length),
 * then rotates the rendered result 270° back into the vertical footprint the layout expects.
 */
fun Modifier.asVerticalSlider(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2)
            )
        }
    }
    .graphicsLayer(rotationZ = 270f)

/**
 * Tab 6: Remote Collaboration & Cloud Synchronization Setup Panel
 */
@Composable
fun CloudSyncView(viewModel: SequencerViewModel) {
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val currentProjId by viewModel.currentProjectId.collectAsStateWithLifecycle()
    val bluetoothSyncStatus by viewModel.bluetoothSyncStatus.collectAsStateWithLifecycle()
    val usbSyncStatus by viewModel.usbSyncStatus.collectAsStateWithLifecycle()
    val cloudSyncing by viewModel.cloudSyncing.collectAsStateWithLifecycle()
    val activeCollaborators by viewModel.activeCollaborators.collectAsStateWithLifecycle()

    var collaboratorNameInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "COLLABORATION & SYNC MANAGER",
                color = CyberBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Hardware controller Sync Card (Bluetooth/USB)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                border = BorderStroke(1.dp, CyberBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("HARDWARE MIDI & CLOCK SYNC", color = CyberBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Bluetooth MIDI Controller", color = Color.White, fontSize = 11.sp)
                            Text(bluetoothSyncStatus, color = Color.Gray, fontSize = 9.sp)
                        }
                        Button(
                            onClick = { viewModel.toggleBluetoothSync() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (bluetoothSyncStatus.startsWith("Synced")) CyberGreen else DarkGrey),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(if (bluetoothSyncStatus.startsWith("Synced")) "Disconnect" else "Scan", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("USB Host Link / MIDI In", color = Color.White, fontSize = 11.sp)
                            Text(usbSyncStatus, color = Color.Gray, fontSize = 9.sp)
                        }
                        Button(
                            onClick = { viewModel.toggleUsbSync() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (usbSyncStatus.startsWith("Synced")) CyberGreen else DarkGrey),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(if (usbSyncStatus.startsWith("Synced")) "Disconnect" else "Connect", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Cloud Projects list
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                border = BorderStroke(1.dp, Color(0x1AFFFFFF)), // frosted glass border
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SAVED PROJECTS", color = CyberPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { viewModel.triggerCloudBackup() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                            shape = RoundedCornerShape(4.dp),
                            enabled = !cloudSyncing
                        ) {
                            Text(if (cloudSyncing) "Syncing..." else "Cloud Backup", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (savedProjects.isEmpty()) {
                        Text(
                            "No saved projects found. Record or sequence patterns to begin!",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            modifier = Modifier
                                .heightIn(max = 180.dp)
                                .fillMaxWidth()
                        ) {
                            items(savedProjects) { project ->
                                val isSelected = currentProjId == project.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(if (isSelected) Color.DarkGray else DarkGrey, RoundedCornerShape(6.dp))
                                        .clickable { viewModel.loadProject(project) }
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(project.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("${project.bpm} BPM | ${project.barCount} Bars", color = Color.Gray, fontSize = 9.sp)
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteProject(project) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CyberPink, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cross-device Remote Jam Session
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("CROSS-DEVICE COLLABORATION ROOM", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enter dynamic jam code to sync timelines remotely.", color = Color.Gray, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = collaboratorNameInput,
                            onValueChange = { collaboratorNameInput = it },
                            placeholder = { Text("Producer Name", fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyberGreen,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (collaboratorNameInput.isNotBlank()) {
                                    viewModel.addCollaborator(collaboratorNameInput)
                                    collaboratorNameInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Invite", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Active Remote Producers:", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (activeCollaborators.isEmpty()) {
                        Text("No remote sessions active. Standalone production mode.", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            activeCollaborators.forEach { name ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(CyberGreen, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(name, color = Color.White, fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { viewModel.removeCollaborator(name) }) {
                                        Text("Disconnect", color = CyberPink, fontSize = 9.sp)
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
