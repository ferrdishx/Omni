package com.omni.app.data.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.omni.app.OmniApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as OmniApp).downloadRepository

    val downloads: StateFlow<List<DownloadItem>> = repo.downloads
    
    val activeDownloads: StateFlow<List<DownloadItem>> = repo.downloads
        .map { list -> 
            list.filter { it.status !in listOf(DownloadStatus.COMPLETED, DownloadStatus.CANCELLED) } 
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedMedia: StateFlow<List<DownloadedMedia>> = 
        repo.completedMedia.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _scannedFiles = MutableStateFlow<List<DownloadedMedia>>(emptyList())
    val scannedFiles: StateFlow<List<DownloadedMedia>> = _scannedFiles.asStateFlow()

    val ytdlpSetupProgress: StateFlow<Float?> = repo.ytdlpSetupProgress

    init { 
        repo.ensureYtDlp()
        
        viewModelScope.launch {
            repo.completedMedia.collect {
                refreshScannedFiles()
            }
        }
    }

    fun enqueue(item: DownloadItem) = repo.enqueue(item)
    fun cancel(id: String)          = repo.cancel(id)
    fun retry(id: String)           = repo.retry(id)
    fun remove(id: String)          = repo.remove(id)

    fun deleteMedia(item: DownloadedMedia) {
        viewModelScope.launch { 
            repo.deleteMedia(item)
            refreshScannedFiles()
        }
    }

    fun refresh() = refreshScannedFiles()

    fun refreshScannedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val video = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                    video && audio
                } else {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                }

                if (!hasPermission) {
                    _scannedFiles.value = emptyList()
                    return@launch
                }

                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val omniDir = File(baseDir, "Omni")
                
                // Folders to scan
                val videosDir = File(omniDir, "Videos")
                val musicsDir = File(omniDir, "Musics")
                
                val foldersToScan = listOf(videosDir, musicsDir)
                foldersToScan.forEach { if (!it.exists()) it.mkdirs() }

                val physicalFiles = foldersToScan.flatMap { folder ->
                    folder.listFiles()?.filter {
                        it.isFile && it.extension.lowercase() in listOf("mp4", "mp3", "m4a", "wav", "flac", "ogg", "mkv", "webm")
                    }?.toList() ?: emptyList()
                }

                val dbList = repo.completedMedia.first()

                val resultList = physicalFiles.map { file ->
                    val dbMatch = dbList.find { dbItem ->
                        val dbFileName = File(dbItem.filePath).name
                        dbFileName == file.name
                    }

                    if (dbMatch != null) {
                        dbMatch.copy(filePath = file.absolutePath)
                    } else {
                        val nameNoExt = file.nameWithoutExtension
                        val omniDir = file.parentFile?.parentFile
                        val thumbVideoDir = File(omniDir, ".thumbnails")
                        val thumbAudioDir = File(omniDir, ".thumbaudio")
                        
                        val localThumb = File(file.parent, "$nameNoExt.jpg").takeIf { it.exists() }
                            ?: File(file.parent, "$nameNoExt.png").takeIf { it.exists() }
                            ?: File(thumbVideoDir, "$nameNoExt.jpg").takeIf { it.exists() }
                            ?: File(thumbAudioDir, "$nameNoExt.jpg").takeIf { it.exists() }
                        
                        DownloadedMedia(
                            id = file.absolutePath,
                            title = file.name,
                            filePath = file.absolutePath,
                            thumbnailUrl = localThumb?.absolutePath,
                            isAudio = file.extension.lowercase() in listOf("mp3", "m4a", "wav", "flac", "ogg"),
                            format = file.extension.uppercase(),
                            timestamp = file.lastModified()
                        )
                    }
                }.sortedByDescending { it.timestamp }

                _scannedFiles.value = resultList
            } catch (e: Exception) {
                Log.e("Omni", "Error in refreshScannedFiles", e)
            }
        }
    }
}
