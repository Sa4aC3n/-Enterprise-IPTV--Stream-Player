package com.enterprise.iptv.presentation.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.domain.recorder.RecordState
import com.enterprise.iptv.domain.recorder.StreamRecorder
import com.enterprise.iptv.domain.repository.ChannelRepository
import com.enterprise.iptv.presentation.player.PlaybackStatus
import com.enterprise.iptv.presentation.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState
    data class Success(
        val channels: List<Channel>,
        val categories: List<String>,
        val selectedCategory: String?,
        val countries: List<String>,
        val selectedCountry: String?,
        val currentChannel: Channel?,
        val playbackStatus: PlaybackStatus,
        val recordState: RecordState,
        val isImporting: Boolean = false,
        val searchQuery: String = ""
    ) : ChannelsUiState
    data class Error(val message: String) : ChannelsUiState
}

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val repository: ChannelRepository,
    val playerEngine: PlayerEngine,
    private val streamRecorder: StreamRecorder
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedCountry = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    private val _recordState = MutableStateFlow<RecordState>(RecordState.Idle)
    private val _isImporting = MutableStateFlow(false)
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen = _isFullscreen.asStateFlow()

    private var recordingJob: Job? = null

    init {
        // 1. تحميل القنوات الافتراضية محلياً من Assets فوراً وبدون إنترنت
        viewModelScope.launch {
            repository.initializeDefaultChannels()
            // 2. فحص خلفي صامت وغير معطل للواجهة لتحديثات GitHub في حال تغيير الروابط
            repository.syncRemoteChannels()
        }
    }

    private data class ChannelFilterResult(
        val channels: List<Channel>,
        val categories: List<String>,
        val countries: List<String>,
        val selectedCategory: String?,
        val selectedCountry: String?,
        val query: String
    )

    private val channelDataFlow = combine(
        _selectedCategory,
        _selectedCountry,
        _searchQuery
    ) { category, country, query ->
        Triple(category, country, query)
    }.flatMapLatest { (category, country, query) ->
        combine(
            repository.getFilteredChannels(category, country, null, query.ifBlank { null }),
            repository.getAllCategories(),
            repository.getAllCountries()
        ) { channels, categories, countries ->
            ChannelFilterResult(channels, categories, countries, category, country, query)
        }
    }

    val uiState: StateFlow<ChannelsUiState> = combine(
        channelDataFlow,
        _currentChannel,
        playerEngine.playbackStatus,
        _recordState,
        _isImporting
    ) { filterResult, currentChannel, playbackStatus, recordState, isImporting ->
        if (currentChannel == null && filterResult.channels.isNotEmpty()) {
            onChannelSelect(filterResult.channels.first())
        }

        ChannelsUiState.Success(
            channels = filterResult.channels,
            categories = filterResult.categories,
            selectedCategory = filterResult.selectedCategory,
            countries = filterResult.countries,
            selectedCountry = filterResult.selectedCountry,
            currentChannel = currentChannel,
            playbackStatus = playbackStatus,
            recordState = recordState,
            isImporting = isImporting,
            searchQuery = filterResult.query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChannelsUiState.Loading
    )

    fun onCategorySelect(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun onCountrySelect(country: String?) {
        _selectedCountry.value = if (_selectedCountry.value == country) null else country
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onChannelSelect(channel: Channel) {
        _currentChannel.value = channel
        playerEngine.playChannel(channel.streamUrl)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.id, !channel.isFavorite)
        }
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun setFullscreen(enabled: Boolean) {
        _isFullscreen.value = enabled
    }

    fun importM3uPlaylist(url: String) {
        viewModelScope.launch {
            _isImporting.value = true
            repository.importM3uFromUrl(url, "user_custom")
            _isImporting.value = false
        }
    }

    fun startRecording() {
        val channel = _currentChannel.value ?: return
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            streamRecorder.recordStream(channel.name, channel.streamUrl).collect { state ->
                _recordState.value = state
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        _recordState.value = RecordState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        playerEngine.release()
        recordingJob?.cancel()
    }
}
