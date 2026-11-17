package com.artesys.netcon

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private enum class DashboardTab(val label: String, val tag: String) {
    Connection("CONNECT", "tab_connect"),
    Settings("CONFIG", "tab_config"),
    Info("INFO", "tab_info"),
}

// Brushed-steel / burnished-copper Design #2 surface tokens
private val DarkInk = Color(0xFF1F2730)
private val GoldText = Color(0xFFF5A623)
private val GoldAccent = Color(0xFFD9822B)
private val CyanGauge = Color(0xFF38BDF8)
private val EmeraldOnline = Color(0xFF10B981)
private val ServerNavyTop = Color(0xFF131D2D)
private val ServerNavyBottom = Color(0xFF0B111C)
private val SentinelTealTop = Color(0xFF0E212B)
private val SentinelTealBottom = Color(0xFF081219)

@Composable
internal fun NetconDashboard(
    serverName: String,
    protocol: String,
    serverLocation: String,
    serverLatitude: Double?,
    serverLongitude: Double?,
    userLatitude: Double?,
    userLongitude: Double?,
    userLocationSource: String,
    userLocationBusy: Boolean,
    onRequestUserLocation: () -> Unit,
    licenseStatus: NetconLicenseManager.LicenseStatus = NetconLicenseManager.LicenseStatus(
        state = NetconLicenseManager.State.TRIAL_ACTIVE,
        trialStartEpochMs = 0L,
        trialExpiresEpochMs = NetconLicenseManager.TRIAL_DURATION_MS,
        remainingTrialMs = NetconLicenseManager.TRIAL_DURATION_MS,
        activatedKeyMasked = "",
    ),
    onRefreshLicenseStatus: () -> Unit = {},
    onActivateLicenseKey: (String, String) -> NetconLicenseManager.ActivationResult = { _, _ ->
        NetconLicenseManager.ActivationResult(
            success = false,
            message = "License activation unavailable in preview.",
            status = licenseStatus,
        )
    },
    onSimulateTrialExpired: (Boolean) -> Unit = {},
    onResetTrialWindow: () -> Unit = {},
    feedback: String,
    onFeedbackDismiss: () -> Unit,
    onImport: (String) -> Boolean,
    onFetchPublicNodes: suspend (String) -> NativePublicDirectory.Result,
    onRecommend: suspend (String, Double, Double, List<NativePublicDirectory.Node>) -> NativeLocalServerRecommender.Recommendation,
    onSelectPublicNode: (NativePublicDirectory.Node) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBlockedProtocol: () -> Unit,
    onStartConfigured: (String, String) -> Unit,
    batteryOptimizationIgnored: Boolean = false,
    onToggleBatteryOptimization: (Boolean) -> Unit = {},
    onOpenVpnSettings: () -> Unit,
    onExport: () -> Unit,
    onMeasurePing: suspend () -> Int?,
    onSelectPresetProfile: (NativeXrayConfig.ServerProfilePreset) -> Unit = {},
) {
    val vpnStatus by produceState(initialValue = NetconVpnService.getStatus()) {
        while (true) {
            onRefreshLicenseStatus()
            value = NetconVpnService.getStatus()
            delay(1000)
        }
    }
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Connection) }
    var serverDialog by rememberSaveable { mutableStateOf(false) }
    var licenseDialog by rememberSaveable { mutableStateOf(false) }
    var serverDialogCountry by rememberSaveable { mutableStateOf("") }
    var importText by rememberSaveable { mutableStateOf("") }
    var savedActivity by rememberSaveable {
        mutableStateOf(NativeLocalServerRecommender.activities.first())
    }
    val activity = savedActivity.takeIf { it in NativeLocalServerRecommender.activities }
        ?: NativeLocalServerRecommender.activities.first()
    var recommendationMessage by remember { mutableStateOf("") }
    var recommendationBusy by remember { mutableStateOf(false) }
    var dns by rememberSaveable { mutableStateOf("1.1.1.1") }
    var route by rememberSaveable { mutableStateOf("Bypass LAN") }
    var enabledProtocols by rememberSaveable {
        mutableStateOf(setOf("vless", "vmess", "trojan", "shadowsocks", "socks", "freedom"))
    }
    var pingMs by remember { mutableStateOf<Int?>(null) }
    var pingBusy by remember { mutableStateOf(false) }

    var sentinelPaused by rememberSaveable { mutableStateOf(false) }
    val sentinelLogs = remember {
        mutableStateListOf(
            "[CORE] Xray Core V1.8.16 · Copper Compass Engine ready",
            "[NODE] Loaded $serverName (${protocol.ifBlank { "FREEDOM" }})",
        )
    }

    fun appendSentinelLog(entry: String) {
        if (sentinelPaused) return
        sentinelLogs.add(entry)
        if (sentinelLogs.size > 24) {
            sentinelLogs.removeAt(0)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val errorState = vpnStatus.state == "error"
    val duration = "%02d:%02d:%02d".format(
        vpnStatus.elapsedSeconds / 3600,
        vpnStatus.elapsedSeconds / 60 % 60,
        vpnStatus.elapsedSeconds % 60,
    )
    val downloadMbps = vpnStatus.downloadBytesPerSecond * 8 / 1_000_000
    val uploadMbps = vpnStatus.uploadBytesPerSecond * 8 / 1_000_000

    fun refreshPing() {
        if (pingBusy) return
        pingBusy = true
        coroutineScope.launch {
            try {
                val measured = onMeasurePing()
                pingMs = measured
                appendSentinelLog(
                    if (measured != null) "[PING] Handshake latency: ${measured}ms"
                    else "[PING] Direct probe timed out",
                )
            } finally {
                pingBusy = false
            }
        }
    }

    LaunchedEffect(serverName) {
        appendSentinelLog("[NODE] Active node: $serverName")
        pingMs = onMeasurePing()
    }

    LaunchedEffect(vpnStatus.state) {
        when (vpnStatus.state) {
            "connecting" -> appendSentinelLog("[VPN] Initializing Android TUN + Xray core…")
            "connected" -> appendSentinelLog("[VPN] Tunnel active · Routing mode: $route · DNS: $dns")
            "error" -> appendSentinelLog("[ERR] ${vpnStatus.error.ifBlank { "Tunnel stopped with error" }}")
        }
    }

    LaunchedEffect(batteryOptimizationIgnored) {
        appendSentinelLog(
            if (batteryOptimizationIgnored) {
                "[PWR] Battery Optimization Ignore ENABLED · NetconVpnService protected"
            } else {
                "[PWR] Battery Optimization active · Exempt NetconVpnService in CONFIG"
            },
        )
    }

    fun findLocalMatch() {
        if (recommendationBusy) return
        val originLatitude = userLatitude
        val originLongitude = userLongitude
        if (originLatitude == null || originLongitude == null) {
            onRequestUserLocation()
            recommendationMessage = "Requesting device location permission to match nearby servers automatically…"
            appendSentinelLog("[LOCAL] Requesting automatic GPS/Network location…")
            return
        }
        val selectedActivity = activity
        recommendationBusy = true
        recommendationMessage = "Checking public server metadata for nearby regions…"
        appendSentinelLog("[LOCAL] Checking nearby public server regions for $selectedActivity…")
        coroutineScope.launch {
            try {
                var result: NativePublicDirectory.Result? = null
                val failures = mutableListOf<String>()
                for (countryCode in NativeLocalServerRecommender.nearestCountryCodes(
                    originLatitude,
                    originLongitude,
                )) {
                    try {
                        val candidate = withTimeout(20_000) { onFetchPublicNodes(countryCode) }
                        if (candidate.nodes.isNotEmpty()) {
                            result = candidate
                            break
                        }
                        failures += "$countryCode: no supported servers in feed"
                    } catch (error: TimeoutCancellationException) {
                        failures += "$countryCode: feed request timed out"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failures += "$countryCode: ${error.message ?: "feed request failed"}"
                    }
                }
                val available = result
                    ?: throw IllegalStateException(
                        failures.joinToString("; ").ifBlank { "No public servers were found in nearby regions." },
                    )
                recommendationMessage = "Comparing ${minOf(available.nodes.size, 30)} public servers locally…"
                val recommendation = onRecommend(
                    selectedActivity,
                    originLatitude,
                    originLongitude,
                    available.nodes,
                )
                val selected = available.nodes.firstOrNull { it.id == recommendation.nodeId }
                    ?: throw IllegalStateException("Local matcher selected a server outside the downloaded list.")
                onSelectPublicNode(selected)
                recommendationMessage = "${recommendation.reason} Imported locally; tap Main Chassis to connect."
                appendSentinelLog("[LOCAL] Selected ${selected.name} (${selected.country})")
            } catch (error: Exception) {
                recommendationMessage = error.message ?: "Unable to find a local server match."
                appendSentinelLog("[LOCAL] ${recommendationMessage}")
            } finally {
                recommendationBusy = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFD5DCE2), Color(0xFFBDC6CF), Color(0xFFA7B1BC)),
                ),
            ),
    ) {
        // Subtle vertical brushed aluminum ribs
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.18f),
        ) {
            val step = 5.dp.toPx()
            var x = 0f
            var idx = 0
            while (x < size.width) {
                drawLine(
                    color = if (idx % 2 == 0) Color.White else Color(0xFF6B7580),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += step
                idx++
            }
        }

        Image(
            painter = painterResource(R.drawable.brushed_steel),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.06f),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                UnifiedHeaderBar(
                    state = vpnStatus.state,
                    protocol = protocol,
                    licenseStatus = licenseStatus,
                    onOpenLicenseDialog = { licenseDialog = true },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (tab) {
                        DashboardTab.Connection -> ConnectionScreen(
                            importText = importText,
                            onImportTextChange = { importText = it },
                            activity = activity,
                            onActivityChange = { savedActivity = it },
                            recommendationMessage = recommendationMessage,
                            recommendationBusy = recommendationBusy,
                            onFindLocalMatch = ::findLocalMatch,
                            serverName = serverName,
                            protocol = protocol,
                            serverLocation = serverLocation,
                            serverLatitude = serverLatitude,
                            serverLongitude = serverLongitude,
                            state = vpnStatus.state,
                            error = if (errorState) vpnStatus.error else feedback,
                            duration = duration,
                            pingMs = pingMs,
                            pingBusy = pingBusy,
                            onRefreshPing = ::refreshPing,
                            receivedBytes = vpnStatus.receivedBytes,
                            sentBytes = vpnStatus.sentBytes,
                            downloadMbps = downloadMbps,
                            uploadMbps = uploadMbps,
                            sentinelPaused = sentinelPaused,
                            onToggleSentinelPause = { sentinelPaused = !sentinelPaused },
                            sentinelLogs = sentinelLogs,
                            onClearSentinelLogs = { sentinelLogs.clear() },
                            onOpenServers = { countryCode ->
                                if (!licenseStatus.isAccessAllowed) {
                                    licenseDialog = true
                                } else {
                                    serverDialogCountry = countryCode
                                    serverDialog = true
                                }
                            },
                            userLatitude = userLatitude,
                            userLongitude = userLongitude,
                            userLocationSource = userLocationSource,
                            userLocationBusy = userLocationBusy,
                            onRequestUserLocation = onRequestUserLocation,
                            onImport = { raw ->
                                if (onImport(raw)) {
                                    appendSentinelLog("[CFG] Imported local configuration")
                                    importText = ""
                                    serverDialog = false
                                }
                            },
                            onProtocolAllowed = {
                                val normalized = protocol.lowercase(Locale.ROOT)
                                normalized == "freedom" || enabledProtocols.contains(normalized)
                            },
                            onConnect = {
                                if (!licenseStatus.isAccessAllowed) {
                                    onDisconnect()
                                    licenseDialog = true
                                } else {
                                    onConnect()
                                }
                            },
                            onDisconnect = onDisconnect,
                            onBlockedProtocol = onBlockedProtocol,
                            onStartConfigured = {
                                if (!licenseStatus.isAccessAllowed) {
                                    onDisconnect()
                                    licenseDialog = true
                                } else {
                                    onStartConfigured(dns, route)
                                }
                            },
                            onDismissError = onFeedbackDismiss,
                        )

                        DashboardTab.Settings -> SettingsScreen(
                            dns = dns,
                            onDnsChange = { dns = it },
                            route = route,
                            onRouteChange = { route = it },
                            protocols = enabledProtocols,
                            onProtocolsChange = { enabledProtocols = it },
                            batteryOptimizationIgnored = batteryOptimizationIgnored,
                            onToggleBatteryOptimization = onToggleBatteryOptimization,
                            onOpenVpnSettings = onOpenVpnSettings,
                            onExport = onExport,
                        )

                        DashboardTab.Info -> InfoScreen(
                            serverName = serverName,
                            protocol = protocol,
                            state = vpnStatus.state,
                            licenseStatus = licenseStatus,
                            onOpenLicenseDialog = { licenseDialog = true },
                            onSimulateTrialExpired = onSimulateTrialExpired,
                            onResetTrialWindow = onResetTrialWindow,
                            onOpenVpnSettings = onOpenVpnSettings,
                        )
                    }
                }

                // Fixed native Android bottom navigation bar
                NativeBottomNavigation(
                    current = tab,
                    onSelect = { tab = it },
                )
            }
        }

        // Full-screen non-dismissible lock overlay when the 3-day free trial is expired
        if (licenseStatus.isFrozen) {
            FrozenTrialLockOverlay(
                licenseStatus = licenseStatus,
                onActivateLicenseKey = { accountId, raw ->
                    val res = onActivateLicenseKey(accountId, raw)
                    if (res.success) {
                        appendSentinelLog("[LICENSE] Activated ${res.status.activatedKeyMasked} for ${res.status.activatedAccount}")
                    }
                    res
                },
                onResetTrialWindow = onResetTrialWindow,
            )
        }
    }

    if (licenseDialog && !licenseStatus.isFrozen) {
        LicenseActivationDialog(
            licenseStatus = licenseStatus,
            onDismiss = { licenseDialog = false },
            onActivateLicenseKey = { accountId, raw ->
                val res = onActivateLicenseKey(accountId, raw)
                if (res.success) {
                    appendSentinelLog("[LICENSE] Activated ${res.status.activatedKeyMasked} for ${res.status.activatedAccount}")
                    licenseDialog = false
                }
                res
            },
            onSimulateTrialExpired = {
                licenseDialog = false
                onSimulateTrialExpired(true)
            },
            onResetTrialWindow = onResetTrialWindow,
        )
    }

    if (serverDialog && licenseStatus.isAccessAllowed) {
        PublicServerDialog(
            initialCountryCode = serverDialogCountry,
            activeServerName = serverName,
            onDismiss = { serverDialog = false },
            fetchNodes = onFetchPublicNodes,
            onSelectNode = { node ->
                onSelectPublicNode(node)
                appendSentinelLog("[NODE] Switched to ${node.name} (${node.country})")
                serverDialog = false
            },
            onSelectPreset = { preset ->
                onSelectPresetProfile(preset)
                appendSentinelLog("[PROFILE] Activated ${preset.name} (${preset.protocol})")
                serverDialog = false
            },
            onImport = { raw ->
                if (onImport(raw)) {
                    appendSentinelLog("[CFG] Imported local configuration")
                    serverDialog = false
                }
            },
        )
    }
}

@Composable
private fun UnifiedHeaderBar(
    state: String,
    protocol: String,
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onOpenLicenseDialog: () -> Unit,
) {
    val accentDot = when (state) {
        "connected" -> Color(0xFF22FF88)
        "connecting" -> GoldText
        "error" -> Color(0xFFEF4444)
        else -> GoldAccent
    }
    val protoLabel = when (protocol.uppercase(Locale.ROOT)) {
        "", "FREEDOM" -> "VLESS"
        else -> protocol.uppercase(Locale.ROOT)
    }
    val isLicensed = licenseStatus.isLicensed
    val isFrozen = licenseStatus.isFrozen
    val licColor = when {
        isLicensed -> EmeraldOnline
        isFrozen -> Color(0xFFEF4444)
        else -> GoldText
    }
    val licText = when {
        isLicensed -> "LICENSED"
        isFrozen -> "FROZEN"
        else -> licenseStatus.formatRemainingCountdown()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A1018), Color(0xFF131E2E), Color(0xFF0B121C)),
                ),
            )
            .border(1.2.dp, Color(0xFF2E3E54), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.netcon_logo),
                contentDescription = "Netcon logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(20.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF060B12))
                    .border(1.dp, Color(0xFF273649), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(accentDot, CircleShape),
                )
                Text(
                    "$protoLabel · XRAY",
                    color = Color(0xFFCBD5E1),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(licColor.copy(alpha = 0.14f))
                .border(1.dp, licColor.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
                .testTag("trial_license_bar")
                .clickable(onClick = onOpenLicenseDialog)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(licColor, CircleShape),
            )
            Text(
                licText,
                color = if (isLicensed) Color(0xFF4ADE80) else licColor,
                fontFamily = JetBrainsMonoFont,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NativeBottomNavigation(current: DashboardTab, onSelect: (DashboardTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF141C27), Color(0xFF0A1017))),
            )
            .border(1.dp, Color(0xFF2E3E54), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardTab.entries.forEach { item ->
            val active = item == current
            val icon = when (item) {
                DashboardTab.Connection -> Icons.Filled.Bolt
                DashboardTab.Settings -> Icons.Filled.Tune
                DashboardTab.Info -> Icons.Filled.Info
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .testTag(item.tag)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (active) Color(0xFF192738) else Color.Transparent,
                    )
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = if (active) GoldAccent.copy(alpha = 0.65f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.label,
                    tint = if (active) GoldText else Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    item.label,
                    color = if (active) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun ConnectionScreen(
    importText: String,
    onImportTextChange: (String) -> Unit,
    activity: String,
    onActivityChange: (String) -> Unit,
    recommendationMessage: String,
    recommendationBusy: Boolean,
    onFindLocalMatch: () -> Unit,
    serverName: String,
    protocol: String,
    serverLocation: String,
    serverLatitude: Double?,
    serverLongitude: Double?,
    state: String,
    error: String,
    duration: String,
    pingMs: Int?,
    pingBusy: Boolean,
    onRefreshPing: () -> Unit,
    receivedBytes: Long,
    sentBytes: Long,
    downloadMbps: Double,
    uploadMbps: Double,
    sentinelPaused: Boolean,
    onToggleSentinelPause: () -> Unit,
    sentinelLogs: List<String>,
    onClearSentinelLogs: () -> Unit,
    onProtocolAllowed: () -> Boolean,
    onStartConfigured: () -> Unit,
    onOpenServers: (String) -> Unit,
    userLatitude: Double?,
    userLongitude: Double?,
    userLocationSource: String,
    userLocationBusy: Boolean,
    onRequestUserLocation: () -> Unit,
    onImport: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBlockedProtocol: () -> Unit,
    onDismissError: () -> Unit,
) {
    var showToolsDialog by rememberSaveable { mutableStateOf(false) }
    var showSentinelDialog by rememberSaveable { mutableStateOf(false) }
    val countryCode = remember(serverLocation, serverLatitude, serverLongitude) {
        inferCountryCode(serverLocation, serverLatitude, serverLongitude) ?: "SG"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // 1. Compact F-16 / F-22 Raptor Avionics MFD Telemetry Cluster
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF141C27),
                            Color(0xFF0B1119),
                            Color(0xFF070B10),
                        ),
                    ),
                )
                .border(1.2.dp, Color(0xFF334459), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val isPulsingStatus = state == "connected" || state == "connecting"
            val statusLabel = when (state) {
                "connected" -> "ONLINE"
                "connecting" -> "CONNECTING"
                "error" -> "ATTENTION"
                else -> "OFFLINE"
            }
            val statusColor = when (state) {
                "connected" -> Color(0xFF22FF88)
                "connecting" -> GoldText
                "error" -> Color(0xFFEF4444)
                else -> Color(0xFF94A3B8)
            }
            val statusTransition = rememberInfiniteTransition(label = "statusPillPulse")
            val pillPulsePhase by statusTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = if (state == "connecting") 750 else 1250,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "pillPulsePhase",
            )
            val hudSweepAngle by statusTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "hudSweepAngle",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "COCKPIT TELEMETRY",
                    color = Color(0xFF22FF88),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.14f))
                        .border(1.dp, statusColor.copy(alpha = 0.48f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier.size(11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isPulsingStatus) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val maxR = size.minDimension / 2f
                                drawCircle(
                                    color = statusColor.copy(alpha = (1f - pillPulsePhase) * 0.68f),
                                    radius = 2.8.dp.toPx() + (maxR - 2.8.dp.toPx()) * pillPulsePhase,
                                    style = Stroke(width = 1.2.dp.toPx()),
                                )
                            }
                        }
                        val coreScale = if (isPulsingStatus) {
                            0.88f + 0.24f * sin(pillPulsePhase * Math.PI.toFloat())
                        } else {
                            1f
                        }
                        Box(
                            Modifier
                                .size((6f * coreScale).dp)
                                .background(statusColor, CircleShape),
                        )
                    }
                    Text(
                        statusLabel,
                        color = statusColor,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DesignTwoGauge(
                    topLabel = "ms",
                    value = pingMs?.toString() ?: "—",
                    bottomLabel = "PING",
                    progress = ((pingMs ?: 0).coerceIn(0, 250) / 250f),
                    secondaryProgress = if (pingMs != null) 1f - ((pingMs.coerceIn(0, 250)) / 250f) else 0f,
                    accentColor = Color(0xFF22FF88),
                    secondaryColor = GoldText,
                    sweepAngleDeg = if (state == "connected" || state == "connecting" || pingBusy) hudSweepAngle else 0f,
                    modifier = Modifier
                        .widthIn(max = 94.dp)
                        .weight(1f)
                        .testTag("ping_gauge")
                        .clickable(onClick = onRefreshPing),
                )

                MainChassisCenterControl(
                    state = state,
                    duration = duration,
                    sweepAngleDeg = hudSweepAngle,
                    pulsePhase = pillPulsePhase,
                    onClick = {
                        when {
                            state == "connected" || state == "connecting" || state == "error" -> onDisconnect()
                            protocol.isBlank() -> onConnect()
                            onProtocolAllowed() -> onStartConfigured()
                            else -> onBlockedProtocol()
                        }
                    },
                    modifier = Modifier
                        .widthIn(max = 104.dp)
                        .weight(1.1f),
                )

                val txSpeedMbps = if (state == "connected") {
                    uploadMbps.coerceAtLeast(0.1)
                } else {
                    uploadMbps
                }
                val rxSpeedMbps = downloadMbps
                DesignTwoGauge(
                    topLabel = "Mbps",
                    value = "%.1f".format(txSpeedMbps),
                    bottomLabel = "TX",
                    progress = (txSpeedMbps / 25.0).toFloat().coerceIn(0f, 1f),
                    secondaryProgress = (rxSpeedMbps / 50.0).toFloat().coerceIn(0f, 1f),
                    accentColor = CyanGauge,
                    secondaryColor = Color(0xFF22FF88),
                    sweepAngleDeg = if (state == "connected") hudSweepAngle else 0f,
                    modifier = Modifier
                        .widthIn(max = 94.dp)
                        .weight(1f),
                )
            }

            // Compact F-16 DED Odometer strip: RX & TX
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OledTelemetryCard(
                    label = "RX",
                    value = formatBytes(receivedBytes),
                    valueColor = Color(0xFF22FF88),
                    modifier = Modifier.weight(1f),
                )
                OledTelemetryCard(
                    label = "TX",
                    value = formatBytes(sentBytes),
                    valueColor = CyanGauge,
                    modifier = Modifier.weight(1f),
                )
            }

            if (error.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF331418))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(7.dp))
                        .clickable(onClick = onDismissError)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        error,
                        color = Color(0xFFFCA5A5),
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss error",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // 2. Compact Active Server Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.verticalGradient(listOf(ServerNavyTop, ServerNavyBottom)),
                )
                .border(1.dp, Color(0xFF29384D), RoundedCornerShape(11.dp))
                .clickable { onOpenServers("") }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CountryFlagBadge(countryCode = countryCode)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        serverName.ifBlank { "No Server Selected" },
                        color = Color(0xFFF8FAFC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (state == "connected") EmeraldOnline else GoldAccent,
                                CircleShape,
                            ),
                    )
                }
                val protoLabel = when (protocol.uppercase(Locale.ROOT)) {
                    "FREEDOM", "" -> "VLESS-XTLS"
                    else -> protocol.uppercase(Locale.ROOT)
                }
                val pingLabel = pingMs?.let { "${it}ms" } ?: "—"
                Text(
                    "$protoLabel · ${serverLocation.ifBlank { "PORT 443" }} · $pingLabel",
                    color = CyanGauge,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .testTag("switch_server_button")
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF1F2B3D), Color(0xFF131B28))),
                    )
                    .border(1.dp, Color(0xFF3E4E68), RoundedCornerShape(8.dp))
                    .clickable { onOpenServers("") }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "SWITCH",
                    color = Color(0xFFE2E8F0),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Switch server",
                    tint = Color(0xFFE2E8F0),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // 3. Flexible Interactive Global Routing Map (stretches cleanly to fill remaining height)
        ProtonWorldMapPanel(
            serverName = serverName,
            locationName = serverLocation,
            latitude = serverLatitude,
            longitude = serverLongitude,
            userLatitude = userLatitude,
            userLongitude = userLongitude,
            userLocationSource = userLocationSource,
            userLocationBusy = userLocationBusy,
            vpnState = state,
            onBrowseCountryServers = { code -> onOpenServers(code) },
            onRequestUserLocation = onRequestUserLocation,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 128.dp),
            compact = true,
        )

        // 4. Compact Docked Sentinel Ticker + Quick Modal Tools Bar
        val latestLog = sentinelLogs.lastOrNull() ?: "[SENTINEL] Xray Core ready · Tap LOGS to inspect"
        val logColor = when {
            latestLog.startsWith("[ERR]") -> Color(0xFFFCA5A5)
            latestLog.startsWith("[VPN]") -> Color(0xFF4ADE80)
            latestLog.startsWith("[LOCAL]") -> Color(0xFFFCD34D)
            latestLog.startsWith("[PING]") -> CyanGauge
            else -> Color(0xFFCBD5E1)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(listOf(SentinelTealTop, SentinelTealBottom)),
                )
                .border(1.dp, Color(0xFF284357), RoundedCornerShape(10.dp))
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showSentinelDialog = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = "Sentinel stream",
                    tint = Color(0xFF7DD3FC),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    latestLog,
                    color = logColor,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            SentinelControlChip(
                label = "LOGS",
                testTag = "sentinel_open_button",
                onClick = { showSentinelDialog = true },
            )

            Row(
                modifier = Modifier
                    .testTag("ai_panel_toggle")
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFF6D08A), Color(0xFFD9822B), Color(0xFF9C5114)),
                        ),
                    )
                    .clickable { showToolsDialog = true }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Import & Match",
                    tint = Color(0xFF1D0E05),
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    "TOOLS",
                    color = Color(0xFF1D0E05),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (showSentinelDialog) {
        Dialog(onDismissRequest = { showSentinelDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NetconColors.PanelSurface)
                    .border(1.dp, Color(0xFF284357), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "AI LIVE SENTINEL STREAM",
                            color = Color(0xFF7DD3FC),
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (sentinelPaused) "STREAM PAUSED · LOCAL TELEMETRY"
                            else "XRAY CORE + LOCAL TELEMETRY",
                            color = Color(0xFF94A3B8),
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 8.5.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SentinelControlChip(
                            label = if (sentinelPaused) "RESUME" else "PAUSE",
                            testTag = "sentinel_pause_button",
                            onClick = onToggleSentinelPause,
                        )
                        SentinelControlChip(
                            label = "CLEAR",
                            testTag = "sentinel_clear_button",
                            onClick = onClearSentinelLogs,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 300.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF04090E))
                        .border(1.dp, Color(0xFF1D3342), RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (sentinelLogs.isEmpty()) {
                        Text(
                            "[SENTINEL] Stream cleared. Awaiting tunnel or config activity…",
                            color = Color(0xFF64748B),
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 9.5.sp,
                        )
                    } else {
                        sentinelLogs.forEach { line ->
                            val lineColor = when {
                                line.startsWith("[ERR]") -> Color(0xFFFCA5A5)
                                line.startsWith("[VPN]") -> Color(0xFF4ADE80)
                                line.startsWith("[LOCAL]") -> Color(0xFFFCD34D)
                                line.startsWith("[PING]") -> CyanGauge
                                else -> Color(0xFFCBD5E1)
                            }
                            Text(
                                line,
                                color = lineColor,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.5.sp,
                            )
                        }
                    }
                }

                SecondaryActionButton(
                    label = "CLOSE",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showSentinelDialog = false },
                )
            }
        }
    }

    if (showToolsDialog) {
        Dialog(onDismissRequest = { showToolsDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NetconColors.PanelSurface)
                    .border(1.dp, GoldText.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "IMPORT CONFIG & LOCAL MATCH",
                        color = NetconColors.CopperLight,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { showToolsDialog = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                    }
                }

                RecessedInput(
                    value = importText,
                    onValueChange = onImportTextChange,
                    placeholder = "Paste vless://, vmess://, trojan://, ss:// or Xray JSON",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CopperButton(
                        label = "IMPORT LINK",
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_link_button"),
                    ) {
                        onImport(importText.trim())
                        showToolsDialog = false
                    }
                    SecondaryActionButton(
                        label = "PUBLIC SERVERS",
                        icon = Icons.Filled.Public,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_public_servers_button"),
                    ) {
                        showToolsDialog = false
                        onOpenServers("")
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1F3647)))

                Text(
                    "ACTIVITY PROFILE · LOCAL DISTANCE MATCH",
                    color = Color(0xFF9FB2C4),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NativeLocalServerRecommender.activities.forEach { label ->
                        Keycap(
                            label = label,
                            modifier = Modifier.weight(1f),
                            selected = activity == label,
                        ) { onActivityChange(label) }
                    }
                }
                Text(
                    "Matches nearest server using local region distance only; zero external AI telemetry.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                )
                CopperButton(
                    label = if (recommendationBusy) "MATCHING LOCALLY…" else "LOCAL MATCH · FIND PUBLIC SERVER",
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_server_match_button"),
                    onClick = onFindLocalMatch,
                )
                if (recommendationMessage.isNotBlank()) {
                    Text(
                        recommendationMessage,
                        color = if (recommendationBusy) GoldText else Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignTwoGauge(
    topLabel: String,
    value: String,
    subValue: String = "",
    bottomLabel: String,
    progress: Float,
    secondaryProgress: Float = 0f,
    accentColor: Color,
    secondaryColor: Color = GoldText,
    sweepAngleDeg: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 220f, dampingRatio = 0.85f),
        label = "gaugeNeedle",
    )
    val animatedSecondary by animateFloatAsState(
        targetValue = secondaryProgress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.88f),
        label = "gaugeSecondary",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(2.dp),
            ) {
                val outerRadius = size.minDimension / 2f
                // F-16 MFD octagonal/circular anodized bezel
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFF283546), Color(0xFF151E29), Color(0xFF090E14)),
                    ),
                    radius = outerRadius,
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.35f),
                    radius = outerRadius * 0.96f,
                    style = Stroke(width = 1.dp.toPx()),
                )

                val dialRadius = outerRadius * 0.88f
                drawCircle(Color(0xFF040A0E), dialRadius)

                // Subtle F-16 radar crosshairs & concentric range rings
                drawCircle(
                    color = accentColor.copy(alpha = 0.10f),
                    radius = dialRadius * 0.62f,
                    style = Stroke(
                        width = 0.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                    ),
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.12f),
                    start = Offset(center.x - dialRadius * 0.72f, center.y),
                    end = Offset(center.x + dialRadius * 0.72f, center.y),
                    strokeWidth = 0.7.dp.toPx(),
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.12f),
                    start = Offset(center.x, center.y - dialRadius * 0.72f),
                    end = Offset(center.x, center.y + dialRadius * 0.72f),
                    strokeWidth = 0.7.dp.toPx(),
                )

                // Corner HUD bracket ticks inside the MFD frame
                val bracketOffset = dialRadius * 0.64f
                val bracketArm = dialRadius * 0.16f
                val bracketColor = accentColor.copy(alpha = 0.42f)
                val bStroke = 1.1.dp.toPx()
                // Top-left bracket
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y - bracketOffset), Offset(center.x - bracketOffset + bracketArm, center.y - bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y - bracketOffset), Offset(center.x - bracketOffset, center.y - bracketOffset + bracketArm), bStroke)
                // Top-right bracket
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y - bracketOffset), Offset(center.x + bracketOffset - bracketArm, center.y - bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y - bracketOffset), Offset(center.x + bracketOffset, center.y - bracketOffset + bracketArm), bStroke)
                // Bottom-left bracket
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y + bracketOffset), Offset(center.x - bracketOffset + bracketArm, center.y + bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x - bracketOffset, center.y + bracketOffset), Offset(center.x - bracketOffset, center.y + bracketOffset - bracketArm), bStroke)
                // Bottom-right bracket
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y + bracketOffset), Offset(center.x + bracketOffset - bracketArm, center.y + bracketOffset), bStroke)
                drawLine(bracketColor, Offset(center.x + bracketOffset, center.y + bracketOffset), Offset(center.x + bracketOffset, center.y + bracketOffset - bracketArm), bStroke)

                // Outer primary telemetry arc
                val arcSize = Size(dialRadius * 1.66f, dialRadius * 1.66f)
                val arcTopLeft = Offset(center.x - dialRadius * 0.83f, center.y - dialRadius * 0.83f)
                drawArc(
                    color = accentColor.copy(alpha = 0.18f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = arcTopLeft,
                    size = arcSize,
                )
                if (animatedProgress > 0.01f) {
                    drawArc(
                        color = accentColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = arcTopLeft,
                        size = arcSize,
                    )
                }

                // Inner secondary telemetry arc (e.g. signal quality or DL rate)
                val innerArcSize = Size(dialRadius * 1.38f, dialRadius * 1.38f)
                val innerArcTopLeft = Offset(center.x - dialRadius * 0.69f, center.y - dialRadius * 0.69f)
                drawArc(
                    color = secondaryColor.copy(alpha = 0.14f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 1.4.dp.toPx()),
                    topLeft = innerArcTopLeft,
                    size = innerArcSize,
                )
                if (animatedSecondary > 0.01f) {
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.85f),
                        startAngle = 135f,
                        sweepAngle = 270f * animatedSecondary,
                        useCenter = false,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = innerArcTopLeft,
                        size = innerArcSize,
                    )
                }

                // 24 F-16 azimuth/nozzle precision ticks
                for (tick in 0..24) {
                    val angle = Math.toRadians(135.0 + tick * (270.0 / 24.0))
                    val isMajor = tick % 4 == 0
                    val lit = (tick / 24f) <= animatedProgress && animatedProgress > 0.01f
                    val outer = dialRadius * 0.93f
                    val inner = dialRadius * if (isMajor) 0.78f else 0.85f
                    drawLine(
                        color = when {
                            lit -> accentColor
                            isMajor -> accentColor.copy(alpha = 0.55f)
                            else -> Color(0xFF334155)
                        },
                        start = Offset(
                            center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner,
                        ),
                        end = Offset(
                            center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer,
                        ),
                        strokeWidth = if (isMajor) 1.4.dp.toPx() else 0.8.dp.toPx(),
                    )
                }

                // Rotating radar sweep line when active
                if (sweepAngleDeg > 0f) {
                    val sweepRad = Math.toRadians(sweepAngleDeg.toDouble())
                    drawLine(
                        color = accentColor.copy(alpha = 0.25f),
                        start = center,
                        end = Offset(
                            center.x + cos(sweepRad).toFloat() * dialRadius * 0.75f,
                            center.y + sin(sweepRad).toFloat() * dialRadius * 0.75f,
                        ),
                        strokeWidth = 1.1.dp.toPx(),
                    )
                }

                // Tactical HUD needle + target-lock diamond at tip
                val needleDeg = 135.0 + 270.0 * animatedProgress
                val rad = Math.toRadians(needleDeg)
                val tip = Offset(
                    center.x + cos(rad).toFloat() * dialRadius * 0.68f,
                    center.y + sin(rad).toFloat() * dialRadius * 0.68f,
                )
                drawLine(
                    color = accentColor.copy(alpha = 0.85f),
                    start = center,
                    end = tip,
                    strokeWidth = 1.8.dp.toPx(),
                )
                val dSize = 3.2.dp.toPx()
                val diamond = Path().apply {
                    moveTo(tip.x, tip.y - dSize)
                    lineTo(tip.x + dSize, tip.y)
                    lineTo(tip.x, tip.y + dSize)
                    lineTo(tip.x - dSize, tip.y)
                    close()
                }
                drawPath(diamond, color = accentColor)
                drawCircle(Color(0xFF040A0E), radius = 4.dp.toPx(), center = center)
                drawCircle(accentColor, radius = 2.4.dp.toPx(), center = center)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 13.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    topLabel,
                    color = accentColor.copy(alpha = 0.85f),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xD903080C))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        value,
                        color = accentColor,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            bottomLabel,
            color = Color(0xFFE2E8F0),
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun MainChassisCenterControl(
    state: String,
    duration: String,
    sweepAngleDeg: Float = 0f,
    pulsePhase: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = state == "connected"
    val connecting = state == "connecting"
    val isError = state == "error"

    val statusColor = when {
        connected -> Color(0xFF22FF88)
        connecting -> GoldText
        isError -> Color(0xFFEF4444)
        else -> Color(0xFF38BDF8)
    }
    val statusWord = when {
        connected -> "ONLINE"
        connecting -> "WAIT"
        isError -> "ERROR"
        else -> "OFFLINE"
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag("vpn_power_button")
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF2E3D52), Color(0xFF182230), Color(0xFF0A1017)),
                    ),
                )
                .border(1.5.dp, statusColor.copy(alpha = 0.65f), CircleShape)
                .clickable(
                    onClickLabel = if (connected || connecting || isError) "Disconnect VPN" else "Connect VPN",
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = "VPN connection"
                    stateDescription = statusWord
                }
                .padding(6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (connected) Color(0xFF07291E) else if (connecting) Color(0xFF261C0A) else Color(0xFF08131C),
                                Color(0xFF03070B),
                            ),
                        ),
                    )
                    .border(
                        width = 1.8.dp,
                        color = statusColor.copy(alpha = if (connected || connecting) 0.9f else 0.45f),
                        shape = CircleShape,
                    ),
            ) {
                // F-16 HUD Pitch Ladder, Horizon Line, & Rotating Tactical Lock Ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    // Pulsing radar wave when connected or connecting
                    if (connected || connecting) {
                        drawCircle(
                            color = statusColor.copy(alpha = (1f - pulsePhase) * 0.42f),
                            radius = r * (0.48f + 0.44f * pulsePhase),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }

                    // Artificial horizon line & pitch ladder rungs
                    val ladderColor = statusColor.copy(alpha = 0.28f)
                    val lStroke = 1.dp.toPx()
                    // Horizon wings
                    drawLine(ladderColor, Offset(center.x - r * 0.72f, center.y), Offset(center.x - r * 0.36f, center.y), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.36f, center.y), Offset(center.x + r * 0.72f, center.y), lStroke)
                    // Upper pitch rung (+10 deg)
                    val upperY = center.y - r * 0.34f
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, upperY), Offset(center.x - r * 0.22f, upperY), lStroke)
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, upperY), Offset(center.x - r * 0.46f, upperY + r * 0.07f), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.22f, upperY), Offset(center.x + r * 0.46f, upperY), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.46f, upperY), Offset(center.x + r * 0.46f, upperY + r * 0.07f), lStroke)
                    // Lower pitch rung (-10 deg)
                    val lowerY = center.y + r * 0.34f
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, lowerY), Offset(center.x - r * 0.22f, lowerY), lStroke)
                    drawLine(ladderColor, Offset(center.x - r * 0.46f, lowerY), Offset(center.x - r * 0.46f, lowerY - r * 0.07f), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.22f, lowerY), Offset(center.x + r * 0.46f, lowerY), lStroke)
                    drawLine(ladderColor, Offset(center.x + r * 0.46f, lowerY), Offset(center.x + r * 0.46f, lowerY - r * 0.07f), lStroke)

                    // Segmented rotating tactical lock ring
                    val ringRadius = r * 0.84f
                    val arcBox = Size(ringRadius * 2f, ringRadius * 2f)
                    val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
                    val baseAngle = if (connected || connecting) sweepAngleDeg else 0f
                    for (seg in 0 until 4) {
                        drawArc(
                            color = statusColor.copy(alpha = if (connected || connecting) 0.78f else 0.35f),
                            startAngle = baseAngle + seg * 90f + 18f,
                            sweepAngle = 54f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            topLeft = arcTopLeft,
                            size = arcBox,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        statusWord,
                        color = statusColor,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                    if (connected) {
                        Text(
                            duration,
                            color = Color(0xFFE2E8F0),
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "STATUS",
            color = Color(0xFFE2E8F0),
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun OledTelemetryCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF04090E))
            .border(1.dp, valueColor.copy(alpha = 0.42f), RoundedCornerShape(7.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = Color(0xFF94A3B8),
            fontFamily = JetBrainsMonoFont,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Text(
            value,
            color = valueColor,
            fontFamily = JetBrainsMonoFont,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CountryFlagBadge(countryCode: String) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF172233))
            .border(1.dp, Color(0xFF394B66), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (countryCode == "SG") {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val halfH = size.height / 2f
                drawRect(Color(0xFFEF3340), topLeft = Offset.Zero, size = Size(size.width, halfH))
                drawRect(Color.White, topLeft = Offset(0f, halfH), size = Size(size.width, halfH))
                drawCircle(
                    color = Color.White,
                    radius = halfH * 0.56f,
                    center = Offset(size.width * 0.28f, halfH * 0.5f),
                )
                drawCircle(
                    color = Color(0xFFEF3340),
                    radius = halfH * 0.45f,
                    center = Offset(size.width * 0.34f, halfH * 0.5f),
                )
            }
        } else {
            Text(
                countryCode,
                color = NetconColors.CopperLight,
                fontFamily = JetBrainsMonoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SentinelControlChip(
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF132634))
            .border(1.dp, Color(0xFF3A5B73), RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = Color(0xFFE2E8F0),
            fontFamily = JetBrainsMonoFont,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsScreen(
    dns: String,
    onDnsChange: (String) -> Unit,
    route: String,
    onRouteChange: (String) -> Unit,
    protocols: Set<String>,
    onProtocolsChange: (Set<String>) -> Unit,
    batteryOptimizationIgnored: Boolean,
    onToggleBatteryOptimization: (Boolean) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("TUNNEL & PROTOCOL MATRIX")
        MetalPanel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "DNS RESOLVER",
                        color = NetconColors.TextSecondary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        dns,
                        color = CyanGauge,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1.1.1.1", "8.8.8.8", "94.140.14.14", "9.9.9.9").forEach { addr ->
                        Keycap(
                            label = addr,
                            modifier = Modifier.weight(1f),
                            selected = dns == addr,
                        ) { onDnsChange(addr) }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

                Text(
                    "ALLOWED PROTOCOLS",
                    color = NetconColors.TextSecondary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "VLESS" to "vless",
                        "VMESS" to "vmess",
                        "TROJAN" to "trojan",
                        "SS" to "shadowsocks",
                    ).forEach { (name, id) ->
                        val checked = id in protocols
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (checked) GoldAccent.copy(alpha = 0.2f) else Color(0xFF0D1722),
                                )
                                .border(
                                    1.dp,
                                    if (checked) GoldText else Color(0xFF2C3E52),
                                    RoundedCornerShape(7.dp),
                                )
                                .clickable {
                                    onProtocolsChange(if (checked) protocols - id else protocols + id)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(
                                        if (checked) EmeraldOnline else Color(0xFF475569),
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                name,
                                color = if (checked) NetconColors.CopperLight else NetconColors.TextMuted,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

                Text(
                    "ROUTING MODE",
                    color = NetconColors.TextSecondary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Global", "Bypass LAN", "Rule-based").forEach { value ->
                        Keycap(
                            label = value,
                            modifier = Modifier.weight(1f),
                            selected = route == value,
                        ) { onRouteChange(value) }
                    }
                }
            }
        }

        SectionTitle("BACKGROUND TUNNEL SURVIVABILITY")
        MetalPanel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "VPNSERVICE POWER EXEMPTION",
                        color = NetconColors.TextSecondary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val badgeColor = if (batteryOptimizationIgnored) EmeraldOnline else GoldText
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.14f))
                            .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (batteryOptimizationIgnored) "UNRESTRICTED" else "OPTIMIZED",
                            color = badgeColor,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Battery Optimization Ignore",
                            color = NetconColors.TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (batteryOptimizationIgnored) {
                                "NetconVpnService (:vpn) protected from Doze"
                            } else {
                                "Prevent Android from killing background tunnel"
                            },
                            color = NetconColors.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = batteryOptimizationIgnored,
                        onCheckedChange = onToggleBatteryOptimization,
                        modifier = Modifier.testTag("battery_optimization_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1D0E05),
                            checkedTrackColor = EmeraldOnline,
                            uncheckedThumbColor = NetconColors.TextSecondary,
                            uncheckedTrackColor = NetconColors.ChassisDark,
                            uncheckedBorderColor = NetconColors.BevelBorder,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CompactMetricTile(
                        label = "TARGET SERVICE",
                        value = "NetconVpnService (:vpn)",
                        modifier = Modifier.weight(1f),
                    )
                    CompactMetricTile(
                        label = "DOZE GUARD",
                        value = if (batteryOptimizationIgnored) "IGNORE ACTIVE" else "SYSTEM MANAGED",
                        valueColor = if (batteryOptimizationIgnored) EmeraldOnline else GoldText,
                        modifier = Modifier.weight(1f),
                    )
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Android VPN Lockdown",
                            color = NetconColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Always-on VPN & block non-VPN traffic",
                            color = NetconColors.TextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                    SecondaryActionButton(label = "OPEN", onClick = onOpenVpnSettings)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CopperButton(
                label = "EXPORT JSON",
                icon = Icons.Filled.ContentCopy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("export_json_button"),
                onClick = onExport,
            )
            SecondaryActionButton(
                label = "VPN SETTINGS",
                modifier = Modifier.weight(1f),
                onClick = onOpenVpnSettings,
            )
        }
    }
}

@Composable
private fun LicenseActivationDialog(
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onDismiss: () -> Unit,
    onActivateLicenseKey: (String, String) -> NetconLicenseManager.ActivationResult,
    onSimulateTrialExpired: () -> Unit,
    onResetTrialWindow: () -> Unit,
) {
    var accountInput by rememberSaveable {
        mutableStateOf(
            licenseStatus.activatedAccount.ifBlank { NetconLicenseManager.DEFAULT_DEMO_ACCOUNT },
        )
    }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf("") }
    var feedbackError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, GoldText.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "1-KEY-PER-ACCOUNT LICENSE",
                    color = NetconColors.TextPrimary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            Text(
                text = if (licenseStatus.isLicensed) {
                    "Bound to ${licenseStatus.activatedAccount} (${licenseStatus.activatedKeyMasked}). Each Netcon key works exclusively for its assigned account."
                } else {
                    "3-day (72-hour) free trial (${licenseStatus.formatRemainingCountdown()} remaining). Each activation key is cryptographically bound to one account email and refuses activation on any other account."
                },
                color = NetconColors.TextSecondary,
                fontSize = 11.sp,
            )

            RecessedInput(
                value = accountInput,
                onValueChange = { accountInput = it },
                placeholder = "Account email (e.g. arxdevmm@gmail.com)",
                modifier = Modifier.testTag("dialog_account_input"),
            )

            RecessedInput(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = "NETCON-XXXX-XXXX-XXXX-XXXX",
                modifier = Modifier.testTag("dialog_license_input"),
            )

            if (feedbackMsg.isNotBlank()) {
                Text(
                    text = feedbackMsg,
                    color = if (feedbackError) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    label = "ISSUE FOR EMAIL",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val targetAccount = accountInput.trim().ifBlank { NetconLicenseManager.DEFAULT_DEMO_ACCOUNT }
                        accountInput = targetAccount
                        keyInput = NetconLicenseManager.generateLicenseKeyForAccount(targetAccount, "FULL", "2026")
                        feedbackError = false
                        feedbackMsg = "Generated 1-account key locked to $targetAccount. Tap ACTIVATE KEY."
                    },
                )
                CopperButton(
                    label = "ACTIVATE KEY",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dialog_activate_key_button"),
                    onClick = {
                        val res = onActivateLicenseKey(accountInput, keyInput)
                        feedbackError = !res.success
                        feedbackMsg = res.message
                    },
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton(
                    label = "TEST FREEZE",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_freeze_button"),
                    onClick = onSimulateTrialExpired,
                )
                SecondaryActionButton(
                    label = "RESET 3D TRIAL",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onResetTrialWindow()
                        feedbackError = false
                        feedbackMsg = "3-day (72h) trial timer reset."
                    },
                )
            }
        }
    }
}

@Composable
private fun FrozenTrialLockOverlay(
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onActivateLicenseKey: (String, String) -> NetconLicenseManager.ActivationResult,
    onResetTrialWindow: () -> Unit,
) {
    var accountInput by rememberSaveable {
        mutableStateOf(NetconLicenseManager.DEFAULT_DEMO_ACCOUNT)
    }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember {
        mutableStateOf("3-day free trial expired. Enter your registered Account Email and your 1-account Netcon License Key to unlock.")
    }
    var isError by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF205080E))
            .clickable(enabled = true, onClick = {})
            .testTag("frozen_trial_lock_overlay")
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A0F14), Color(0xFF0D131D), Color(0xFF070B11)),
                    ),
                )
                .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2B0D12))
                    .border(1.5.dp, Color(0xFFEF4444), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "APK Frozen",
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(25.dp),
                )
            }

            Text(
                text = "APK FROZEN · 3-DAY TRIAL EXPIRED",
                color = Color(0xFFF87171),
                fontFamily = JetBrainsMonoFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "1 KEY = 1 ACCOUNT · ALL SERVERS REFUSED",
                color = GoldText,
                fontFamily = JetBrainsMonoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = statusMessage,
                color = if (isError) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            RecessedInput(
                value = accountInput,
                onValueChange = { accountInput = it },
                placeholder = "Account email (e.g. arxdevmm@gmail.com)",
                modifier = Modifier.testTag("frozen_account_input"),
            )

            RecessedInput(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = "Enter license key: NETCON-XXXX-XXXX-XXXX-XXXX",
                modifier = Modifier.testTag("frozen_license_input"),
            )

            CopperButton(
                label = "ACTIVATE ACCOUNT-BOUND KEY",
                icon = Icons.Filled.Lock,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("frozen_activate_button"),
                onClick = {
                    val result = onActivateLicenseKey(accountInput, keyInput)
                    isError = !result.success
                    statusMessage = result.message
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryActionButton(
                    label = "FILL ACCOUNT KEY",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("frozen_fill_demo_key_button"),
                    onClick = {
                        val targetAccount = accountInput.trim().ifBlank { NetconLicenseManager.DEFAULT_DEMO_ACCOUNT }
                        accountInput = targetAccount
                        keyInput = NetconLicenseManager.generateLicenseKeyForAccount(targetAccount, "FULL", "2026")
                        isError = false
                        statusMessage = "Loaded key ${keyInput} bound only to $targetAccount."
                    },
                )
                SecondaryActionButton(
                    label = "RESET 3D TRIAL",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("frozen_reset_trial_button"),
                    onClick = onResetTrialWindow,
                )
            }

            Text(
                text = "Each key is HMAC-signed for one account email (${licenseStatus.badgeSummary()})",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 8.5.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InfoScreen(
    serverName: String,
    protocol: String,
    state: String,
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onOpenLicenseDialog: () -> Unit,
    onSimulateTrialExpired: (Boolean) -> Unit,
    onResetTrialWindow: () -> Unit,
    onOpenVpnSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("LICENSE & 1-KEY-PER-ACCOUNT")
        MetalPanel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMetricTile(
                        label = "STATE",
                        value = licenseStatus.state.name,
                        valueColor = if (licenseStatus.isLicensed) EmeraldOnline else GoldText,
                        modifier = Modifier.weight(1f),
                    )
                    CompactMetricTile(
                        label = "COUNTDOWN",
                        value = licenseStatus.formatRemainingCountdown(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMetricTile(
                        label = "SERVER ACCESS",
                        value = if (licenseStatus.isAccessAllowed) "ALLOWED" else "FROZEN · REFUSED",
                        valueColor = if (licenseStatus.isAccessAllowed) Color(0xFF22FF88) else Color(0xFFF87171),
                        modifier = Modifier.weight(1f),
                    )
                    CompactMetricTile(
                        label = if (licenseStatus.isLicensed) "BOUND ACCOUNT" else "BINDING RULE",
                        value = if (licenseStatus.isLicensed) licenseStatus.activatedAccount else "1 Key = 1 Account",
                        valueColor = CyanGauge,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "CLI: bash scripts/manage-keystore.sh generate-license-key <email>",
                    color = NetconColors.TextSecondary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CopperButton(
                        label = "ACTIVATE KEY",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenLicenseDialog,
                    )
                    SecondaryActionButton(
                        label = "TEST FREEZE",
                        modifier = Modifier.weight(1f),
                        onClick = { onSimulateTrialExpired(true) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SecondaryActionButton(
                        label = "RESET 3D TRIAL",
                        modifier = Modifier.weight(1f),
                        onClick = onResetTrialWindow,
                    )
                    SecondaryActionButton(
                        label = "SYSTEM VPN",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenVpnSettings,
                    )
                }
            }
        }

        SectionTitle("CORE TELEMETRY & RELEASE BUILD")
        MetalPanel(compact = true) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMetricTile(
                        label = "CORE ENGINE",
                        value = "Xray V1.8.16",
                        modifier = Modifier.weight(1f),
                    )
                    CompactMetricTile(
                        label = "APP VERSION",
                        value = BuildConfig.VERSION_NAME,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMetricTile(
                        label = "TUNNEL STATUS",
                        value = "${state.uppercase(Locale.ROOT)} · ${protocol.ifBlank { "FREEDOM" }}",
                        modifier = Modifier.weight(1f),
                    )
                    CompactMetricTile(
                        label = "ACTIVE NODE",
                        value = serverName.ifBlank { "—" },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactMetricTile(
                        label = "RELEASE CI",
                        value = "release-apk.yml",
                        modifier = Modifier.weight(1f),
                    )
                    CompactMetricTile(
                        label = "SIGNING KEY",
                        value = "upload (.jks)",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        MetalPanel(compact = true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = GoldText,
                    modifier = Modifier.size(18.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "ZERO-LOG & LOCAL-ONLY STORAGE",
                        color = NetconColors.CopperLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Configs & license bindings remain in device-private storage with Android VpnService routing and zero ads or telemetry SDKs.",
                        color = NetconColors.TextSecondary,
                        fontSize = 9.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicServerDialog(
    initialCountryCode: String,
    activeServerName: String = "",
    onDismiss: () -> Unit,
    fetchNodes: suspend (String) -> NativePublicDirectory.Result,
    onSelectNode: (NativePublicDirectory.Node) -> Unit,
    onSelectPreset: (NativeXrayConfig.ServerProfilePreset) -> Unit = {},
    onImport: (String) -> Unit,
) {
    var raw by rememberSaveable { mutableStateOf("") }
    var countryCode by rememberSaveable(initialCountryCode) { mutableStateOf(initialCountryCode) }
    var search by rememberSaveable { mutableStateOf("") }
    var countryMenu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf(emptyList<NativePublicDirectory.Node>()) }
    val presets = remember { NativeXrayConfig.presetProfiles() }
    val scope = rememberCoroutineScope()

    fun refresh() {
        if (loading) return
        loading = true
        error = ""
        scope.launch {
            try {
                val result = withTimeout(25_000) { fetchNodes(countryCode) }
                nodes = result.nodes
                error = "${result.nodes.size} supported · ${result.skipped} skipped"
            } catch (cause: Exception) {
                error = cause.message ?: "Public directory unavailable. Try another feed."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(countryCode) { refresh() }

    val visiblePresets = presets.filter {
        (countryCode.isEmpty() || it.countryCode == countryCode) &&
            (search.isBlank() || "${it.name} ${it.countryName} ${it.protocol} ${it.transportSummary}".contains(search, ignoreCase = true))
    }

    val visibleNodes = nodes.filter {
        search.isBlank() || "${it.name} ${it.country} ${it.protocol}".contains(search, ignoreCase = true)
    }.take(40)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, NetconColors.BevelHighlight, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SERVER PROFILES & DIRECTORY",
                    color = NetconColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            if (visiblePresets.isNotEmpty()) {
                Text(
                    "STABILIZED MULTI-PROTOCOL PROFILES",
                    color = NetconColors.CopperLight,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                visiblePresets.forEach { preset ->
                    val isSelected = preset.name == activeServerName
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF122638) else NetconColors.ChassisDark)
                            .border(
                                1.dp,
                                if (isSelected) NetconColors.CyberCyan else NetconColors.BevelBorder,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${preset.countryCode} · ${preset.name}",
                                color = NetconColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${preset.transportSummary} · ${preset.cityLabel}",
                                color = NetconColors.CyberCyan,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 9.sp,
                            )
                        }
                        CopperButton(
                            label = if (isSelected) "ACTIVE" else "USE",
                            onClick = { onSelectPreset(preset) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(NetconColors.BevelBorder))
            }

            Text(
                "FREE PUBLIC SERVERS · FASTNODES COMMUNITY FEED",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )

            Box {
                Keycap(
                    label = if (countryCode.isEmpty()) "TOP FEED · ALL REGIONS"
                    else "${Locale("", countryCode).displayCountry.uppercase(Locale.ROOT)} ($countryCode)",
                    modifier = Modifier.fillMaxWidth(),
                    selected = countryCode.isNotEmpty(),
                    onClick = { countryMenu = true },
                )
                DropdownMenu(expanded = countryMenu, onDismissRequest = { countryMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Top feed · All regions") },
                        onClick = {
                            countryCode = ""
                            countryMenu = false
                        },
                    )
                    NativePublicDirectory.countryCodes.forEach { code ->
                        DropdownMenuItem(
                            text = { Text("${Locale("", code).displayCountry} ($code)") },
                            onClick = {
                                countryCode = code
                                countryMenu = false
                            },
                        )
                    }
                }
            }

            RecessedInput(search, { search = it }, "Search protocol or location")

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (loading) "Loading public feed…" else error,
                    color = if (loading) NetconColors.CopperLight else NetconColors.TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SecondaryActionButton(
                    label = "REFRESH",
                    icon = Icons.Filled.Refresh,
                    onClick = ::refresh,
                )
            }

            visibleNodes.forEach { node ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetconColors.ChassisDark)
                        .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.name,
                            color = NetconColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${node.protocol.uppercase(Locale.ROOT)} · ${node.country}",
                            color = NetconColors.TextSecondary,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 9.sp,
                        )
                    }
                    CopperButton("IMPORT", onClick = { onSelectNode(node) })
                }
            }

            if (nodes.isEmpty() && !loading && error.isNotBlank()) {
                Text(
                    "Try refreshing, picking another country, or pasting a link below.",
                    color = NetconColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }

            Text(
                "PASTE A SERVER LINK",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            RecessedInput(raw, { raw = it }, "vless://, vmess://, trojan://, ss:// or Xray JSON", minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionButton("CANCEL", modifier = Modifier.weight(1f), onClick = onDismiss)
                CopperButton("IMPORT LOCALLY", Modifier.weight(1f)) { onImport(raw.trim()) }
            }
        }
    }
}

@Composable
private fun CompactMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = NetconColors.TextPrimary,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF070C14))
            .border(1.dp, Color(0xFF263447), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            color = NetconColors.TextSecondary,
            fontFamily = JetBrainsMonoFont,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = valueColor,
            fontFamily = JetBrainsMonoFont,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetalPanel(
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ServerNavyTop, ServerNavyBottom),
                ),
            )
            .border(1.dp, Color(0xFF2D3B50), RoundedCornerShape(11.dp))
            .padding(if (compact) 10.dp else 14.dp),
    ) {
        content()
    }
}

@Composable
private fun RecessedInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    secure: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF060A10))
            .border(1.dp, Color(0xFF2D3C50), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                color = NetconColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = NetconColors.TextPrimary,
                fontFamily = SpaceGroteskFont,
                fontSize = 13.sp,
            ),
            minLines = minLines,
            cursorBrush = Brush.verticalGradient(listOf(NetconColors.CopperLight, GoldAccent)),
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}

@Composable
private fun Keycap(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (selected) GoldAccent.copy(alpha = 0.24f) else Color(0xFF0D1722),
            )
            .border(
                1.dp,
                if (selected) GoldText else Color(0xFF2C3E52),
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) NetconColors.CopperLight else NetconColors.TextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CopperButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6D08A), Color(0xFFD9822B), Color(0xFF9C5114)),
                ),
            )
            .border(1.dp, Color(0xFFFBE0A9).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF1D0E05),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            color = Color(0xFF1D0E05),
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF131D2B))
            .border(1.dp, Color(0xFF3E4E68), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NetconColors.CopperLight,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            color = NetconColors.TextPrimary,
            fontFamily = JetBrainsMonoFont,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = DarkInk,
        fontFamily = JetBrainsMonoFont,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1000 -> "$bytes B"
    bytes < 1_000_000 -> "%.2f KB".format(bytes / 1000.0)
    bytes < 1_000_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
    else -> "%.2f GB".format(bytes / 1_000_000_000.0)
}