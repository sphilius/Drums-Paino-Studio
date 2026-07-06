package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.SoundPack
import com.example.database.PresetEntity
import java.io.File

/**
 * Tab 7: Community Library — sound packs, synth presets, and rendered stems, all
 * shared through the OS share sheet (email/chat/cloud drive) rather than a hosted
 * backend, so producers can trade sounds without this app needing a server.
 */
@Composable
fun CommunityLibraryView(viewModel: SequencerViewModel) {
    val context = LocalContext.current
    var section by remember { mutableStateOf(0) }
    val sections = listOf("Sound Packs", "Synth Presets", "Project Stems")

    var exportedFiles by remember { mutableStateOf(listOf<File>()) }
    LaunchedEffect(section) {
        if (section == 2) exportedFiles = viewModel.listExportedFiles(context)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "COMMUNITY LIBRARY",
            color = CyberBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            "Discover, save, and share sound packs, synth presets, and project stems.",
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEachIndexed { index, label ->
                Button(
                    onClick = { section = index },
                    colors = ButtonDefaults.buttonColors(containerColor = if (section == index) CyberBlue else DarkGrey),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (section == index) Color.Black else Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (section) {
            0 -> SoundPackLibrarySection(viewModel)
            1 -> SynthPresetLibrarySection(viewModel)
            2 -> ProjectStemsLibrarySection(exportedFiles) { exportedFiles = viewModel.listExportedFiles(context) }
        }
    }
}

@Composable
private fun SoundPackLibrarySection(viewModel: SequencerViewModel) {
    val context = LocalContext.current
    val activeName by viewModel.activeSoundPackName.collectAsStateWithLifecycle()
    val userPacks by viewModel.userSoundPackPresets.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("BUILT-IN PACKS", color = CyberPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = { showImportDialog = true }) { Text("Import", color = CyberGreen, fontSize = 10.sp) }
                    TextButton(onClick = { showSaveDialog = true }) { Text("Save Current", color = CyberBlue, fontSize = 10.sp) }
                }
            }
        }
        items(viewModel.builtInSoundPacks) { pack ->
            SoundPackCard(pack, isActive = activeName == pack.name, onApply = { viewModel.applySoundPack(pack) })
        }
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("MY PACKS", color = CyberGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (userPacks.isEmpty()) {
            item {
                Text(
                    "No custom packs saved yet. Tune a pad in Sound Design (tap the tune icon on any pad) and save it here!",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        } else {
            items(userPacks) { entity ->
                UserPresetCard(
                    entity = entity,
                    activeName = activeName,
                    onApply = { viewModel.applyUserSoundPackPreset(entity) },
                    onShare = { shareTextPreset(context, "BeatCraft sound pack: ${entity.name}", entity.payloadJson) },
                    onDelete = { viewModel.deletePreset(entity) }
                )
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            title = "Save Sound Pack",
            onDismiss = { showSaveDialog = false },
            onConfirm = { name, author -> viewModel.saveCurrentAsSoundPack(name, author); showSaveDialog = false }
        )
    }
    if (showImportDialog) {
        ImportJsonDialog(
            title = "Import Sound Pack",
            onDismiss = { showImportDialog = false },
            onConfirm = { json -> viewModel.importSoundPackFromJson(json); showImportDialog = false }
        )
    }
}

@Composable
private fun SynthPresetLibrarySection(viewModel: SequencerViewModel) {
    val context = LocalContext.current
    val userPresets by viewModel.userSynthPresets.collectAsStateWithLifecycle()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("MY SYNTH PRESETS", color = CyberPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = { showImportDialog = true }) { Text("Import", color = CyberGreen, fontSize = 10.sp) }
                    TextButton(onClick = { showSaveDialog = true }) { Text("Save Current", color = CyberBlue, fontSize = 10.sp) }
                }
            }
        }
        if (userPresets.isEmpty()) {
            item {
                Text(
                    "No synth presets saved yet. Dial in a waveform + ADSR on the Piano tab and save it here!",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        } else {
            items(userPresets) { entity ->
                UserPresetCard(
                    entity = entity,
                    activeName = null,
                    onApply = { viewModel.applySynthPreset(entity) },
                    onShare = { shareTextPreset(context, "BeatCraft synth preset: ${entity.name}", entity.payloadJson) },
                    onDelete = { viewModel.deletePreset(entity) }
                )
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            title = "Save Synth Preset",
            onDismiss = { showSaveDialog = false },
            onConfirm = { name, author -> viewModel.saveCurrentAsSynthPreset(name, author); showSaveDialog = false }
        )
    }
    if (showImportDialog) {
        ImportJsonDialog(
            title = "Import Synth Preset",
            onDismiss = { showImportDialog = false },
            onConfirm = { json -> viewModel.importSynthPresetFromJson(json); showImportDialog = false }
        )
    }
}

@Composable
private fun ProjectStemsLibrarySection(files: List<File>, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("EXPORTED STEMS & MIXES", color = CyberOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh) { Text("Refresh", color = CyberBlue, fontSize = 10.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (files.isEmpty()) {
            Text(
                "No exports yet. Use EXPORT in the transport bar (Full Mix, Stems, or MIDI File).",
                color = Color.Gray,
                fontSize = 10.sp
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlass(RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = true)) {
                            Text(file.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
                            Text("${file.length() / 1024} KB", color = Color.Gray, fontSize = 9.sp)
                        }
                        IconButton(onClick = { shareExportedFile(context, file) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share ${file.name}", tint = CyberBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundPackCard(pack: SoundPack, isActive: Boolean, onApply: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(RoundedCornerShape(10.dp))
            .border(1.dp, if (isActive) CyberGreen else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
            .clickable { onApply() }
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pack.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (isActive) Text("ACTIVE", color = CyberGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text("by ${pack.author}", color = Color.Gray, fontSize = 9.sp)
        Text(pack.description, color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun UserPresetCard(
    entity: PresetEntity,
    activeName: String?,
    onApply: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = activeName != null && activeName == entity.name
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass(RoundedCornerShape(10.dp))
            .border(1.dp, if (isActive) CyberGreen else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
            .clickable { onApply() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(entity.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("by ${entity.author}", color = Color.Gray, fontSize = 9.sp)
        }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share ${entity.name}", tint = CyberBlue) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${entity.name}", tint = CyberPink) }
    }
}

@Composable
private fun SavePresetDialog(title: String, onDismiss: () -> Unit, onConfirm: (name: String, author: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Your name (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, author) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
            ) { Text("Save", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.LightGray) } },
        containerColor = StudioCardBg
    )
}

@Composable
private fun ImportJsonDialog(title: String, onDismiss: () -> Unit, onConfirm: (json: String) -> Unit) {
    var json by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Paste JSON shared by another producer.", color = Color.Gray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = json,
                    onValueChange = { json = it },
                    label = { Text("Preset JSON") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (json.isNotBlank()) onConfirm(json) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen)
            ) { Text("Import", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.LightGray) } },
        containerColor = StudioCardBg
    )
}

private fun shareExportedFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = if (file.extension == "mid") "audio/midi" else "audio/wav"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
}

private fun shareTextPreset(context: Context, subject: String, json: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, json)
    }
    context.startActivity(Intent.createChooser(intent, "Share preset"))
}
