package com.omni.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

import com.omni.app.data.ytdlp.YtDlpManager

class SearchViewModel : ViewModel() {
    private val _searchResults = MutableStateFlow<List<YtDlpManager.SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            clearResults()
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                delay(500)

                val results = YtDlpManager.searchVideos(query)
                _searchResults.value = results

            } catch (e: Exception) {
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearResults() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }
}