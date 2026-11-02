package com.photocompressorlite.myapp
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class AppState(
val screen: String = "home", val photos: List<Photo> = emptyList(), val selected: Set<Uri> = emptySet(),
val options: CompressionOptions = CompressionOptions(), val results: List<PhotoResult> = emptyList(),
val processing: Boolean = false, val current: Int = 0, val batchTotal: Int = 0, val cancelled: Boolean = false,
val loading: Boolean = false, val saving: Boolean = false, val saved: Set<String> = emptySet(),
val theme: String = "System", val history: List<HistoryEntry> = emptyList(), val message: String? = null
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = PhotoRepository(application)
    private val mutable = MutableStateFlow(AppState(theme = repository.theme, options = CompressionOptions(target = repository.defaultTarget, format = repository.defaultFormat), history = repository.history()))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var settingsReturn = "home"
    fun navigate(screen: String) {
        if (screen == "settings") settingsReturn = state.value.screen
        mutable.update {
            it.copy(screen = screen)
        }
    }
    fun back() {
        when (state.value.screen) {
            "processing" -> cancel()
            "options" -> navigate("select")
            "settings" -> navigate(settingsReturn)
            "results", "select" -> navigate("home")
            else -> Unit
        }
    }
    fun message(value: String?) {
        mutable.update {
            it.copy(message = value)
        }
    }
    fun add(uris: List<Uri>) {
        viewModelScope.launch {
            mutable.update {
                it.copy(loading = true)
            }
            var failed = 0
            val added = uris.distinct().mapNotNull {
                uri -> try {
                    repository.readPhoto(uri)
                } catch (e: Exception) {
                    failed++
                    null
                }
            }
            mutable.update {
                s -> s.copy(photos = (s.photos + added).distinctBy {
                    it.uri
                }, selected = s.selected + added.map {
                    it.uri
                }, loading = false, message = if (failed > 0) "$failed photos could not be opened." else null)
            }
        }
    }
    fun toggle(uri: Uri) {
        mutable.update {
            it.copy(selected = if (uri in it.selected) it.selected - uri else it.selected + uri)
        }
    }
    fun deselect() {
        mutable.update {
            it.copy(selected = emptySet())
        }
    }
    fun remove() {
        mutable.update {
            s -> s.copy(photos = s.photos.filterNot {
                it.uri in s.selected
            }, selected = emptySet())
        }
    }
    fun options(value: CompressionOptions) {
        mutable.update {
            it.copy(options = value)
        }
    }
    fun theme(value: String) {
        repository.preference("theme", value)
        mutable.update {
            it.copy(theme = value)
        }
    }
    fun defaultTarget(value: Long) {
        repository.target(value)
        options(state.value.options.copy(target = value))
    }
    fun defaultFormat(value: String) {
        repository.preference("format", value)
        options(state.value.options.copy(format = value))
    }
    fun clearHistory() {
        repository.clearHistory()
        mutable.update {
            it.copy(history = emptyList(), message = "Local history cleared.")
        }
    }
    fun start() {
        if (state.value.processing) return
        val photos = state.value.photos.filter {
            it.uri in state.value.selected
        }
        if (photos.isEmpty()) return
        val options = state.value.options
        mutable.update {
            it.copy(screen = "processing", processing = true, results = emptyList(), saved = emptySet(), current = 1, batchTotal = photos.size, cancelled = false)
        }
        job = viewModelScope.launch {
            try {
                photos.forEachIndexed {
                    index, photo -> mutable.update {
                        it.copy(current = index + 1)
                    }
                    val result = repository.compress(photo, options)
                    mutable.update {
                        it.copy(results = it.results + result)
                    }
                }
            }
            finally {
                repository.record(state.value.results)
                mutable.update {
                    it.copy(screen = "results", processing = false, history = repository.history())
                }
            }
        }
    }
    fun cancel() {
        mutable.update {
            it.copy(cancelled = true)
        }
        job?.cancel()
    }
    fun save() {
        if (state.value.saving) return
        viewModelScope.launch {
            mutable.update {
                it.copy(saving = true)
            }
            var success = 0
            var failed = 0
            state.value.results.filter {
                it.path != null && it.path !in state.value.saved
            }.forEach {
                result -> try {
                    repository.save(result)
                    success++
                    mutable.update {
                        it.copy(saved = it.saved + result.path!!)
                    }
                } catch (e: Exception) {
                    failed++
                }
            }
            mutable.update {
                it.copy(saving = false, message = "$success photos saved to Pictures/Photo Compressor Lite." + if (failed > 0) " $failed could not be saved. Check storage and try again." else "")
            }
        }
    }
    fun reset() {
        mutable.update {
            it.copy(screen = "select", photos = emptyList(), selected = emptySet(), results = emptyList(), saved = emptySet())
        }
    }
}
