package anhiutangerinee.kittisu.ui.screen.rezygisk

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.R
import anhiutangerinee.kittisu.ksuApp
import anhiutangerinee.kittisu.rezygisk.MonitorState
import anhiutangerinee.kittisu.rezygisk.ReZygiskState
import anhiutangerinee.kittisu.rezygisk.applyReZygiskSepolicy
import anhiutangerinee.kittisu.rezygisk.deployReZygiskBinaries
import anhiutangerinee.kittisu.rezygisk.getReZygiskState
import anhiutangerinee.kittisu.rezygisk.installReZygiskBootScript
import anhiutangerinee.kittisu.rezygisk.startReZygisk
import anhiutangerinee.kittisu.rezygisk.stopReZygisk
import anhiutangerinee.kittisu.rezygisk.uninstallReZygiskBootScript
import anhiutangerinee.kittisu.ui.component.settings.SegmentedColumn
import anhiutangerinee.kittisu.ui.component.settings.SettingsBaseWidget
import anhiutangerinee.kittisu.ui.component.settings.SettingsSwitchWidget
import anhiutangerinee.kittisu.ui.navigation.LocalNavigator
import anhiutangerinee.kittisu.ui.theme.blurEffect
import anhiutangerinee.kittisu.ui.util.LocalSnackbarHost
import anhiutangerinee.kittisu.ui.util.getRootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReZygiskSettingsScreen() {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current

    var currentEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val prefs = anhiutangerinee.kittisu.ksuApp.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            currentEnabled = prefs.getBoolean("rezygisk_enabled", false)
        }
    }

    var state by remember { mutableStateOf(ReZygiskState()) }

    fun refreshState() {
        scope.launch(Dispatchers.IO) {
            val shell = getRootShell()
            state = getReZygiskState(shell)
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = { navigator.pop() },
                onRefresh = { refreshState() }
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.nestedScroll(TopAppBarDefaults.pinnedScrollBehavior().nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                start = 0.dp,
                end = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + 15.dp
            )
        ) {
            item {
                SegmentedColumn(
                    title = stringResource(R.string.rezygisk_status_section),
                    content = {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.Filled.Memory,
                                title = stringResource(R.string.rezygisk_root_impl),
                                description = state.rootImpl.ifEmpty { "—" }
                            )
                        }
                        item {
                            val monitorLabel = when (state.monitorState) {
                                MonitorState.TRACING -> stringResource(R.string.rezygisk_state_tracing)
                                MonitorState.STOPPING -> stringResource(R.string.rezygisk_state_stopping)
                                MonitorState.STOPPED -> stringResource(R.string.rezygisk_state_stopped)
                                MonitorState.EXITING -> stringResource(R.string.rezygisk_state_exiting)
                                MonitorState.UNKNOWN -> stringResource(R.string.rezygisk_state_unknown)
                            }
                            SettingsBaseWidget(
                                title = stringResource(R.string.rezygisk_monitor_state),
                                description = monitorLabel
                            )
                        }
                        item {
                            val d64Label = when {
                                state.daemon64.running -> stringResource(R.string.rezygisk_state_running)
                                else -> stringResource(R.string.rezygisk_state_not_running)
                            }
                            SettingsBaseWidget(
                                title = stringResource(R.string.rezygisk_daemon_64),
                                description = d64Label
                            )
                        }
                        item {
                            val d32Label = when {
                                state.daemon32.running -> stringResource(R.string.rezygisk_state_running)
                                else -> stringResource(R.string.rezygisk_state_not_running)
                            }
                            SettingsBaseWidget(
                                title = stringResource(R.string.rezygisk_daemon_32),
                                description = d32Label
                            )
                        }
                        item {
                            val z64Label = when {
                                state.zygote64Injected -> stringResource(R.string.rezygisk_state_injected)
                                else -> stringResource(R.string.rezygisk_state_not_injected)
                            }
                            SettingsBaseWidget(
                                title = stringResource(R.string.rezygisk_zygote_64),
                                description = z64Label
                            )
                        }
                        item {
                            val z32Label = when {
                                state.zygote32Injected -> stringResource(R.string.rezygisk_state_injected)
                                else -> stringResource(R.string.rezygisk_state_not_injected)
                            }
                            SettingsBaseWidget(
                                title = stringResource(R.string.rezygisk_zygote_32),
                                description = z32Label
                            )
                        }
                    }
                )
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.rezygisk_modules_section),
                    content = {
                        val allModules = state.daemon64.modules + state.daemon32.modules
                        if (allModules.isEmpty()) {
                            item {
                                SettingsBaseWidget(
                                    title = stringResource(R.string.rezygisk_no_modules)
                                )
                            }
                        } else {
                            allModules.forEach { mod ->
                                item {
                                    SettingsBaseWidget(title = mod)
                                }
                            }
                        }
                    }
                )
            }

            item {
                SegmentedColumn(
                    title = stringResource(R.string.tools),
                    content = {
                        item {
                            SettingsSwitchWidget(
                                title = stringResource(R.string.rezygisk_title),
                                description = when {
                                    currentEnabled -> stringResource(R.string.rezygisk_running)
                                    else -> stringResource(R.string.rezygisk_stopped)
                                },
                                checked = currentEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch(Dispatchers.IO) {
                                        val shell = getRootShell()
                                        val prefs = anhiutangerinee.kittisu.ksuApp.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
                                        if (enabled) {
                                            val ok = deployReZygiskBinaries(shell) &&
                                                applyReZygiskSepolicy(shell) &&
                                                installReZygiskBootScript(shell) &&
                                                startReZygisk(shell)
                                            if (ok) {
                                                prefs.edit { putBoolean("rezygisk_enabled", true) }
                                                withContext(Dispatchers.Main) {
                                                    currentEnabled = true
                                                    snackBarHost.showSnackbar(
                                                        anhiutangerinee.kittisu.ksuApp.getString(R.string.rezygisk_enable_success)
                                                    )
                                                    refreshState()
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    snackBarHost.showSnackbar(
                                                        anhiutangerinee.kittisu.ksuApp.getString(R.string.rezygisk_failed, "enable")
                                                    )
                                                }
                                            }
                                        } else {
                                            stopReZygisk(shell)
                                            uninstallReZygiskBootScript(shell)
                                            prefs.edit { putBoolean("rezygisk_enabled", false) }
                                            withContext(Dispatchers.Main) {
                                                currentEnabled = false
                                                snackBarHost.showSnackbar(
                                                    anhiutangerinee.kittisu.ksuApp.getString(R.string.rezygisk_disable_success)
                                                )
                                                refreshState()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.rezygisk_refresh)
                )
            }
        },
        title = {
            Text(text = stringResource(R.string.rezygisk_settings_title))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)
        )
    )
}
