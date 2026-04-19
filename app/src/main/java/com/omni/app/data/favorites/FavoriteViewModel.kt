package com.omni.app.data.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omni.app.data.download.AppDatabase
import com.omni.app.data.download.DownloadedMedia
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val items: List<FavoriteItem> = emptyList(),
    val query: String = "",
    val sortBy: String = "date",       // "date" | "title" | "author" | "size"
    val ascending: Boolean = false,
    val filterAudio: Boolean = false,
    val filterVideo: Boolean = false
)

class FavoriteViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getDatabase(app).favoriteDao()

    private val _state = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val favorites: StateFlow<List<FavoriteItem>> = _state
        .flatMapLatest { s ->
            dao.getFiltered(
                query     = s.query,
                sortBy    = s.sortBy,
                ascending = s.ascending,
                audioOnly = s.filterAudio,
                videoOnly = s.filterVideo
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isFavorite(id: String): Flow<Boolean> = dao.isFavorite(id)

    // ── Actions ───────────────────────────────────────────────────────────────

    fun toggle(media: DownloadedMedia, url: String = "", author: String = "", website: String = "") {
        viewModelScope.launch {
            if (dao.isFavorite(media.id).first()) {
                dao.deleteById(media.id)
            } else {
                dao.insert(
                    FavoriteItem(
                        id           = media.id,
                        url          = url,
                        title        = media.title,
                        author       = author,
                        thumbnailUrl = media.thumbnailUrl,
                        isAudio      = media.isAudio,
                        format       = media.format,
                        filePath     = media.filePath,
                        website      = website
                    )
                )
            }
        }
    }

    fun remove(id: String) = viewModelScope.launch { dao.deleteById(id) }

    fun clearAll() = viewModelScope.launch { dao.deleteAll() }

    // ── Filters / sort ────────────────────────────────────────────────────────

    fun setQuery(q: String)               = _state.update { it.copy(query = q) }
    fun setSortBy(s: String)              = _state.update { it.copy(sortBy = s) }
    fun toggleAscending()                 = _state.update { it.copy(ascending = !it.ascending) }
    fun setFilterAudio(v: Boolean)        = _state.update { it.copy(filterAudio = v, filterVideo = if (v) false else it.filterVideo) }
    fun setFilterVideo(v: Boolean)        = _state.update { it.copy(filterVideo = v, filterAudio = if (v) false else it.filterAudio) }
    fun clearFilters()                    = _state.update { FavoritesUiState() }
}
