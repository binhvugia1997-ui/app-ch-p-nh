package com.aiphotographer.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiphotographer.camera.CameraSession
import com.aiphotographer.perception.SourceStatus

@Composable internal fun PerceptionStatus(camera: CameraSession) {
    val pipeline = camera.perception ?: return
    val state by pipeline.state.collectAsStateWithLifecycle()
    val message = when {
        state.pose.status == SourceStatus.UNAVAILABLE -> R.string.perception_unavailable
        state.pose.status == SourceStatus.INITIALIZING -> R.string.perception_starting
        state.face.status == SourceStatus.UNAVAILABLE -> R.string.face_unavailable
        else -> null
    }
    message?.let { Text(stringResource(it), Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium) }
}
