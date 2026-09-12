package com.example.aichat

import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichat.ui.AiChatApp
import com.example.aichat.ui.AiChatTheme
import com.example.aichat.ui.MainViewModel
import com.example.aichat.ui.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as AiChatApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) mainViewModel.receiveShare(this, intent)
        setContent {
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()
            AiChatTheme(themeColorKey = state.config.themeColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AiChatApp(mainViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.receiveShare(this, intent)
    }
}
