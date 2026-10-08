package app.mismeet.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.mismeet.android.ui.ContactsScreen
import app.mismeet.android.ui.MapScreen
import app.mismeet.android.ui.MeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = AppModel.get(this)
        setContent {
            MaterialTheme {
                MismeetScreen(model)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppModel.get(this).becameActive()
    }
}

@Composable
private fun MismeetScreen(model: AppModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text("Contacts") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    label = { Text("Map") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Me") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            0 -> ContactsScreen(model, Modifier.padding(padding))
            1 -> MapScreen(model, Modifier.padding(padding))
            else -> MeScreen(model, Modifier.padding(padding))
        }
    }
}
