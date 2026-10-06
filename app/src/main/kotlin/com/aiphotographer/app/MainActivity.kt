package com.aiphotographer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiphotographer.camera.*
import com.aiphotographer.model.*

class MainActivity : ComponentActivity() {
    private val launchNs = SystemClock.elapsedRealtimeNanos()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CameraApp(launchNs, intent.getBooleanExtra("benchmarkRgb", false), intent.getBooleanExtra("analysis720p", false)) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable private fun CameraApp(launchNs: Long, benchmarkRgbAtLaunch: Boolean, analysis720p: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(granted()) }
    var licencesVisible by remember { mutableStateOf(false) }
    if (licencesVisible) {
        val notices = remember { context.assets.open("third_party_notices.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { licencesVisible = false }, title = { Text(stringResource(R.string.licences)) },
            text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { licencesVisible = false }) { Text(stringResource(R.string.close)) } })
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permission = granted() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (!permission) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.permission_explanation))
            Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.permission_allow)) }
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            }) { Text(stringResource(R.string.permission_settings)) }
        }
        return
    }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var facing by rememberSaveable { mutableStateOf(CameraFacing.BACK) }
    var resolution by rememberSaveable { mutableStateOf(if (analysis720p) AnalysisResolution.R720P else AnalysisResolution.R480P) }
    var aspect by rememberSaveable { mutableFloatStateOf(3f / 4f) }
    var generation by remember { mutableIntStateOf(0) }
    var rgbBenchmark by rememberSaveable { mutableStateOf(benchmarkRgbAtLaunch) }
    var session by remember { mutableStateOf<CameraSession?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
          val viewAspect = if (landscape) 1f / aspect else aspect
          val previewWidth = minOf(maxWidth, maxHeight * viewAspect)
          Box(Modifier.size(previewWidth, previewWidth / viewAspect).align(Alignment.Center).clipToBounds()) {
            key(facing, resolution, aspect, landscape, generation, rgbBenchmark) {
                val preview = remember { PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                } }
                val monitor = remember { DeviceMonitor(context) }
                val camera = remember { CameraSession(context, owner, preview, monitor, facing, resolution, rgbBenchmark, launchNs) }
                DisposableEffect(camera) {
                    session = camera
                    val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { if (it == PreviewView.StreamState.STREAMING) camera.previewStreaming() }
                    preview.previewStreamState.observe(owner, observer)
                    val lifecycleObserver = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) monitor.stop()
                        if (event == Lifecycle.Event.ON_START) { monitor.start(); camera.router.resetSessionWindow() }
                    }
                    owner.lifecycle.addObserver(lifecycleObserver)
                    onDispose {
                        preview.previewStreamState.removeObserver(observer)
                        owner.lifecycle.removeObserver(lifecycleObserver)
                        camera.close()
                        if (session === camera) session = null
                    }
                }
                AndroidView(factory = { preview.apply { doOnLayout { camera.start() } } }, modifier = Modifier.fillMaxSize())
                DeveloperOverlay(camera)
            }
          }
        }
        session?.let { camera ->
            val state by camera.state.collectAsStateWithLifecycle()
            val message = when (state.mode) {
                SessionMode.STARTING -> R.string.starting
                SessionMode.FULL -> R.string.full_mode
                SessionMode.ANALYSIS_ONLY -> R.string.analysis_only
                SessionMode.CAPTURE_ONLY -> R.string.capture_only
                SessionMode.ERROR -> R.string.camera_error
            }
            Text(stringResource(message), Modifier.padding(horizontal = 16.dp))
            when (state.capture) {
                CaptureStatus.SAVED -> Text(stringResource(R.string.saved, state.savedLocation.orEmpty()), Modifier.padding(horizontal = 16.dp))
                CaptureStatus.ERROR -> Text(stringResource(R.string.save_error))
                else -> Unit
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { facing = if (facing == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK }, enabled = state.capture != CaptureStatus.SAVING) {
                    Text(stringResource(R.string.switch_camera))
                }
                Button(onClick = camera::takePhoto, enabled = state.mode !in listOf(SessionMode.STARTING, SessionMode.ERROR) && state.capture != CaptureStatus.SAVING) {
                    Text(stringResource(if (state.capture == CaptureStatus.SAVING) R.string.saving else R.string.capture))
                }
                if (state.mode == SessionMode.ERROR) TextButton(onClick = { generation++ }) { Text(stringResource(R.string.retry)) }
            }
        }
        DeveloperControls(resolution, { resolution = it }, aspect, { aspect = it; generation++ }, rgbBenchmark, { rgbBenchmark = it })
        TextButton(onClick = { licencesVisible = true }) { Text(stringResource(R.string.licences)) }
    }
}
