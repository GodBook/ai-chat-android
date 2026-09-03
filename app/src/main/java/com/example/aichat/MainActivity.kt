package com.example.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aichat.ui.AiChatApp
import com.example.aichat.ui.AiChatTheme
import com.example.aichat.ui.MainViewModel
import com.example.aichat.ui.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val application = application as AiChatApplication
        setContent {
            AiChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(application.container),
                    )
                    AiChatApp(mainViewModel)
                }
            }
        }
    }
}
