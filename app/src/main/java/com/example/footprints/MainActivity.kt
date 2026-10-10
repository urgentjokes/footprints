package com.example.footprints

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.footprints.ui.theme.FootprintsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocationHelper.ensureDocumentsFolderExists(this)
        setContent {
            FootprintsTheme {
                FootprintsApp()
            }
        }
    }
}

enum class AppScreen {
    MAIN,
    SETTINGS
}

@Composable
fun FootprintsApp(viewModel: FootprintsViewModel = viewModel()) {
    val context = LocalContext.current
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.MAIN) }

    LaunchedEffect(Unit) {
        viewModel.initSettings(context)
    }

    when (currentScreen) {
        AppScreen.MAIN -> {
            MainFootprintsScreen(
                viewModel = viewModel,
                onNavigateToSettings = { currentScreen = AppScreen.SETTINGS }
            )
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = AppScreen.MAIN }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFootprintsScreen(
    viewModel: FootprintsViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val records = viewModel.records
    val isLoading = viewModel.isLoading
    val statusMessage = viewModel.statusMessage
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var showStopDialog by rememberSaveable { mutableStateOf(false) }
    var showSosDialog by rememberSaveable { mutableStateOf(false) }
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val autoCaptureLocation = viewModel.autoCaptureLocation

    // When in auto-capture mode, ensure the display is always scrolled to the bottom (most recent capture visible)
    LaunchedEffect(records.size, autoCaptureLocation) {
        if (autoCaptureLocation && records.isNotEmpty()) {
            listState.animateScrollToItem(records.size - 1)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            viewModel.recordCurrentLocation(context)
        } else {
            Toast.makeText(context, "Fine location permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    fun onPlusClick() {
        if (isLoading) return
        if (LocationHelper.hasLocationPermission(context)) {
            viewModel.recordCurrentLocation(
                context = context,
                onComplete = {
                    coroutineScope.launch {
                        if (records.isNotEmpty()) {
                            listState.animateScrollToItem(records.size - 1)
                        }
                    }
                }
            )
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Footprints",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share current location") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.textMostRecentLocation(context)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share whole journey") },
                                onClick = {
                                    menuExpanded = false
                                    Toast.makeText(context, "Share whole journey (coming soon)", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export log") },
                                onClick = {
                                    menuExpanded = false
                                    Toast.makeText(context, "Export log (coming soon)", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateToSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Help") },
                                onClick = {
                                    menuExpanded = false
                                    showHelpDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Dedicated bottom row for actions - map cards will not intrude
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: SOS button (same size and circular shape)
                    CircularActionButton(
                        iconRes = R.drawable.ic_sos_button,
                        contentDescription = "Emergency SOS",
                        size = 74.dp,
                        onClick = { showSosDialog = true }
                    )

                    // Center: Footprint button
                    FootprintButton(
                        isLoading = isLoading,
                        size = 74.dp,
                        onClick = { onPlusClick() }
                    )

                    // Right: STOP-sign button (same size and circular shape)
                    CircularActionButton(
                        iconRes = R.drawable.ic_stop_button,
                        contentDescription = "Stop Journey",
                        size = 74.dp,
                        onClick = { showStopDialog = true }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen output area: renders list when records exist, or helpful empty state
            if (records.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(records, key = { _, item -> item.id }) { index, item ->
                        LocationCard(
                            index = index + 1,
                            record = item,
                            showMap = viewModel.showMapImage
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "No footprints in current journey",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the footprint button below to capture your current location.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Status message toast banner when acquiring
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = statusMessage ?: "",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    // STOP Dialog: presents 3 choices
    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = {
                Text(
                    text = "End Journey",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (records.isNotEmpty()) {
                            "You have recorded ${records.size} location footprint(s). Choose how to end this journey:"
                        } else {
                            "Choose an option to reset your journey:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Choice 1: Save and start new journey
                    Button(
                        onClick = {
                            showStopDialog = false
                            viewModel.saveAndStartNewJourney(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Save and start new journey",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Choice 2: Discard and start new journey
                    OutlinedButton(
                        onClick = {
                            showStopDialog = false
                            viewModel.discardAndStartNewJourney(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Discard and start new journey",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Choice 3: Cancel
                    TextButton(
                        onClick = { showStopDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // SOS Dialog
    if (showSosDialog) {
        val lastRecord = records.lastOrNull()
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = {
                Text(
                    text = "🚨 Emergency SOS",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Broadcast your emergency status and coordinates to responders or contacts.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (lastRecord != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Coordinates: %+.6f, %+.6f\nTime: %s".format(
                                lastRecord.latitude,
                                lastRecord.longitude,
                                lastRecord.timestamp
                            ),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            showSosDialog = false
                            viewModel.textMostRecentLocation(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Text current location", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            showSosDialog = false
                            val lat = lastRecord?.latitude
                            val lon = lastRecord?.longitude
                            val text = if (lat != null && lon != null) {
                                "EMERGENCY SOS: I need help! My current GPS location is: https://maps.google.com/?q=$lat,$lon ($lat, $lon)"
                            } else {
                                "EMERGENCY SOS: I need help! Please contact me."
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, text)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Send SOS Message via"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Share via other app")
                    }

                    TextButton(
                        onClick = { showSosDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "Help & Information",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "• Footprints Button (Center): Tap to record your current GPS coordinates, timestamp, and map preview.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Finish Journey (Right): End your current journey. Choose to save your log as a CSV to 'Documents/Footprints log' or discard it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Emergency SOS (Left): Instantly prepare and broadcast an emergency SOS message with your latest GPS coordinates.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FootprintsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isWithMap = viewModel.showMapImage

    BackHandler { onNavigateBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Single-row setting: "Show map" and an on-off switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setShowMapImage(context, !isWithMap) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show map",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Switch(
                    checked = isWithMap,
                    onCheckedChange = { viewModel.setShowMapImage(context, it) }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Setting 2: "Automatically capture location" with on-off switch
            val autoCaptureLocation = viewModel.autoCaptureLocation
            val autoCaptureIntervalText = viewModel.autoCaptureIntervalText
            val runInBackground = viewModel.runInBackground
            val enabledColor = MaterialTheme.colorScheme.onBackground
            val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            val textColor = if (autoCaptureLocation) enabledColor else disabledColor
            val subTextColor = if (autoCaptureLocation) MaterialTheme.colorScheme.onSurfaceVariant else disabledColor

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { _ -> }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !autoCaptureLocation
                        if (next && runInBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.setAutoCaptureLocation(context, next)
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Automatically capture location",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Switch(
                    checked = autoCaptureLocation,
                    onCheckedChange = { next ->
                        if (next && runInBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.setAutoCaptureLocation(context, next)
                    }
                )
            }

            // Field: "Every ___ minutes" (greyed out when switched off)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Every",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )

                BasicTextField(
                    value = autoCaptureIntervalText,
                    onValueChange = { viewModel.setAutoCaptureIntervalText(context, it) },
                    enabled = autoCaptureLocation,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (autoCaptureLocation) MaterialTheme.colorScheme.onBackground else disabledColor,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .width(68.dp)
                                .height(36.dp)
                                .background(
                                    color = if (autoCaptureLocation) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (autoCaptureLocation) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (autoCaptureIntervalText.isEmpty()) {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = disabledColor,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Text(
                    text = "minutes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
            }

            // Setting 3: "Run in background" (runs as foreground service when app loses focus)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = autoCaptureLocation) {
                        val next = !runInBackground
                        if (next && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.setRunInBackground(context, next)
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    Text(
                        text = "Run in background",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                    Text(
                        text = "Foreground service keeps capturing when app is closed",
                        style = MaterialTheme.typography.bodySmall,
                        color = subTextColor
                    )
                }

                Switch(
                    checked = runInBackground,
                    enabled = autoCaptureLocation,
                    onCheckedChange = { next ->
                        if (next && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.setRunInBackground(context, next)
                    }
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun CircularActionButton(
    iconRes: Int,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun FootprintButton(
    isLoading: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_human_footprints_button),
            contentDescription = "Record Footprint Location",
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.45f),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

@Composable
fun LocationCard(
    index: Int,
    record: LocationRecord,
    showMap: Boolean = true
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ENTRY #$index",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = record.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Latitude:  %+.6f°".format(record.latitude),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Longitude: %+.6f°".format(record.longitude),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            // Accompanying Map Image with Red Pin (like Google Maps)
            if (showMap) {
                Spacer(modifier = Modifier.height(12.dp))
                val imageModel = remember(record.mapImagePath, record.latitude, record.longitude) {
                    record.mapImagePath?.let { java.io.File(it) }
                        ?: MapHelper.getMapImageUrl(context, record.latitude, record.longitude)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            val uri = "geo:${record.latitude},${record.longitude}?q=${record.latitude},${record.longitude}(Footprint #$index)".toUri()
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val webUri = "https://maps.google.com/?q=${record.latitude},${record.longitude}".toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                            }
                        }
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .setHeader("User-Agent", "FootprintsApp/1.0 (Android)")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Map location preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tap to open Google Maps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    )

                    // Precise Location Marker Pin aligned over exact GPS coordinates
                    val tileFrac = remember(record.latitude, record.longitude) {
                        MapHelper.getTileFraction(record.latitude, record.longitude)
                    }
                    val pinAlignment = remember(tileFrac) {
                        androidx.compose.ui.BiasAlignment(
                            horizontalBias = (tileFrac.x - 0.5f) * 2f,
                            verticalBias = (tileFrac.y - 0.5f) * 2f
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Location Pin",
                        tint = Color(0xFFE53935),
                        modifier = Modifier
                            .size(36.dp)
                            .align(pinAlignment)
                            .offset(y = (-14).dp)
                    )

                    // Pill Badge at bottom right: "Show location on Google Maps ↗"
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Show location on Google Maps ↗",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Accuracy: ±%.1f m".format(record.accuracy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (record.altitude != null && record.altitude != 0.0) {
                    Text(
                        text = "Alt: %.1f m".format(record.altitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "via ${record.provider}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
