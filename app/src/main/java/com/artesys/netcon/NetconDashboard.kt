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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class ActiveSheet {
    NONE,
    IMPORT,
    PUBLIC_DIRECTORY,
    SETTINGS,
    DIAGNOSTICS,
}

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
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onRefreshLicenseStatus: () -> NetconLicenseManager.LicenseStatus,
    onActivateLicenseKey: (accountIdentifier: String, rawKey: String) -> NetconLicenseManager.ActivationResult,
    feedback: String,
    onFeedbackDismiss: () -> Unit,
    onImport: (String) -> Boolean,
    onFetchPublicNodes: suspend (String) -> NativePublicDirectory.Result,
    onRecommend: (String, Double, Double, List<NativePublicDirectory.Node>) -> NativeLocalServerRecommender.Recommendation,
    onSelectPublicNode: (NativePublicDirectory.Node) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBlockedProtocol: () -> Unit,
    onStartConfigured: (String, String) -> Unit,
    batteryOptimizationIgnored: Boolean,
    onToggleBatteryOptimization: (Boolean) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onExport: () -> Unit,
    onMeasurePing: suspend () -> Int?,
) {
    val scope = rememberCoroutineScope()
    val vpnStatus by produceState(initialValue = NetconVpnService.getStatus()) {
        while (true) {
            value = NetconVpnService.getStatus()
            onRefreshLicenseStatus()
            delay(1000)
        }
    }

    var activeSheet by rememberSaveable { mutableStateOf(ActiveSheet.NONE) }
    var licenseDialogOpen by rememberSaveable { mutableStateOf(false) }
    var preselectedCountry by rememberSaveable { mutableStateOf("") }

    var enabledProtocols by rememberSaveable {
        mutableStateOf(listOf("VLESS", "VMESS", "TROJAN", "SHADOWSOCKS", "SOCKS", "FREEDOM"))
    }
    var selectedDns by rememberSaveable { mutableStateOf("1.1.1.1") }
    var selectedRouting by rememberSaveable { mutableStateOf("Bypass LAN") }

    var measuredPingMs by remember { mutableStateOf<Int?>(null) }
    var pingBusy by remember { mutableStateOf(false) }

    val sentinelLogs = remember { mutableStateListOf<String>() }
    fun appendSentinelLog(line: String) {
        if (sentinelLogs.size >= 40) sentinelLogs.removeAt(0)
        sentinelLogs.add(line)
    }

    val isConnected = vpnStatus.state == "connected"
    val isConnecting = vpnStatus.state == "connecting"
    val hasServer = serverName.isNotBlank()

    LaunchedEffect(vpnStatus.state, serverName) {
        if (vpnStatus.state == "connected") {
            appendSentinelLog("[TUNNEL] Connected -> ${serverName.ifBlank { "Active Node" }} (${protocol.ifBlank { "XRAY" }})")
            if (measuredPingMs == null && hasServer) {
                pingBusy = true
                measuredPingMs = onMeasurePing()
                pingBusy = false
            }
        } else if (vpnStatus.state == "error" && vpnStatus.error.isNotBlank()) {
            appendSentinelLog("[ERROR] ${vpnStatus.error}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A0F17),
                        Color(0xFF070A10),
                        Color(0xFF05070B),
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Top Hardware Bezel Header & License Status Badge
            TopHardwareHeader(
                vpnState = vpnStatus.state,
                protocol = protocol,
                licenseStatus = licenseStatus,
                onOpenLicenseDialog = { licenseDialogOpen = true },
            )

            // 2. Feedback Banner (only shown when real system/action message exists)
            if (feedback.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF142232))
                        .border(1.dp, NetconColors.CyberCyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        feedback,
                        color = NetconColors.TextPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss feedback",
                        tint = NetconColors.TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onFeedbackDismiss),
                    )
                }
            }

            // 3. Interactive Global Routing Map (Proton-style Vector Globe)
            ProtonWorldMapPanel(
                serverName = serverName,
                locationName = serverLocation,
                latitude = serverLatitude,
                longitude = serverLongitude,
                userLatitude = userLatitude,
                userLongitude = userLongitude,
                userLocationSource = userLocationSource,
                userLocationBusy = userLocationBusy,
                vpnState = vpnStatus.state,
                onBrowseCountryServers = { code ->
                    preselectedCountry = code
                    activeSheet = ActiveSheet.PUBLIC_DIRECTORY
                },
                onRequestUserLocation = onRequestUserLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                compact = true,
            )

            // 4. Real-Time Telemetry Cluster (Live Ping Gauge + Live RX/TX Throughput + Session Timer)
            TelemetryClusterCard(
                vpnStatus = vpnStatus,
                pingMs = measuredPingMs,
                pingBusy = pingBusy,
                hasServer = hasServer,
                onTapPing = {
                    if (!hasServer || pingBusy) return@TelemetryClusterCard
                    scope.launch {
                        pingBusy = true
                        val res = onMeasurePing()
                        measuredPingMs = res
                        pingBusy = false
                        appendSentinelLog(
                            if (res != null) "[PING] TCP handshake latency: ${res}ms"
                            else "[PING] Target endpoint unreachable",
                        )
                    }
                },
            )

            // 5. Active Server Profile Selector & Quick Actions Bar
            ActiveServerControlCard(
                serverName = serverName,
                protocol = protocol,
                serverLocation = serverLocation,
                onOpenImport = { activeSheet = ActiveSheet.IMPORT },
                onOpenPublicDirectory = {
                    preselectedCountry = ""
                    activeSheet = ActiveSheet.PUBLIC_DIRECTORY
                },
                onOpenSettings = { activeSheet = ActiveSheet.SETTINGS },
                onOpenDiagnostics = { activeSheet = ActiveSheet.DIAGNOSTICS },
            )

            // 6. Primary Tactical Power Switch
            PowerIgnitionDeck(
                vpnState = vpnStatus.state,
                hasServer = hasServer,
                onTogglePower = {
                    if (isConnected || isConnecting) {
                        onDisconnect()
                    } else if (!hasServer) {
                        activeSheet = ActiveSheet.PUBLIC_DIRECTORY
                    } else {
                        val upperProto = protocol.uppercase(Locale.ROOT)
                        if (upperProto.isNotBlank() && upperProto !in enabledProtocols) {
                            onBlockedProtocol()
                        } else {
                            onStartConfigured(selectedDns, selectedRouting)
                        }
                    }
                },
            )
        }

        // Modal Sheets
        when (activeSheet) {
            ActiveSheet.IMPORT -> {
                ImportConfigDialog(
                    onDismiss = { activeSheet = ActiveSheet.NONE },
                    onSubmit = { raw ->
                        val ok = onImport(raw)
                        if (ok) {
                            measuredPingMs = null
                            appendSentinelLog("[IMPORT] Saved custom Xray configuration")
                            activeSheet = ActiveSheet.NONE
                        }
                        ok
                    },
                )
            }

            ActiveSheet.PUBLIC_DIRECTORY -> {
                PublicDirectoryDialog(
                    initialCountryCode = preselectedCountry,
                    userLatitude = userLatitude,
                    userLongitude = userLongitude,
                    onDismiss = { activeSheet = ActiveSheet.NONE },
                    onFetchPublicNodes = onFetchPublicNodes,
                    onRecommend = onRecommend,
                    onSelectNode = { node ->
                        onSelectPublicNode(node)
                        measuredPingMs = null
                        appendSentinelLog("[NODE] Selected ${node.name} (${node.country})")
                        activeSheet = ActiveSheet.NONE
                    },
                )
            }

            ActiveSheet.SETTINGS -> {
                SettingsDialog(
                    enabledProtocols = enabledProtocols,
                    onToggleProtocol = { proto ->
                        enabledProtocols = if (proto in enabledProtocols) {
                            enabledProtocols - proto
                        } else {
                            enabledProtocols + proto
                        }
                    },
                    selectedDns = selectedDns,
                    onSelectDns = { selectedDns = it },
                    selectedRouting = selectedRouting,
                    onSelectRouting = { selectedRouting = it },
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    onToggleBatteryOptimization = onToggleBatteryOptimization,
                    onOpenVpnSettings = onOpenVpnSettings,
                    onExport = onExport,
                    onDismiss = { activeSheet = ActiveSheet.NONE },
                )
            }

            ActiveSheet.DIAGNOSTICS -> {
                DiagnosticsDialog(
                    vpnStatus = vpnStatus,
                    serverName = serverName,
                    protocol = protocol,
                    userLocationSource = userLocationSource,
                    logs = sentinelLogs,
                    onClearLogs = { sentinelLogs.clear() },
                    onDismiss = { activeSheet = ActiveSheet.NONE },
                )
            }

            ActiveSheet.NONE -> Unit
        }

        if (licenseDialogOpen && !licenseStatus.isFrozen) {
            LicenseActivationDialog(
                licenseStatus = licenseStatus,
                onDismiss = { licenseDialogOpen = false },
                onActivateLicenseKey = { accountId, raw ->
                    val res = onActivateLicenseKey(accountId, raw)
                    if (res.success) {
                        appendSentinelLog("[LICENSE] Activated ${res.status.activatedKeyMasked}")
                        licenseDialogOpen = false
                    }
                    res
                },
            )
        }

        if (licenseStatus.isFrozen) {
            FrozenTrialLockOverlay(
                licenseStatus = licenseStatus,
                onActivateLicenseKey = { accountId, raw ->
                    val res = onActivateLicenseKey(accountId, raw)
                    if (res.success) {
                        appendSentinelLog("[LICENSE] Unlocked ${res.status.activatedKeyMasked}")
                    }
                    res
                },
            )
        }
    }
}

@Composable
private fun TopHardwareHeader(
    vpnState: String,
    protocol: String,
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onOpenLicenseDialog: () -> Unit,
) {
    val stateColor = when (vpnState) {
        "connected" -> NetconColors.Online
        "connecting" -> NetconColors.CopperLight
        "error" -> NetconColors.ErrorRed
        else -> NetconColors.TextSecondary
    }
    val licColor = when {
        licenseStatus.isLicensed -> NetconColors.OnlineSoft
        licenseStatus.isFrozen -> NetconColors.ErrorRed
        else -> NetconColors.CopperLight
    }
    val licText = when {
        licenseStatus.isLicensed -> "PRO LICENSED"
        licenseStatus.isFrozen -> "TRIAL EXPIRED"
        else -> "TRIAL · ${licenseStatus.formatRemainingCountdown()}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(NetconColors.PanelSurface)
            .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(stateColor, CircleShape),
            )
            Column {
                Text(
                    "NETCON XRAY CORE",
                    color = NetconColors.TextPrimary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                val protoLabel = protocol.ifBlank { "STANDBY" }
                Text(
                    "$protoLabel · TUN/V2RAY",
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
                color = licColor,
                fontFamily = JetBrainsMonoFont,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TelemetryClusterCard(
    vpnStatus: NetconVpnService.Status,
    pingMs: Int?,
    pingBusy: Boolean,
    hasServer: Boolean,
    onTapPing: () -> Unit,
) {
    val isConnected = vpnStatus.state == "connected"
    val downRate = if (isConnected) formatRate(vpnStatus.downloadBytesPerSecond) else "0 B/s"
    val upRate = if (isConnected) formatRate(vpnStatus.uploadBytesPerSecond) else "0 B/s"
    val totalRx = if (isConnected) formatBytes(vpnStatus.receivedBytes) else "0 B"
    val totalTx = if (isConnected) formatBytes(vpnStatus.sentBytes) else "0 B"
    val sessionClock = if (isConnected) formatDuration(vpnStatus.elapsedSeconds) else "00:00:00"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(NetconColors.PanelSurface)
            .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Real TCP Latency Dial
        AnalogPingDial(
            pingMs = pingMs,
            busy = pingBusy,
            enabled = hasServer,
            onClick = onTapPing,
        )

        // Live Throughput & Session Telemetry
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetryMetricBlock(
                    label = "DOWNLINK",
                    value = downRate,
                    sub = "RX $totalRx",
                    accent = NetconColors.OnlineSoft,
                    modifier = Modifier.weight(1f),
                )
                TelemetryMetricBlock(
                    label = "UPLINK",
                    value = upRate,
                    sub = "TX $totalTx",
                    accent = NetconColors.CyberCyan,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(NetconColors.DeepInk)
                    .border(1.dp, NetconColors.BevelBorder.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SESSION UPTIME",
                    color = NetconColors.TextMuted,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    sessionClock,
                    color = if (isConnected) NetconColors.OnlineSoft else NetconColors.TextSecondary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricBlock(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            color = NetconColors.TextMuted,
            fontFamily = JetBrainsMonoFont,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            value,
            color = accent,
            fontFamily = JetBrainsMonoFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            sub,
            color = NetconColors.TextSecondary,
            fontFamily = JetBrainsMonoFont,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AnalogPingDial(
    pingMs: Int?,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val targetFraction = when {
        pingMs == null -> 0f
        else -> (pingMs.coerceIn(1, 500) / 500f)
    }
    val needleFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(stiffness = 260f, dampingRatio = 0.8f),
        label = "pingNeedle",
    )
    val dialColor = when {
        pingMs == null -> NetconColors.TextMuted
        pingMs < 110 -> NetconColors.OnlineSoft
        pingMs < 240 -> NetconColors.CopperLight
        else -> NetconColors.ErrorRed
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(NetconColors.DeepInk)
            .border(1.5.dp, NetconColors.BevelHighlight, CircleShape)
            .testTag("ping_gauge")
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 3.dp.toPx()
            drawArc(
                color = NetconColors.BevelBorder,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (needleFraction > 0f) {
                drawArc(
                    color = dialColor,
                    startAngle = 140f,
                    sweepAngle = 260f * needleFraction,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            val angleRad = Math.toRadians((140f + 260f * needleFraction).toDouble())
            val radius = min(size.width, size.height) / 2f - 4.dp.toPx()
            val end = Offset(
                x = center.x + (cos(angleRad) * radius).toFloat(),
                y = center.y + (sin(angleRad) * radius).toFloat(),
            )
            drawLine(
                color = dialColor,
                start = center,
                end = end,
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(color = NetconColors.TextPrimary, radius = 2.5.dp.toPx(), center = center)
        }
        Column(
            modifier = Modifier.padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    busy -> "…"
                    pingMs != null -> "${pingMs}MS"
                    else -> "--"
                },
                color = dialColor,
                fontFamily = JetBrainsMonoFont,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "PING",
                color = NetconColors.TextMuted,
                fontFamily = JetBrainsMonoFont,
                fontSize = 6.5.sp,
            )
        }
    }
}

@Composable
private fun ActiveServerControlCard(
    serverName: String,
    protocol: String,
    serverLocation: String,
    onOpenImport: () -> Unit,
    onOpenPublicDirectory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(NetconColors.PanelSurface)
            .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(11.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    serverName.ifBlank { "No Server Loaded" },
                    color = NetconColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (serverName.isBlank()) {
                        "Import a config link or browse live public nodes"
                    } else {
                        listOfNotNull(
                            protocol.takeIf { it.isNotBlank() },
                            serverLocation.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { "Custom Xray Node" }
                    },
                    color = NetconColors.CyberCyan,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallHardwareChip(
                    label = "IMPORT",
                    tag = "import_link_button",
                    onClick = onOpenImport,
                )
                SmallHardwareChip(
                    label = "NODES",
                    tag = "open_public_servers_button",
                    accent = true,
                    onClick = onOpenPublicDirectory,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SmallHardwareChip(
                label = "SWITCH NODE",
                tag = "switch_server_button",
                modifier = Modifier.weight(1f),
                onClick = onOpenPublicDirectory,
            )
            SmallHardwareChip(
                label = "SETTINGS",
                tag = "settings_button",
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings,
            )
            SmallHardwareChip(
                label = "SENTINEL LOG",
                tag = "ai_panel_toggle",
                modifier = Modifier.weight(1f),
                onClick = onOpenDiagnostics,
            )
        }
    }
}

@Composable
private fun SmallHardwareChip(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (accent) {
                    Brush.verticalGradient(listOf(NetconColors.CopperLight, NetconColors.Copper))
                } else {
                    Brush.verticalGradient(listOf(NetconColors.PanelElevated, NetconColors.ChassisDark))
                },
            )
            .border(
                1.dp,
                if (accent) NetconColors.CopperLight else NetconColors.BevelHighlight.copy(alpha = 0.7f),
                RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (accent) Color(0xFF1D0E05) else NetconColors.TextPrimary,
            fontFamily = JetBrainsMonoFont,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PowerIgnitionDeck(
    vpnState: String,
    hasServer: Boolean,
    onTogglePower: () -> Unit,
) {
    val isConnected = vpnState == "connected"
    val isConnecting = vpnState == "connecting"
    val infiniteTransition = rememberInfiniteTransition(label = "powerGlow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "powerPulse",
    )

    val buttonBrush = when {
        isConnected -> Brush.horizontalGradient(listOf(Color(0xFF15803D), Color(0xFF22C55E)))
        isConnecting -> Brush.horizontalGradient(listOf(NetconColors.CopperDark, NetconColors.Copper))
        !hasServer -> Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF334155)))
        else -> Brush.horizontalGradient(listOf(NetconColors.Copper, NetconColors.CopperLight))
    }

    val label = when {
        isConnected -> "DISCONNECT TUNNEL"
        isConnecting -> "ESTABLISHING HANDSHAKE…"
        !hasServer -> "SELECT OR IMPORT SERVER"
        else -> "ENGAGE XRAY TUNNEL"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("vpn_power_button")
            .clip(RoundedCornerShape(12.dp))
            .background(buttonBrush)
            .border(
                1.5.dp,
                when {
                    isConnected -> NetconColors.OnlineSoft.copy(alpha = pulseAlpha)
                    isConnecting -> NetconColors.CopperLight.copy(alpha = pulseAlpha)
                    else -> NetconColors.BevelHighlight
                },
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onTogglePower),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = label,
                tint = if (isConnected || !hasServer) NetconColors.TextPrimary else Color(0xFF1D0E05),
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                color = if (isConnected || !hasServer) NetconColors.TextPrimary else Color(0xFF1D0E05),
                fontFamily = JetBrainsMonoFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
            )
        }
    }
}

@Composable
private fun ImportConfigDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean,
) {
    var input by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "IMPORT XRAY CONFIGURATION",
                    color = NetconColors.TextPrimary,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            Text(
                "Paste a vless://, vmess://, trojan://, ss://, socks5:// URI or raw Xray JSON.",
                color = NetconColors.TextSecondary,
                fontSize = 11.sp,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetconColors.DeepInk)
                    .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(
                        color = NetconColors.TextPrimary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.sp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "vless://... or {\"outbounds\":[...]}",
                                color = NetconColors.TextMuted,
                                fontFamily = JetBrainsMonoFont,
                                fontSize = 11.sp,
                            )
                        }
                        inner()
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SmallHardwareChip(
                    label = "SAVE PROFILE",
                    tag = "save_import_button",
                    accent = true,
                    onClick = { onSubmit(input) },
                )
            }
        }
    }
}

@Composable
private fun PublicDirectoryDialog(
    initialCountryCode: String,
    userLatitude: Double?,
    userLongitude: Double?,
    onDismiss: () -> Unit,
    onFetchPublicNodes: suspend (String) -> NativePublicDirectory.Result,
    onRecommend: (String, Double, Double, List<NativePublicDirectory.Node>) -> NativeLocalServerRecommender.Recommendation,
    onSelectNode: (NativePublicDirectory.Node) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedCountry by rememberSaveable { mutableStateOf(initialCountryCode) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<NativePublicDirectory.Node>>(emptyList()) }
    var recommendationText by remember { mutableStateOf("") }
    var selectedActivity by rememberSaveable { mutableStateOf("Browsing") }
    var countryMenuOpen by remember { mutableStateOf(false) }

    fun loadCountry(code: String) {
        scope.launch {
            loading = true
            errorMsg = ""
            recommendationText = ""
            try {
                val res = onFetchPublicNodes(code)
                nodes = res.nodes
                if (res.nodes.isEmpty()) {
                    errorMsg = "No valid nodes returned for ${code.ifBlank { "Global" }}."
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to load directory."
                nodes = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(selectedCountry) {
        loadCountry(selectedCountry)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NetconColors.DeepInk)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = NetconColors.CyberCyan, modifier = Modifier.size(18.dp))
                    Text(
                        "LIVE PUBLIC NODE DIRECTORY",
                        color = NetconColors.TextPrimary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    SmallHardwareChip(
                        label = "REGION: ${selectedCountry.ifBlank { "ALL (TOP)" }}",
                        tag = "country_filter_button",
                        onClick = { countryMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = countryMenuOpen,
                        onDismissRequest = { countryMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("ALL (TOP)") },
                            onClick = {
                                selectedCountry = ""
                                countryMenuOpen = false
                            },
                        )
                        NativePublicDirectory.countryCodes.forEach { code ->
                            DropdownMenuItem(
                                text = { Text("$code · ${Locale("", code).displayCountry}") },
                                onClick = {
                                    selectedCountry = code
                                    countryMenuOpen = false
                                },
                            )
                        }
                    }
                }

                SmallHardwareChip(
                    label = "REFRESH",
                    tag = "refresh_directory_button",
                    onClick = { loadCountry(selectedCountry) },
                )

                SmallHardwareChip(
                    label = "AUTO MATCH",
                    tag = "local_server_match_button",
                    accent = true,
                    onClick = {
                        val lat = userLatitude ?: 1.3521
                        val lon = userLongitude ?: 103.8198
                        if (nodes.isEmpty()) {
                            errorMsg = "Load public servers before requesting a local match."
                        } else {
                            try {
                                val rec = onRecommend(selectedActivity, lat, lon, nodes)
                                recommendationText = rec.reason
                                nodes.firstOrNull { it.id == rec.nodeId }?.let(onSelectNode)
                            } catch (e: Exception) {
                                errorMsg = e.message ?: "Unable to match server."
                            }
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NativeLocalServerRecommender.activities.forEach { act ->
                    val active = selectedActivity == act
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color(0xFF15293B) else NetconColors.PanelSurface)
                            .border(
                                1.dp,
                                if (active) NetconColors.CyberCyan else NetconColors.BevelBorder,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { selectedActivity = act }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            act.uppercase(Locale.ROOT),
                            color = if (active) NetconColors.CyberCyan else NetconColors.TextSecondary,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (recommendationText.isNotBlank()) {
                Text(
                    recommendationText,
                    color = NetconColors.CyberCyan,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NetconColors.PanelSurface)
                        .padding(8.dp),
                )
            }

            if (loading) {
                Text(
                    "Fetching live Xray nodes…",
                    color = NetconColors.CopperLight,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 11.sp,
                )
            } else if (errorMsg.isNotBlank()) {
                Text(
                    errorMsg,
                    color = NetconColors.ErrorRed,
                    fontSize = 11.sp,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                nodes.forEach { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NetconColors.PanelSurface)
                            .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectNode(node) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
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
                        Text(
                            "USE",
                            color = NetconColors.CopperLight,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    enabledProtocols: List<String>,
    onToggleProtocol: (String) -> Unit,
    selectedDns: String,
    onSelectDns: (String) -> Unit,
    selectedRouting: String,
    onSelectRouting: (String) -> Unit,
    batteryOptimizationIgnored: Boolean,
    onToggleBatteryOptimization: (Boolean) -> Unit,
    onOpenVpnSettings: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = NetconColors.CopperLight, modifier = Modifier.size(18.dp))
                    Text(
                        "TUNNEL & CORE SETTINGS",
                        color = NetconColors.TextPrimary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            Text(
                "ALLOWED OUTBOUND PROTOCOLS",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("VLESS", "VMESS", "TROJAN", "SHADOWSOCKS").forEach { proto ->
                    val enabled = proto in enabledProtocols
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (enabled) Color(0xFF15293B) else NetconColors.DeepInk)
                            .border(
                                1.dp,
                                if (enabled) NetconColors.OnlineSoft else NetconColors.BevelBorder,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { onToggleProtocol(proto) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            proto.take(6),
                            color = if (enabled) NetconColors.OnlineSoft else NetconColors.TextMuted,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                "UPSTREAM DNS RESOLVER",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("1.1.1.1", "8.8.8.8", "9.9.9.9").forEach { dns ->
                    val active = selectedDns == dns
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color(0xFF15293B) else NetconColors.DeepInk)
                            .border(
                                1.dp,
                                if (active) NetconColors.CyberCyan else NetconColors.BevelBorder,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { onSelectDns(dns) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            dns,
                            color = if (active) NetconColors.CyberCyan else NetconColors.TextSecondary,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                "ROUTING POLICY",
                color = NetconColors.TextSecondary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Bypass LAN", "Global Proxy", "Rule-based").forEach { mode ->
                    val active = selectedRouting == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) Color(0xFF15293B) else NetconColors.DeepInk)
                            .border(
                                1.dp,
                                if (active) NetconColors.CyberCyan else NetconColors.BevelBorder,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { onSelectRouting(mode) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            mode,
                            color = if (active) NetconColors.CyberCyan else NetconColors.TextSecondary,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetconColors.DeepInk)
                    .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "IGNORE BATTERY OPTIMIZATION",
                        color = NetconColors.TextPrimary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (batteryOptimizationIgnored) "Unrestricted background tunnel enabled"
                        else "Tap to exempt :vpn process from Doze",
                        color = NetconColors.TextSecondary,
                        fontSize = 10.sp,
                    )
                }
                Switch(
                    checked = batteryOptimizationIgnored,
                    onCheckedChange = onToggleBatteryOptimization,
                    modifier = Modifier.testTag("battery_optimization_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NetconColors.OnlineSoft,
                        checkedTrackColor = Color(0xFF14532D),
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmallHardwareChip(
                    label = "ANDROID VPN SETTINGS",
                    tag = "android_vpn_settings_button",
                    modifier = Modifier.weight(1f),
                    onClick = onOpenVpnSettings,
                )
                SmallHardwareChip(
                    label = "EXPORT JSON",
                    tag = "export_json_button",
                    modifier = Modifier.weight(1f),
                    accent = true,
                    onClick = onExport,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsDialog(
    vpnStatus: NetconVpnService.Status,
    serverName: String,
    protocol: String,
    userLocationSource: String,
    logs: List<String>,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "LIVE SENTINEL DIAGNOSTICS",
                    color = NetconColors.CyberCyan,
                    fontFamily = JetBrainsMonoFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetconColors.DeepInk)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("STATE: ${vpnStatus.state.uppercase(Locale.ROOT)}", color = NetconColors.TextPrimary, fontFamily = JetBrainsMonoFont, fontSize = 10.sp)
                Text("NODE: ${serverName.ifBlank { "NONE" }} (${protocol.ifBlank { "NONE" }})", color = NetconColors.TextSecondary, fontFamily = JetBrainsMonoFont, fontSize = 9.5.sp)
                Text("ORIGIN: ${userLocationSource.ifBlank { "Pending GPS/Network fix" }}", color = NetconColors.TextSecondary, fontFamily = JetBrainsMonoFont, fontSize = 9.5.sp)
                if (vpnStatus.error.isNotBlank()) {
                    Text("LAST ERROR: ${vpnStatus.error}", color = NetconColors.ErrorRed, fontFamily = JetBrainsMonoFont, fontSize = 9.5.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NetconColors.DeepInk)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "No live events recorded in this session yet.",
                        color = NetconColors.TextMuted,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 9.5.sp,
                    )
                } else {
                    logs.forEach { entry ->
                        Text(
                            entry,
                            color = NetconColors.TextSecondary,
                            fontFamily = JetBrainsMonoFont,
                            fontSize = 9.sp,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SmallHardwareChip(
                    label = "CLEAR LOGS",
                    tag = "clear_sentinel_logs_button",
                    onClick = onClearLogs,
                )
            }
        }
    }
}

@Composable
private fun LicenseActivationDialog(
    licenseStatus: NetconLicenseManager.LicenseStatus,
    onDismiss: () -> Unit,
    onActivateLicenseKey: (String, String) -> NetconLicenseManager.ActivationResult,
) {
    var accountInput by rememberSaveable { mutableStateOf(licenseStatus.activatedAccount) }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NetconColors.PanelSurface)
                .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = NetconColors.CopperLight, modifier = Modifier.size(16.dp))
                    Text(
                        "LICENSE ACTIVATION",
                        color = NetconColors.TextPrimary,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = NetconColors.TextSecondary)
                }
            }

            Text(
                licenseStatus.badgeSummary(),
                color = NetconColors.CopperLight,
                fontFamily = JetBrainsMonoFont,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
            )

            LicenseInputBox(
                value = accountInput,
                onValueChange = { accountInput = it },
                placeholder = "Account email (e.g. user@domain.com)",
                tag = "dialog_account_input",
            )

            LicenseInputBox(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = "NETCON-XXXX-XXXX-XXXX-XXXX",
                tag = "dialog_license_input",
            )

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, color = NetconColors.ErrorRed, fontSize = 10.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SmallHardwareChip(
                    label = "ACTIVATE KEY",
                    tag = "dialog_activate_key_button",
                    accent = true,
                    onClick = {
                        val res = onActivateLicenseKey(accountInput, keyInput)
                        if (!res.success) statusMessage = res.message
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
) {
    var accountInput by rememberSaveable { mutableStateOf("") }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF205070B))
            .testTag("frozen_trial_lock_overlay")
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NetconColors.PanelSurface)
                .border(1.5.dp, NetconColors.ErrorRed, RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "3-DAY FREE TRIAL EXPIRED",
                color = NetconColors.ErrorRed,
                fontFamily = JetBrainsMonoFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Your 72-hour evaluation window (${licenseStatus.formatRemainingCountdown()}) has ended. Enter your account-bound license key to unlock Xray tunnel connections.",
                color = NetconColors.TextSecondary,
                fontSize = 11.sp,
            )

            LicenseInputBox(
                value = accountInput,
                onValueChange = { accountInput = it },
                placeholder = "Licensed account email",
                tag = "frozen_account_input",
            )

            LicenseInputBox(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = "NETCON-XXXX-XXXX-XXXX-XXXX",
                tag = "frozen_license_input",
            )

            if (errorMsg.isNotBlank()) {
                Text(errorMsg, color = NetconColors.ErrorRed, fontSize = 10.sp)
            }

            SmallHardwareChip(
                label = "UNLOCK WITH LICENSE KEY",
                tag = "frozen_activate_button",
                accent = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val res = onActivateLicenseKey(accountInput, keyInput)
                    if (!res.success) errorMsg = res.message
                },
            )
        }
    }
}

@Composable
private fun LicenseInputBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NetconColors.DeepInk)
            .border(1.dp, NetconColors.BevelBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = NetconColors.TextPrimary,
                fontFamily = JetBrainsMonoFont,
                fontSize = 11.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = NetconColors.TextMuted,
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.sp,
                    )
                }
                inner()
            },
        )
    }
}

private fun formatRate(bytesPerSec: Double): String {
    if (bytesPerSec <= 0.0) return "0 B/s"
    return "${formatBytes(bytesPerSec.toLong())}/s"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val h = safe / 3600L
    val m = (safe % 3600L) / 60L
    val s = safe % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
