package com.movtery.zalithlauncher.ui.screens.main

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private data class EnderAudioTrack(val uri: Uri, val title: String, val artist: String)

@Composable
internal fun EnderMusicPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf(emptyList<EnderAudioTrack>()) }
    var current by remember { mutableStateOf<EnderAudioTrack?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun scan() {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val found = mutableListOf<EnderAudioTrack>()
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                found += EnderAudioTrack(
                    ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    cursor.getString(titleCol) ?: "Faixa sem título",
                    cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "Artista desconhecido"
                )
            }
        }
        tracks = found
        message = if (found.isEmpty()) "Nenhuma música encontrada no armazenamento compartilhado." else null
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else message = "Permita acesso a músicas e áudio para localizar seus arquivos."
    }

    fun openLibrary() {
        showLibrary = true
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) scan()
        else requestPermission.launch(permission)
    }

    fun playTrack(track: EnderAudioTrack) {
        try {
            player?.release()
            player = MediaPlayer().also { mp ->
                mp.setOnPreparedListener { it.start(); playing = true; current = track }
                mp.setOnCompletionListener { playing = false }
                mp.setOnErrorListener { _, _, _ -> playing = false; message = "Falha ao reproduzir este arquivo."; true }
                mp.setDataSource(context, track.uri)
                mp.prepareAsync()
            }
            current = track
            playing = false
            message = null
            showLibrary = false
        } catch (_: Exception) {
            message = "Não foi possível abrir esta música."
        }
    }

    DisposableEffect(Unit) {
        onDispose { player?.release(); player = null }
    }

    Surface(modifier.padding(horizontal = 12.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("ENDER MUSIC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(current?.title ?: "Sua música no Minecraft", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                current?.let { Text(it.artist, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
            TextButton(onClick = { openLibrary() }) { Text("Músicas") }
            if (current != null) TextButton(onClick = {
                player?.let { mp -> if (playing) { mp.pause(); playing = false } else { mp.start(); playing = true } }
            }) { Text(if (playing) "Pausar" else "Tocar") }
        }
    }

    if (showLibrary) AlertDialog(
        onDismissRequest = { showLibrary = false },
        title = { Text("Biblioteca de músicas") },
        text = {
            Column {
                Text("Músicas encontradas neste dispositivo.")
                if (tracks.isEmpty()) Text(message ?: "Nenhuma faixa carregada.")
                LazyColumn {
                    items(tracks, key = { it.uri.toString() }) { track ->
                        Column(Modifier.fillMaxWidth().clickable { playTrack(track) }.padding(vertical = 8.dp)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium)
                            Text(track.artist, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) scan()
            else requestPermission.launch(permission)
        }) { Text("Atualizar") } },
        dismissButton = { TextButton(onClick = { showLibrary = false }) { Text("Fechar") } }
    )
}
