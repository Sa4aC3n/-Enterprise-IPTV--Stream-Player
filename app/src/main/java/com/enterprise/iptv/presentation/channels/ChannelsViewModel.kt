package com.enterprise.iptv.presentation.channels

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.iptv.domain.cast.CastDevice
import com.enterprise.iptv.domain.cast.CastState
import com.enterprise.iptv.domain.cast.DlnaCastManager
import com.enterprise.iptv.domain.chapter.SmartChapter
import com.enterprise.iptv.domain.epg.EpgManager
import com.enterprise.iptv.domain.healing.ServerHealth
import com.enterprise.iptv.domain.healing.StreamAutoHealer
import com.enterprise.iptv.domain.kids.KidsSpaceManager
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.domain.model.EpgProgram
import com.enterprise.iptv.domain.recorder.RecordState
import com.enterprise.iptv.domain.recorder.StreamRecorder
import com.enterprise.iptv.domain.repository.ChannelRepository
import com.enterprise.iptv.domain.voice.VoiceCommand
import com.enterprise.iptv.domain.voice.VoiceRecognitionManager
import com.enterprise.iptv.presentation.multiview.MultiViewLayout
import com.enterprise.iptv.presentation.multiview.MultiViewManager
import com.enterprise.iptv.presentation.player.PlaybackStatus
import com.enterprise.iptv.presentation.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ImportState {
    data object Idle : ImportState
    data object Loading : ImportState
    data class Success(val count: Int) : ImportState
    data class Error(val message: String) : ImportState
}

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
        val importState: ImportState = ImportState.Idle,
        val searchQuery: String = "",
        val epgNow: EpgProgram? = null,
        val epgNext: EpgProgram? = null,
        val isKidsMode: Boolean = false,
        val autoHealStatus: String? = null
    ) : ChannelsUiState
    data class Error(val message: String) : ChannelsUiState
}

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val repository: ChannelRepository,
    val playerEngine: PlayerEngine,
    val multiViewManager: MultiViewManager,
    private val streamRecorder: StreamRecorder,
    val epgManager: EpgManager,
    val kidsSpaceManager: KidsSpaceManager,
    val autoHealer: StreamAutoHealer,
    val castManager: DlnaCastManager,
    val voiceManager: VoiceRecognitionManager
) : ViewModel() {

    val isMultiViewActive = multiViewManager.isActive
    val multiViewLayout = multiViewManager.layout
    val isKidsModeActive = kidsSpaceManager.isKidsModeActive

    // حالة الأوامر والتعرف الصوتي (Voice Commands)
    val voiceState = voiceManager.voiceState

    // حالة وأجهزة البث للشاشات الذكية (DLNA / Cast)
    val discoveredCastDevices = castManager.discoveredDevices
    val isScanningForCast = castManager.isScanning
    val activeCastDevice = castManager.activeCastDevice
    val castState = castManager.castState

    private val _autoHealStatus = MutableStateFlow<String?>(null)
    val autoHealStatus = _autoHealStatus.asStateFlow()

    private val _currentChannelEpg = MutableStateFlow<Pair<EpgProgram?, EpgProgram?>>(Pair(null, null))
    val currentChannelEpg = _currentChannelEpg.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _selectedCountry = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    private val _recordState = MutableStateFlow<RecordState>(RecordState.Idle)
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    private val _savedRecordings = MutableStateFlow<List<File>>(emptyList())
    val savedRecordings = _savedRecordings.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen = _isFullscreen.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode = _isInPipMode.asStateFlow()

    private val _sleepTimerSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerSeconds = _sleepTimerSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null

    private var recordingJob: Job? = null
    private var activeRecordingFile: File? = null

    init {
        // 1. تحميل القنوات الافتراضية محلياً من Assets فوراً وبدون إنترنت
        viewModelScope.launch {
            repository.initializeDefaultChannels()
            // 2. فحص خلفي صامت وغير معطل للواجهة لتحديثات GitHub في حال تغيير الروابط
            repository.syncRemoteChannels()
        }
        refreshSavedRecordings()
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
        _searchQuery,
        kidsSpaceManager.isKidsModeActive
    ) { category, country, query, isKidsActive ->
        listOf(category ?: "", country ?: "", query, isKidsActive.toString())
    }.flatMapLatest {
        val category = _selectedCategory.value
        val country = _selectedCountry.value
        val query = _searchQuery.value
        val isKidsActive = kidsSpaceManager.isKidsModeActive.value
        combine(
            repository.getFilteredChannels(category, country, null, query.ifBlank { null }),
            repository.getAllCategories(),
            repository.getAllCountries()
        ) { channels, categories, countries ->
            val finalChannels = if (isKidsActive) {
                channels.filter { kidsSpaceManager.isKidsChannel(it) }
            } else {
                channels
            }
            val finalCategories = if (isKidsActive) {
                categories.filter { cat ->
                    val lower = cat.lowercase()
                    lower.contains("طفل") || lower.contains("أطفال") || lower.contains("اطفال") || lower.contains("كرتون") || lower.contains("kids") || lower.contains("cartoon")
                }
            } else {
                categories
            }
            ChannelFilterResult(finalChannels, finalCategories, countries, category, country, query)
        }
    }

    val uiState: StateFlow<ChannelsUiState> = combine(
        channelDataFlow,
        _currentChannel,
        playerEngine.playbackStatus,
        _recordState,
        _importState,
        kidsSpaceManager.isKidsModeActive,
        _currentChannelEpg,
        _autoHealStatus
    ) { args: Array<Any?> ->
        val filterResult = args[0] as ChannelFilterResult
        val currentChannel = args[1] as? Channel
        val playbackStatus = args[2] as PlaybackStatus
        val recordState = args[3] as RecordState
        val importState = args[4] as ImportState
        val isKidsActive = args[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val epg = args[6] as Pair<EpgProgram?, EpgProgram?>
        val autoHealStatus = args[7] as? String

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
            importState = importState,
            searchQuery = filterResult.query,
            epgNow = epg.first,
            epgNext = epg.second,
            isKidsMode = isKidsActive,
            autoHealStatus = autoHealStatus
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
        _currentChannelEpg.value = epgManager.getNowAndNext(channel)
        _autoHealStatus.value = null
        playerEngine.playChannel(channel.streamUrl, channel.backupUrls)
    }

    fun autoHealCurrentChannel() {
        val current = _currentChannel.value ?: return
        val allUrls = (listOf(current.streamUrl) + current.backupUrls).filter { it.isNotBlank() }
        if (allUrls.isEmpty()) return

        viewModelScope.launch {
            _autoHealStatus.value = "جاري فحص استقرار وجودة السيرفرات..."
            val fastest = autoHealer.findFastestServer(allUrls)
            if (fastest != null && fastest.isReachable) {
                _autoHealStatus.value = "سيرفر مستقر (${fastest.latencyMs}ms)"
                playerEngine.playChannel(fastest.url, current.backupUrls)
            } else {
                _autoHealStatus.value = "السيرفرات البديلة تحت الصيانة"
            }
        }
    }

    fun getChannelEpg(channel: Channel): Pair<EpgProgram?, EpgProgram?> = epgManager.getNowAndNext(channel)

    fun getChannelSchedule(channel: Channel): List<EpgProgram> = epgManager.getScheduleForChannel(channel)

    fun enableKidsMode() {
        kidsSpaceManager.enableKidsMode()
    }

    fun disableKidsMode(pin: String): Boolean {
        return kidsSpaceManager.disableKidsMode(pin)
    }

    fun playNextChannel() {
        val current = _currentChannel.value ?: return
        val list = when (val state = uiState.value) {
            is ChannelsUiState.Success -> state.channels
            else -> emptyList()
        }
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex != -1) {
            val nextIndex = (currentIndex + 1) % list.size
            onChannelSelect(list[nextIndex])
        }
    }

    fun playPreviousChannel() {
        val current = _currentChannel.value ?: return
        val list = when (val state = uiState.value) {
            is ChannelsUiState.Success -> state.channels
            else -> emptyList()
        }
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex != -1) {
            val prevIndex = (currentIndex - 1 + list.size) % list.size
            onChannelSelect(list[prevIndex])
        }
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

    fun setPipMode(enabled: Boolean) {
        _isInPipMode.value = enabled
    }

    fun importM3uPlaylist(url: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            val result = repository.importM3uFromUrl(url.trim(), "user_url_${System.currentTimeMillis()}")
            result.fold(
                onSuccess = { count ->
                    if (count > 0) {
                        _importState.value = ImportState.Success(count)
                    } else {
                        _importState.value = ImportState.Error("الرابط لا يحتوي على أي قنوات صالحة للبث")
                    }
                },
                onFailure = { e ->
                    _importState.value = ImportState.Error("تعذر استيراد الرابط: ${e.localizedMessage ?: "خطأ في الشبكة أو صيغة الملف"}")
                }
            )
        }
    }

    fun importM3uFromUri(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _importState.value = ImportState.Error("تعذر الوصول للملف المختار")
                    return@launch
                }
                val result = repository.importM3uFromInputStream(inputStream, "user_local_${System.currentTimeMillis()}")
                result.fold(
                    onSuccess = { count ->
                        if (count > 0) {
                            _importState.value = ImportState.Success(count)
                        } else {
                            _importState.value = ImportState.Error("الملف المختار لا يحتوي على أي قنوات صالحة (M3U/M3U8)")
                        }
                    },
                    onFailure = { e ->
                        _importState.value = ImportState.Error("فشل استيراد الملف: ${e.localizedMessage ?: "صيغة غير مدعومة"}")
                    }
                )
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.localizedMessage ?: "حدث خطأ أثناء قراءة الملف")
            }
        }
    }

    fun dismissImportState() {
        _importState.value = ImportState.Idle
    }

    fun startRecording() {
        val channel = _currentChannel.value ?: return
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            streamRecorder.recordStream(channel.name, channel.streamUrl).collect { state ->
                _recordState.value = state
                if (state is RecordState.Recording) {
                    activeRecordingFile = File(state.filePath)
                } else if (state is RecordState.Completed) {
                    refreshSavedRecordings()
                }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        val file = activeRecordingFile
        if (file != null && file.exists() && file.length() > 0) {
            _recordState.value = RecordState.Completed(file)
            refreshSavedRecordings()
        } else {
            _recordState.value = RecordState.Idle
        }
        activeRecordingFile = null
    }

    fun dismissRecordCompleted() {
        _recordState.value = RecordState.Idle
    }

    fun refreshSavedRecordings() {
        _savedRecordings.value = streamRecorder.getSavedRecordings()
    }

    fun deleteRecording(file: File) {
        streamRecorder.deleteRecording(file)
        refreshSavedRecordings()
    }

    fun playRecording(file: File) {
        playerEngine.playFile(file)
    }

    fun seekToChapter(chapter: SmartChapter) {
        playerEngine.seekToChapter(chapter)
    }

    fun seekToNextChapter(): Boolean {
        return playerEngine.seekToNextChapter()
    }

    fun seekToPreviousChapter(): Boolean {
        return playerEngine.seekToPreviousChapter()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _sleepTimerSeconds.value = null
            return
        }
        val totalSeconds = minutes * 60L
        _sleepTimerSeconds.value = totalSeconds
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000L)
                remaining--
                _sleepTimerSeconds.value = remaining
            }
            playerEngine.pause()
            _sleepTimerSeconds.value = null
        }
    }

    fun startMultiView(layout: MultiViewLayout = MultiViewLayout.DUAL) {
        val current = _currentChannel.value
        val list = when (val state = uiState.value) {
            is ChannelsUiState.Success -> state.channels
            else -> emptyList()
        }
        playerEngine.pause()
        multiViewManager.enter(current, list, layout)
    }

    fun stopMultiView(targetChannel: Channel? = null) {
        val nextChannel = targetChannel ?: multiViewManager.exit()
        if (nextChannel != null) {
            onChannelSelect(nextChannel)
        } else {
            playerEngine.play()
        }
    }

    // --- وظائف البث للشاشات الذكية (DLNA / Cast) ---

    fun startCastDiscovery() {
        castManager.startDiscovery()
    }

    fun stopCastDiscovery() {
        castManager.stopDiscovery()
    }

    fun castRecordingToDevice(file: File, device: CastDevice) {
        playerEngine.pause()
        castManager.castRecording(file, device)
    }

    fun castCurrentChannelToDevice(device: CastDevice) {
        val channel = _currentChannel.value ?: return
        playerEngine.pause()
        castManager.castLiveStream(channel.streamUrl, channel.name, device)
    }

    fun toggleCastPlayback() {
        when (castState.value) {
            is CastState.Playing -> castManager.pause()
            is CastState.Paused, is CastState.Connected -> castManager.play()
            else -> {}
        }
    }

    fun disconnectCast() {
        castManager.stop()
        playerEngine.play()
    }

    // إدارة الأوامر والتعرف الصوتي (Voice Commands)
    fun startVoiceListening() {
        voiceManager.startListening()
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
    }

    fun resetVoiceState() {
        voiceManager.resetState()
    }

    fun executeVoiceCommand(command: VoiceCommand) {
        when (command) {
            is VoiceCommand.PlayChannel -> {
                val query = command.channelQuery.trim().lowercase()
                val match = _allChannels.firstOrNull {
                    it.name.lowercase().contains(query) || query.contains(it.name.lowercase())
                } ?: _allChannels.firstOrNull {
                    it.groupTitle.lowercase().contains(query)
                }
                if (match != null) {
                    selectChannel(match)
                } else {
                    onSearchQueryChange(command.channelQuery)
                }
            }
            is VoiceCommand.Search -> {
                onSearchQueryChange(command.query)
            }
            VoiceCommand.OpenKidsMode -> {
                kidsSpaceManager.enableKidsMode()
            }
            VoiceCommand.OpenSleepTimer -> {
                setSleepTimer(30)
            }
            VoiceCommand.ToggleRecording -> {
                if (recordState.value is RecordState.Recording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            }
            VoiceCommand.OpenCast -> {
                startCastDiscovery()
            }
            VoiceCommand.ToggleVocalDsp -> {
                playerEngine.cycleVocalDspMode()
            }
            VoiceCommand.ToggleCaptions -> {
                playerEngine.toggleCaptions()
            }
            VoiceCommand.ToggleFullscreen -> {}
            is VoiceCommand.Unrecognized -> {
                if (command.rawText.isNotBlank()) {
                    onSearchQueryChange(command.rawText)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerEngine.release()
        multiViewManager.release()
        castManager.stop()
        voiceManager.stopListening()
        recordingJob?.cancel()
        sleepTimerJob?.cancel()
    }
}
