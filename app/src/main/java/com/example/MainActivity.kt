package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.ChatScreen
import com.example.ui.ChatViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ChatScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // RAM pressure changes while the app was away; re-measure before the next generation.
        viewModel.onEnterForeground()
    }

    override fun onStop() {
        super.onStop()
        // Drop mapped weights when backgrounded (Settings -> Response Tuning) so a long
        // download/decode cannot leave the process holding gigabytes it no longer uses.
        viewModel.onEnterBackground()
    }
}

