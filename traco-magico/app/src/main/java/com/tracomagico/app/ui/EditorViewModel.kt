package com.tracomagico.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracomagico.app.data.Images
import com.tracomagico.app.filters.FilterEngine
import com.tracomagico.app.filters.Style
import com.tracomagico.app.filters.Styles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    var photo by mutableStateOf<Bitmap?>(null)          // prévia da foto (≤ 1280 px)
        private set
    var result by mutableStateOf<Bitmap?>(null)         // desenho da prévia
        private set
    var rendering by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var full: Bitmap? = null                    // foto em alta (≤ 2048 px) para salvar
    private var thumbSrc: Bitmap? = null
    private val thumbs = ConcurrentHashMap<String, Bitmap>()
    private val thumbGate = Semaphore(2)

    val style = MutableStateFlow(Styles.all.first())
    val intensity = MutableStateFlow(0.5f)
    var photoMix by mutableStateOf(0f)                  // 0 = só desenho, 1 = só foto

    private var loadedUri: Uri? = null
    private var renderJob: Job? = null

    fun load(uri: Uri) {
        if (uri == loadedUri) return
        loadedUri = uri
        photo = null; result = null; error = null; thumbs.clear()
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { Images.load(getApplication(), uri, 2048) }
                full = f
                val p = withContext(Dispatchers.Default) { Images.fit(f, 1280) }
                thumbSrc = withContext(Dispatchers.Default) { Images.fit(p, 200) }
                photo = p
                startRendering()
            } catch (t: Throwable) {
                error = "Não foi possível abrir a foto"
            }
        }
    }

    private fun startRendering() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            combine(style, intensity) { s, k -> s to k }
                .debounce(90)
                .collectLatest { (s, k) ->
                    val p = photo ?: return@collectLatest
                    rendering = true
                    val r = withContext(Dispatchers.Default) { FilterEngine.apply(p, s, k) }
                    result = r
                    rendering = false
                }
        }
    }

    fun select(s: Style) { style.value = s }
    fun setIntensity(v: Float) { intensity.value = v }

    suspend fun thumbnail(s: Style): Bitmap? {
        thumbs[s.id]?.let { return it }
        val src = thumbSrc ?: return null
        return thumbGate.withPermit {
            thumbs[s.id] ?: withContext(Dispatchers.Default) { FilterEngine.apply(src, s, 0.5f) }.also { thumbs[s.id] = it }
        }
    }

    /** Renderiza na resolução alta, já com a mistura da foto. */
    suspend fun renderFinal(): Bitmap? {
        val f = full ?: return null
        return withContext(Dispatchers.Default) {
            fun at(src: Bitmap) = FilterEngine.blend(FilterEngine.apply(src, style.value, intensity.value), src, photoMix)
            try {
                at(f)
            } catch (oom: OutOfMemoryError) {
                // aparelho com pouca memória: usa a resolução da prévia
                photo?.let { at(it) }
            }
        }
    }
}
