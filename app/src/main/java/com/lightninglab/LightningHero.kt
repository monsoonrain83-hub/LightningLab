package com.lightninglab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Compact cinematic home header. Uses both bundled user-provided storm clips as a muted,
 * looping playlist. Playback is released with the composable and paused in the background.
 */
@Composable
fun LightningHero(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            val clips = listOf(
                MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.lightning_header}"),
                MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.lightning_hero}")
            )
            setMediaItems(clips)
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(mediaPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mediaPlayer.play()
                Lifecycle.Event.ON_STOP -> mediaPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaPlayer.release()
        }
    }

    Box(modifier.clip(RoundedCornerShape(0.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.rgb(7, 13, 23))
                    setKeepContentOnPlayerReset(true)
                    player = mediaPlayer
                    contentDescription = "ویدئوی پس‌زمینه ابرهای توفانی و رعدوبرق"
                }
            },
            update = { view ->
                if (view.player !== mediaPlayer) view.player = mediaPlayer
            }
        )
        // Keep the real footage visible while making the compact wordmark readable.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x44050B16),
                    0.42f to Color(0x16050B16),
                    1f to Color(0xD9081220)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0x6607111E),
                    0.58f to Color.Transparent,
                    1f to Color(0x2207111E)
                )
            )
        )
    }
}
