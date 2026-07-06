package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.audio.AudioEngine
import com.example.ui.SequencerScreen
import com.example.ui.SequencerViewModel
import com.example.ui.SequencerViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize custom Audio Engine synthesis & MIDI system
        AudioEngine.initialize(this)

        // 2. Instantiate persistent state ViewModel
        val viewModel: SequencerViewModel by viewModels {
            SequencerViewModelFactory(this)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.StudioDarkBg
                ) {
                    SequencerScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop audio processing thread when app is minimized to save battery & memory
        AudioEngine.stopEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioEngine.stopEngine()
    }
}

