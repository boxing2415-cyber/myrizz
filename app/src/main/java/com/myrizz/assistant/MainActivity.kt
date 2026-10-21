package com.myrizz.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModelProvider
import com.myrizz.assistant.ui.AppNav
import com.myrizz.assistant.ui.theme.MyRizzTheme

class MainActivity : AppCompatActivity() {

    /** A screen requested from outside (for example "open Settings" from the floating panel). */
    private val requestedRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestedRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        val controller = ViewModelProvider(this)[AssistantViewModel::class.java].controller
        setContent {
            MyRizzTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNav(controller = controller, requestedRoute = requestedRoute)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "com.myrizz.assistant.ROUTE"
    }
}
