package com.napolitain.arcade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.napolitain.arcade.ui.screens.ArcadeHomeScreen
import com.napolitain.arcade.ui.theme.ArcadeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArcadeTheme {
                ArcadeHomeScreen()
            }
        }
    }
}
