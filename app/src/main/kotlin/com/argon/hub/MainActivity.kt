package com.argon.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.argon.hub.data.AuthManager
import com.argon.hub.data.AuthState
import com.argon.hub.data.NetworkManager
import com.argon.hub.data.OtaManager
import com.argon.hub.ui.HubScreen
import com.argon.hub.ui.ServiceViewerScreen
import com.argon.hub.ui.auth.LoginScreen
import com.argon.hub.ui.theme.ArgonHubTheme
import kotlinx.coroutines.launch

data class ActiveService(val title: String, val url: String)

class MainActivity : ComponentActivity() {
    private val networkManager = NetworkManager()
    private val otaManager = OtaManager()
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(applicationContext)
        enableEdgeToEdge()

        setContent {
            ArgonHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArgonApp(networkManager, otaManager, authManager, getAppVersionCode())
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            networkManager.checkConnectivity()
            val host = networkManager.getActiveHost()
            otaManager.checkForUpdates(host, getAppVersionCode())
            authManager.verifySession(host)
        }
    }

    private fun getAppVersionCode(): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }
}

@Composable
fun ArgonApp(
    networkManager: NetworkManager,
    otaManager: OtaManager,
    authManager: AuthManager,
    currentVersionCode: Long
) {
    val scope = rememberCoroutineScope()
    val authState by authManager.authState.collectAsStateWithLifecycle()
    var activeService by remember { mutableStateOf<ActiveService?>(null) }
    val activeHost = networkManager.getActiveHost()

    when (val state = authState) {
        is AuthState.Initializing -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Unauthenticated -> {
            LoginScreen(
                authManager = authManager,
                activeHost = activeHost,
                modifier = Modifier.fillMaxSize()
            )
        }
        is AuthState.Authenticated, is AuthState.Guest -> {
            val user = if (state is AuthState.Authenticated) state.user else (state as AuthState.Guest).user
            val current = activeService
            if (current != null) {
                ServiceViewerScreen(
                    title = current.title,
                    url = current.url,
                    activeHost = activeHost,
                    onBack = { activeService = null },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                HubScreen(
                    networkManager = networkManager,
                    otaManager = otaManager,
                    currentUser = user,
                    onOpenService = { title, url ->
                        activeService = ActiveService(title, url)
                    },
                    onRefresh = {
                        scope.launch {
                            networkManager.checkConnectivity()
                            otaManager.checkForUpdates(networkManager.getActiveHost(), currentVersionCode)
                            authManager.verifySession(networkManager.getActiveHost())
                        }
                    },
                    onSignOut = {
                        authManager.logout()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
