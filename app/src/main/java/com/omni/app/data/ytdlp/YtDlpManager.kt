package com.omni.app.data.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo as YtVideoInfo
import com.omni.app.data.download.AvailableFormat
import com.omni.app.data.download.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object YtDlpManager {

    private const val TAG = "YtDlpManager"
    var isReady = false
        private set

    // Opções leves para a busca não ser bloqueada
    private fun YoutubeDLRequest.addSearchOptions() {
        addOption("--no-check-certificate")
        addOption("--no-warnings")
        addOption("--socket-timeout", "20")
        addOption("--no-cache-dir")
    }

    // Opções robustas para extrair informações do vídeo e qualidades
    private fun YoutubeDLRequest.addVideoBypassOptions() {
        addOption("--no-check-certificate")
        addOption("--no-warnings")
        addOption("--socket-timeout", "30")
        addOption("--retries", "10")
        addOption("--no-cache-dir")
        addOption("--force-ipv4")

        // Estratégia atualizada: 'tv' e 'ios' são os que melhor liberam DASH (1080p+) atualmente.
        // Pulamos 'web', 'mweb' e 'android' para evitar o limite de 360p.
        addOption("--extractor-args", "youtube:player_client=tv,ios,android_vr;player_skip=web,mweb,android")
        
        // User-Agent de Smart TV/Chromecast para combinar com o client 'tv'
        addOption("--user-agent", "Mozilla/5.0 (Chromecast; GoogleTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36")
    }

    suspend fun initialize(context: Context, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== Iniciando Inicialização do yt-dlp ===")

            Log.i(TAG, "Inicializando FFmpeg...")
            FFmpeg.getInstance().init(context)
            Log.i(TAG, "✓ FFmpeg inicializado com sucesso")

            Log.i(TAG, "Inicializando YoutubeDL...")
            YoutubeDL.getInstance().init(context)
            Log.i(TAG, "✓ YoutubeDL inicializado com sucesso")

            // Tenta atualizar o yt-dlp para a versão mais recente usando o método correto da biblioteca
            Log.i(TAG, "Tentando atualizar yt-dlp...")
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                Log.i(TAG, "✓ Atualização do yt-dlp: $status")
            } catch (updateError: Exception) {
                Log.w(TAG, "⚠ Não foi possível atualizar yt-dlp - continuando com versão atual", updateError)
            }

            onProgress(100f)
            isReady = true
            Log.i(TAG, "=== Inicialização Concluída com Sucesso ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRO CRÍTICO na inicialização do yt-dlp", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            false
        }
    }

    data class VideoInfo(
        val title: String?,
        val thumbnailUrl: String?,
        val duration: Long?,
        val viewCount: Long?,
        val uploader: String?,
        val availableFormats: List<AvailableFormat> = emptyList()
    )

    data class SearchResult(
        val id: String,
        val url: String,
        val title: String,
        val thumbnailUrl: String?,
        val duration: Int,
        val uploader: String?,
        val viewCountText: String? = null
    )

    suspend fun searchVideos(query: String, count: Int = 20): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Iniciando busca: query='$query', count=$count")
            val request = YoutubeDLRequest("ytsearch$count:$query")
            request.addOption("--dump-single-json")
            request.addOption("--flat-playlist")
            request.addSearchOptions()

            Log.d(TAG, "Executando comando yt-dlp para busca...")
            val response = YoutubeDL.getInstance().execute(request)
            val json = response.out

            if (json.isBlank()) {
                Log.w(TAG, "⚠ Resposta vazia da busca")
                return@withContext emptyList()
            }

            Log.d(TAG, "✓ Resposta recebida (${json.length} caracteres)")

            val root = JSONObject(json)
            val entriesArray = root.optJSONArray("entries")

            if (entriesArray == null) {
                Log.w(TAG, "⚠ Nenhuma entrada encontrada no JSON")
                return@withContext emptyList()
            }

            val results = mutableListOf<SearchResult>()
            Log.d(TAG, "Processando ${entriesArray.length()} resultados...")

            for (i in 0 until entriesArray.length()) {
                val entry = entriesArray.optJSONObject(i) ?: continue
                val id = entry.optString("id")
                if (id.isEmpty()) continue

                val url = "https://www.youtube.com/watch?v=$id"

                var thumbUrl = if (entry.has("thumbnail")) entry.optString("thumbnail") else null
                if (thumbUrl.isNullOrEmpty() && entry.has("thumbnails")) {
                    val thumbs = entry.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                    }
                }
                if (thumbUrl.isNullOrEmpty()) {
                    thumbUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                }

                results.add(
                    SearchResult(
                        id = id,
                        url = url,
                        title = entry.optString("title", "Unknown"),
                        thumbnailUrl = thumbUrl,
                        duration = entry.optInt("duration", 0),
                        uploader = entry.optString("uploader", "Unknown"),
                        viewCountText = entry.optString("view_count", "")
                    )
                )
            }

            Log.i(TAG, "✓ Busca concluída com ${results.size} resultados")
            results
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRO na busca de vídeos", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            emptyList()
        }
    }

    suspend fun fetchVideoInfoWithFormats(url: String, context: Context): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📺 Buscando informações do vídeo: $url")

            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            if (ffmpegPath != null) {
                Log.d(TAG, "Caminho FFmpeg: $ffmpegPath")
            }

            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--all-formats")
            request.addVideoBypassOptions()

            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)

            Log.d(TAG, "Executando yt-dlp getInfo...")
            val info: YtVideoInfo = YoutubeDL.getInstance().getInfo(request)

            val formats = info.formats?.filter { 
                (it.height ?: 0) >= 144 && // Remove resoluções irrelevantes (storyboards/thumbnails)
                it.ext != "mhtml" &&
                it.acodec != "none" || it.vcodec != "none"
            }
                ?.map { f ->
                    val h = f.height ?: 0
                    val fpsValue = f.fps?.toDouble() ?: 0.0
                    val label = "${h}p" + (if (fpsValue >= 49.0) "60" else "")

                    Log.d(TAG, "📊 Formato yt-dlp: height=$h, fps=$fpsValue, ext=${f.ext}, formatId=${f.formatId}, label=$label")

                    AvailableFormat(
                        formatId = f.formatId ?: "",
                        height = h,
                        fps = fpsValue.toInt(),
                        ext = f.ext ?: "",
                        filesize = f.fileSize,
                        label = label
                    )
                }
                ?.sortedWith(compareByDescending<AvailableFormat> { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.filesize ?: 0L })
                ?.distinctBy { it.label } // Mantém apenas um de cada (ex: um 1080p, um 1080p60)
                ?.sortedByDescending { it.height } ?: emptyList()

            Log.i(TAG, "✓ Formatos encontrados: ${formats.size} qualidades")
            formats.forEach { fmt ->
                Log.d(TAG, "  - ${fmt.label} (height=${fmt.height}, fps=${fmt.fps}, ext=${fmt.ext})")
            }

            VideoInfo(
                title = info.title,
                thumbnailUrl = info.thumbnail,
                duration = info.duration.toLong(),
                viewCount = info.viewCount?.toLongOrNull() ?: 0L,
                uploader = info.uploader,
                availableFormats = formats
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRO ao buscar informações: ${e.message}")

            // Diagnóstico específico para LOGIN_REQUIRED
            if (e.message?.contains("LOGIN_REQUIRED") == true || e.message?.contains("Please sign in") == true) {
                Log.e(TAG, "🔐 ERRO DE AUTENTICAÇÃO: YouTube exigiu login")
                Log.e(TAG, "💡 Solução: O vídeo pode estar restrito por idade ou exigir autenticação")
                Log.e(TAG, "   Tente:")
                Log.e(TAG, "   1. Executar em um navegador normalmente para verificar acesso")
                Log.e(TAG, "   2. Verificar se o vídeo é público/acessível")
                Log.e(TAG, "   3. Esperar alguns minutos e tentar novamente")
            } else if (e.message?.contains("could not find chrome cookies database") == true) {
                Log.e(TAG, "🔐 AVISO: Chrome não está disponível para cookies")
                Log.e(TAG, "   Continuando sem cookies - funcionará para vídeos públicos")
            } else if (e.message?.contains("HTTP Error 403") == true) {
                Log.e(TAG, "🚫 ERRO 403: YouTube está bloqueando requisições")
                Log.e(TAG, "💡 Possíveis causas:")
                Log.e(TAG, "   - IP bloqueado ou rate limited")
                Log.e(TAG, "   - Requer autenticação")
                Log.e(TAG, "   - Conteúdo geográfico restrito")
            } else if (e.message?.contains("HTTP Error 429") == true) {
                Log.e(TAG, "⏳ ERRO 429: Rate limited (muitas requisições)")
                Log.e(TAG, "💡 Aguarde alguns minutos antes de tentar novamente")
            }

            Log.e(TAG, "Mensagem: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            null
        }
    }

    data class PlaylistInfo(
        val title: String,
        val author: String?,
        val videoCount: Int,
        val entries: List<PlaylistItem>
    )

    data class PlaylistItem(
        val url: String,
        val title: String,
        val duration: Int,
        val uploader: String?,
        val thumbnailUrl: String?
    )

    suspend fun fetchPlaylistInfo(url: String, context: Context): PlaylistInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📋 Buscando informações da playlist: $url")

            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            request.addSearchOptions()
            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)

            Log.d(TAG, "Executando yt-dlp para playlist...")
            val response = YoutubeDL.getInstance().execute(request)
            val json = response.out

            if (json.isBlank()) {
                Log.w(TAG, "⚠ Resposta vazia para playlist")
                return@withContext null
            }

            val root = JSONObject(json)
            val title = root.optString("title", "Unknown Playlist")
            val uploader = if (root.has("uploader")) root.getString("uploader") else null
            val entriesArray = root.optJSONArray("entries")

            Log.d(TAG, "✓ Playlist recebida: $title por $uploader")

            val playlistItems = mutableListOf<PlaylistItem>()
            if (entriesArray != null) {
                Log.d(TAG, "Processando ${entriesArray.length()} itens da playlist...")

                for (i in 0 until entriesArray.length()) {
                    val entry = entriesArray.optJSONObject(i) ?: continue
                    val id = entry.optString("id")
                    val entryUrl = entry.optString("url")

                    val fullUrl = when {
                        entryUrl.startsWith("http") -> entryUrl
                        id.isNotEmpty() -> "https://www.youtube.com/watch?v=$id"
                        else -> continue
                    }

                    var thumbUrl = if (entry.has("thumbnail")) entry.optString("thumbnail") else null
                    if (thumbUrl.isNullOrEmpty() && entry.has("thumbnails")) {
                        val thumbs = entry.optJSONArray("thumbnails")
                        if (thumbs != null && thumbs.length() > 0) {
                            thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                        }
                    }

                    if (thumbUrl.isNullOrEmpty() && id.isNotEmpty()) {
                        thumbUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                    }

                    playlistItems.add(
                        PlaylistItem(
                            url = fullUrl,
                            title = entry.optString("title", "Unknown Video"),
                            duration = entry.optInt("duration", 0),
                            uploader = if (entry.has("uploader")) entry.getString("uploader") else uploader,
                            thumbnailUrl = thumbUrl
                        )
                    )
                }
            }

            Log.i(TAG, "✓ Playlist carregada com ${playlistItems.size} itens")

            PlaylistInfo(
                title = title,
                author = uploader,
                videoCount = playlistItems.size,
                entries = playlistItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRO ao buscar informações da playlist: $url", e)
            Log.e(TAG, "Tipo de erro: ${e.javaClass.simpleName}")
            Log.e(TAG, "Mensagem: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            null
        }
    }

    suspend fun downloadVideo(
        item: DownloadItem,
        outputDir: File,
        maxHeight: Int?,
        context: Context,
        onTitle: (String) -> Unit = {},
        onProgress: (Float, String, String) -> Unit = { _, _, _ -> },
        onSuccess: (File) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "⬇️ Iniciando download de vídeo")
            Log.d(TAG, "URL: ${item.url}")
            Log.d(TAG, "Formato selecionado: ${item.selectedFormatId}")
            Log.d(TAG, "Altura máx: $maxHeight")
            Log.d(TAG, "60fps: ${item.prefer60fps}")
            Log.d(TAG, "Formato saída: ${item.format}")

            outputDir.mkdirs()
            val request = YoutubeDLRequest(item.url)

            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            val fmt = when {
                item.selectedFormatId != null ->
                    "${item.selectedFormatId}+bestaudio/bestvideo+bestaudio/best"
                maxHeight != null -> {
                    if (item.prefer60fps)
                        "bestvideo[height<=$maxHeight][fps<=60]+bestaudio/bestvideo[height<=$maxHeight]+bestaudio/best[height<=$maxHeight]/best"
                    else
                        "bestvideo[height<=$maxHeight]+bestaudio/best[height<=$maxHeight]/best"
                }
                else ->
                    if (item.prefer60fps) "bestvideo[fps<=60]+bestaudio/bestvideo+bestaudio/best"
                    else "bestvideo+bestaudio/best"
            }

            Log.d(TAG, "Formato yt-dlp: $fmt")

            request.addOption("-f", fmt)
            request.addOption("--merge-output-format", item.format.lowercase())
            request.addOption("--no-playlist")
            request.addVideoBypassOptions()
            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)
            
            // Advanced options
            applyAdvancedOptions(request, item, isAudio = false)
            
            request.addOption("-o", "${outputDir.absolutePath}/${item.outputTemplate}")

            var downloadedFile: File? = null

            Log.d(TAG, "Executando download...")
            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, parseSpeed(line), formatEta(etaInSeconds))
                Log.v(TAG, "[${progress.toInt()}%] $line")

                if (line.contains("[download] Destination:")) {
                    val path = line.substringAfter("Destination: ").trim()
                    downloadedFile = File(path)
                    onTitle(downloadedFile?.nameWithoutExtension ?: "")
                    Log.d(TAG, "Arquivo de saída: $path")
                } else if (line.contains("[ffmpeg] Merging formats into \"")) {
                    downloadedFile = File(line.substringAfter("Merging formats into \"").substringBefore("\""))
                    Log.d(TAG, "Mesclando formatos...")
                } else if (line.contains("has already been downloaded")) {
                    val path = line.substringAfter("[download] ").substringBefore(" has already been downloaded").trim()
                    downloadedFile = File(path)
                    Log.d(TAG, "Arquivo já estava baixado: $path")
                } else if (line.contains("ERROR") || line.contains("error")) {
                    Log.e(TAG, "❌ Erro no download: $line")
                }
            }

            val finalFile = downloadedFile ?: if (!response.out.contains("\n") && response.out.contains("/")) File(response.out) else null
            if (finalFile != null) {
                Log.i(TAG, "✓ Download concluído com sucesso: ${finalFile.name}")
                Log.d(TAG, "Tamanho do arquivo: ${finalFile.length() / (1024 * 1024)} MB")
                onSuccess(finalFile)
                Result.success(finalFile)
            } else {
                Log.e(TAG, "❌ Não foi possível determinar o caminho do arquivo baixado")
                Result.failure(Exception("Could not determine downloaded file path"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRO CRÍTICO no download: ${item.url}", e)
            Log.e(TAG, "Tipo: ${e.javaClass.simpleName}")
            Log.e(TAG, "Mensagem: ${e.message}")
            Log.e(TAG, "Stack: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    suspend fun downloadAudio(
        context: Context,
        item: DownloadItem,
        outputDir: File,
        onTitle: (String) -> Unit = {},
        onProgress: (Float, String, String) -> Unit = { _, _, _ -> },
        onSuccess: (File) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🎵 Iniciando download de áudio")
            Log.d(TAG, "URL: ${item.url}")
            Log.d(TAG, "Formato áudio: ${item.format}")
            Log.d(TAG, "Embed thumbnail: ${item.embedThumbnail}")

            outputDir.mkdirs()
            val request = YoutubeDLRequest(item.url)
            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            request.addOption("-f", "bestaudio/best")
            request.addOption("--extract-audio")
            request.addOption("--audio-format", item.format.lowercase().ifBlank { "mp3" })
            
            val bitrate = item.quality
                .replace("kbps", "").replace("K", "").trim()
                .takeIf { it.all { c -> c.isDigit() } } ?: "0"
            if (bitrate != "0") request.addOption("--audio-quality", bitrate)

            request.addOption("--no-playlist")
            request.addVideoBypassOptions()
            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)
            if (item.embedThumbnail) {
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
            }

            // Advanced options
            applyAdvancedOptions(request, item, isAudio = true)

            request.addOption("-o", "${outputDir.absolutePath}/${item.outputTemplate}")

            var downloadedFile: File? = null

            Log.d(TAG, "Executando download de áudio...")
            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, parseSpeed(line), formatEta(etaInSeconds))
                Log.v(TAG, "[${progress.toInt()}%] $line")

                if (line.contains("[download] Destination:")) {
                    downloadedFile = File(line.substringAfter("Destination: ").trim())
                    Log.d(TAG, "Arquivo de saída: ${downloadedFile?.name}")
                } else if (line.contains("[ffmpeg] Post-process file")) {
                    downloadedFile = File(line.substringAfter("[ffmpeg] Post-process file ").trim())
                    Log.d(TAG, "Pós-processamento: ${downloadedFile?.name}")
                } else if (line.contains("ERROR") || line.contains("error")) {
                    Log.e(TAG, "❌ Erro no download: $line")
                }
            }

            val finalFile = downloadedFile ?: if (!response.out.contains("\n") && response.out.contains("/")) File(response.out) else null
            if (finalFile != null) {
                Log.i(TAG, "✓ Download de áudio concluído com sucesso: ${finalFile.name}")
                Log.d(TAG, "Tamanho do arquivo: ${finalFile.length() / (1024 * 1024)} MB")
                onSuccess(finalFile)
                Result.success(finalFile)
            } else {
                Log.e(TAG, "❌ Não foi possível determinar o caminho do arquivo de áudio baixado")
                Result.failure(Exception("Could not determine downloaded audio file path"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRO CRÍTICO no download de áudio: ${item.url}", e)
            Log.e(TAG, "Tipo: ${e.javaClass.simpleName}")
            Log.e(TAG, "Mensagem: ${e.message}")
            Log.e(TAG, "Stack: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    private fun applyAdvancedOptions(request: YoutubeDLRequest, item: DownloadItem, isAudio: Boolean) {
        Log.d(TAG, "🛠️ Aplicando opções avançadas para ${item.title}")

        // Speed limit
        if (item.speedLimit.isNotBlank()) {
            Log.d(TAG, "  - Limite de velocidade: ${item.speedLimit}")
            request.addOption("--rate-limit", item.speedLimit)
        }

        // Proxy
        if (item.proxy.isNotBlank()) {
            Log.d(TAG, "  - Proxy: ${item.proxy}")
            request.addOption("--proxy", item.proxy)
        }

        // Fragments
        Log.d(TAG, "  - Fragmentos simultâneos: ${item.maxFragments}")
        request.addOption("--concurrent-fragments", item.maxFragments)

        // Cookies
        if (item.cookieSource != com.omni.app.data.download.CookieSource.NONE) {
            val browser = when (item.cookieSource) {
                com.omni.app.data.download.CookieSource.CHROME -> "chrome"
                com.omni.app.data.download.CookieSource.FIREFOX -> "firefox"
                com.omni.app.data.download.CookieSource.EDGE -> "edge"
                com.omni.app.data.download.CookieSource.BRAVE -> "brave"
                else -> null
            }
            if (browser != null) {
                Log.d(TAG, "  - Cookies do navegador: $browser")
                request.addOption("--cookies-from-browser", browser)
            }
        }

        // Subtitles
        if (item.embedSubtitles && !isAudio) {
            Log.d(TAG, "  - Legendas ativadas: idioma=${item.subtitleLanguage}, auto=${item.autoGeneratedSubtitles}")
            request.addOption("--write-subs")
            if (item.autoGeneratedSubtitles) {
                request.addOption("--write-auto-subs")
            }
            request.addOption("--sub-langs", item.subtitleLanguage)
            request.addOption("--convert-subs", "srt")
            
            // Se o usuário quer embutir no arquivo (para players que suportam tracks internas)
            request.addOption("--embed-subs")
        }

        // Chapters
        if (item.embedChapters) {
            Log.d(TAG, "  - Embutir capítulos: Sim")
            request.addOption("--embed-chapters")
        }
        if (item.splitByChapters) {
            Log.d(TAG, "  - Dividir por capítulos: Sim")
            request.addOption("--split-chapters")
        }

        // Metadata
        if (item.writeMetadata) {
            Log.d(TAG, "  - Escrever metadados: Sim")
            request.addOption("--add-metadata")
        }

        // SponsorBlock
        if (item.sponsorBlockEnabled && !isAudio) {
            val categories = item.sponsorBlockCategories.joinToString(",")
            Log.d(TAG, "  - SponsorBlock: $categories (Ação: ${item.sponsorBlockAction})")
            if (item.sponsorBlockAction == com.omni.app.data.download.SponsorBlockAction.REMOVE) {
                request.addOption("--sponsorblock-remove", categories)
            } else {
                request.addOption("--sponsorblock-mark", categories)
            }
        }

        // Time Range
        if (item.startTime.isNotBlank() || item.endTime.isNotBlank()) {
            val start = item.startTime.ifBlank { "00:00:00" }
            val end = item.endTime.ifBlank { "99:59:59" }
            Log.d(TAG, "  - Intervalo de tempo: $start até $end")
            request.addOption("--download-sections", "*$start-$end")
        }

        // Audio Post-processing
        val filters = mutableListOf<String>()
        if (item.normalizeAudio) {
            Log.d(TAG, "  - Normalizar áudio: Sim")
            filters.add("loudnorm")
        }
        if (item.trimSilence) {
            Log.d(TAG, "  - Remover silêncio: Sim")
            filters.add("silenceremove=1:0:-50dB")
        }
        if (filters.isNotEmpty()) {
            request.addOption("--postprocessor-args", "ffmpeg:-af ${filters.joinToString(",")}")
        }

        // Custom Args
        if (item.customArgs.isNotBlank()) {
            Log.d(TAG, "  - Argumentos customizados: ${item.customArgs}")
            item.customArgs.split(" ").filter { it.isNotBlank() }.forEach {
                request.addOption(it)
            }
        }
    }

    private fun parseSpeed(line: String): String = if (line.contains("at")) line.substringAfter("at").trim().split(" ").firstOrNull() ?: "0KiB/s" else "0KiB/s"
    private fun formatEta(seconds: Long): String = if (seconds <= 0) "00:00" else String.format("%02d:%02d", seconds / 60, seconds % 60)
}
