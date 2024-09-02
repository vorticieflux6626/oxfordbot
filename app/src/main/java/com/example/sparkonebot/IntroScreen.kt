package com.example.oxfordbot

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView

@Composable
fun IntroScreen(onAnimationFinished: () -> Unit) {
    val context = LocalContext.current
    val videoView = remember { VideoView(context) }
    val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.logo_sparkonelabs_0}")

    DisposableEffect(videoView) {
        videoView.setVideoURI(videoUri)
        videoView.setOnCompletionListener {
            onAnimationFinished()
        }
        videoView.start()

        onDispose {
            videoView.stopPlayback()
        }
    }

    AndroidView(
        factory = { videoView },
        modifier = Modifier.fillMaxSize()
    )
}