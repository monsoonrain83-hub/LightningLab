package com.lightninglab

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, RESULTS, ABOUT }
data class Item(val event: LightningEvent, val thumb: Bitmap, val selected: Boolean = true)

class MainVM(app: Application) : AndroidViewModel(app) {
    var screen by mutableStateOf(Screen.HOME)
    var uri by mutableStateOf<Uri?>(null)
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var stage by mutableStateOf(R.string.stage_scan)
    var sensitivity by mutableStateOf(0.5f)
    var composite by mutableStateOf<Bitmap?>(null)
    var infoRes by mutableStateOf<Int?>(null)
    var infoArgs by mutableStateOf<List<Any>>(emptyList())
    val items = mutableStateListOf<Item>()
    private var job: Job? = null

    private fun info(res: Int, vararg a: Any) { infoRes = res; infoArgs = a.toList() }

    fun startDetect(u: Uri) {
        job?.cancel(); uri = u; items.clear(); composite = null; infoRes = null
        busy = true; progress = 0f; stage = R.string.stage_scan
        job = viewModelScope.launch {
            try {
                val det = Detector(getApplication())
                withContext(Dispatchers.Default) {
                    val evs = det.detect(u, sensitivity, onProgress = { progress = it },
                        onStage = { stage = it }, cancelled = { !isActive })
                    stage = R.string.stage_thumbs
                    for (e in evs.sortedByDescending { it.score }.take(60).sortedBy { it.startMs }) {
                        det.frame(u, e.bestMs, 480)?.let { items += Item(e, it) }
                    }
                }
                if (items.isEmpty()) info(R.string.no_events) else screen = Screen.RESULTS
            } catch (c: CancellationException) { throw c
            } catch (e: Exception) { info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    fun cancel() { job?.cancel(); busy = false }

    fun toggle(i: Int) { items[i] = items[i].copy(selected = !items[i].selected) }
    fun setAll(v: Boolean) { for (i in items.indices) items[i] = items[i].copy(selected = v) }

    fun compose() {
        val sel = items.filter { it.selected }; val u = uri ?: return
        if (sel.isEmpty() || busy) return
        busy = true; progress = 0f; stage = R.string.stage_compose; infoRes = null
        job = viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.Default) {
                    val det = Detector(getApplication())
                    val baseItem = sel.maxByOrNull { it.event.score }!!
                    val base = det.frame(u, baseItem.event.bestMs, 2560) ?: return@withContext null
                    val others = sel.filter { it !== baseItem }.asSequence().map { det.frame(u, it.event.bestMs, 2560) }
                    Composer.compose(base, others)
                }
                if (r != null) { composite = r.bitmap; info(R.string.composed_info, r.used, r.skipped) }
            } catch (c: CancellationException) { throw c
            } catch (e: Throwable) { info(R.string.error_generic, e.message ?: e.javaClass.simpleName)
            } finally { busy = false }
        }
    }

    fun save() {
        val b = composite ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { Exporter.savePng(getApplication(), b, true); info(R.string.saved) }
            catch (e: Exception) { info(R.string.error_generic, e.message ?: "save") }
        }
    }
}
