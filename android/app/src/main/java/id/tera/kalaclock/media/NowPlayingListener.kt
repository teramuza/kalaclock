package id.tera.kalaclock.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(val title: String, val artist: String, val art: Bitmap?)

object NowPlayingState {
    private val _value = MutableStateFlow<NowPlaying?>(null)
    val value: StateFlow<NowPlaying?> = _value.asStateFlow()
    internal fun set(v: NowPlaying?) { _value.value = v }

    fun hasAccess(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
            ?.contains(c.packageName) == true
}

/** Only exists to hold notification access; reads active media sessions. */
class NowPlayingListener : NotificationListenerService() {
    private lateinit var msm: MediaSessionManager
    private val component by lazy { ComponentName(this, NowPlayingListener::class.java) }
    private val controllers = mutableListOf<MediaController>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artJob: Job? = null
    private var lastArt: Pair<String, Bitmap>? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    override fun onListenerConnected() {
        msm = getSystemService(MediaSessionManager::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsListener, component)
        attach(msm.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        if (::msm.isInitialized) msm.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(callback) }
        controllers.clear()
        NowPlayingState.set(null)
    }

    private fun attach(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers.clear()
        controllers.addAll(list)
        controllers.forEach { it.registerCallback(callback) }
        refresh()
    }

    private fun refresh() {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        val m = c?.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (m == null || title.isNullOrBlank()) {
            NowPlayingState.set(null)
            return
        }
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val key = "$title|$artist"
        if (art != null) {
            NowPlayingState.set(NowPlaying(title, artist, art))
            return
        }
        // Many players only expose an artwork URI; show text now, then fill in the art.
        val cached = lastArt?.takeIf { it.first == key }?.second
        NowPlayingState.set(NowPlaying(title, artist, cached))
        if (cached != null) return
        val uri = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        ).firstNotNullOfOrNull { m.getString(it)?.takeIf { u -> u.isNotBlank() } } ?: return
        artJob?.cancel()
        artJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadArt(uri) } ?: return@launch
            lastArt = key to bmp
            NowPlayingState.set(NowPlaying(title, artist, bmp))
        }
    }

    private fun loadArt(uri: String): Bitmap? = runCatching {
        val u = Uri.parse(uri)
        when (u.scheme) {
            "http", "https" -> URL(uri).openStream().use { BitmapFactory.decodeStream(it) }
            else -> contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
