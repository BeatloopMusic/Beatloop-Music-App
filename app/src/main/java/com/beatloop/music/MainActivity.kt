package com.beatloop.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.beatloop.music.playback.MusicService
import com.beatloop.music.ui.BeatloopApp
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.PlayerConnection
import com.beatloop.music.ui.viewmodel.AppThemeState
import com.beatloop.music.ui.viewmodel.AppThemeViewModel
import com.beatloop.music.ui.theme.BeatloopTheme
import com.beatloop.music.data.preferences.ThemeMode
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        requestPermissions()
        
        setContent {
            val themeViewModel: AppThemeViewModel = hiltViewModel()
            val themeState by themeViewModel.themeState.collectAsState()
            val darkTheme = when (themeState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            Crossfade(
                targetState = AppThemeState(
                    themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
                    dynamicColorEnabled = themeState.dynamicColorEnabled,
                    amoledBlackEnabled = themeState.amoledBlackEnabled
                ),
                animationSpec = tween(420),
                label = "theme_crossfade"
            ) { animatedThemeState ->
                BeatloopTheme(
                    darkTheme = animatedThemeState.themeMode == ThemeMode.DARK,
                    dynamicColor = animatedThemeState.dynamicColorEnabled,
                    amoledBlack = animatedThemeState.amoledBlackEnabled
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CompositionLocalProvider(
                            LocalPlayerConnection provides playerConnection
                        ) {
                            BeatloopApp()
                        }
                    }
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        connectToMusicService()
    }
    
    override fun onStop() {
        super.onStop()
        releaseMediaController()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseMediaController()
    }
    
    private fun connectToMusicService() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val controller = controllerFuture.get()
                playerConnection = PlayerConnection(controller, lifecycleScope)
            },
            MoreExecutors.directExecutor()
        )
    }
    
    private fun releaseMediaController() {
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
            playerConnection = null
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
