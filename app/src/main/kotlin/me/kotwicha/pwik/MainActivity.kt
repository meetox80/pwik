package me.kotwicha.pwik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import me.kotwicha.pwik.ui.nav.AppRoot
import me.kotwicha.pwik.ui.nav.MainViewModel
import me.kotwicha.pwik.ui.planner.PlannerViewModel

class MainActivity : ComponentActivity() {
    private val main: MainViewModel by viewModels()
    private val planner: PlannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot(main, planner) }
    }
}
