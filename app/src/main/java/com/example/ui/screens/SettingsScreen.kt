package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BrowserConstants
import com.example.data.BrowserSettings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SecureEmerald
import com.example.ui.theme.TokStudioBlack
import com.example.ui.theme.TokStudioCyan
import com.example.ui.theme.TokStudioPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    isTikTokLoggedIn: Boolean = false,
    onNavigateBack: () -> Unit,
    onOpenTikTokLogin: () -> Unit = {},
    onLogoutTikTok: () -> Unit = {},
    onSaveHomepage: (String) -> Boolean,
    onResetHomepage: () -> String,
    onToggleDesktopMode: (Boolean) -> Unit,
    onToggleJavaScript: (Boolean) -> Unit,
    onWebsiteZoomChange: (Float) -> Unit,
    onResetWebsiteZoom: () -> Unit,
    onClearBrowsingData: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val focusManager = LocalFocusManager.current
    var homepageDraft by remember { mutableStateOf(settings.homepageUrl) }
    var showInvalidError by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showTikTokLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(settings.homepageUrl) {
        homepageDraft = settings.homepageUrl
    }

    val activeUserAgent = if (settings.desktopModeEnabled) {
        BrowserConstants.DESKTOP_USER_AGENT
    } else {
        BrowserConstants.MOBILE_USER_AGENT
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section 0: TikTok Account Login & Session Status
                TikTokAccountCard(
                    isLoggedIn = isTikTokLoggedIn,
                    onLoginClick = onOpenTikTokLogin,
                    onLogoutClick = { showTikTokLogoutDialog = true }
                )

                // Section 1: Startup & Homepage
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_startup),
                    icon = Icons.Rounded.Home
                ) {
                    OutlinedTextField(
                        value = homepageDraft,
                        onValueChange = {
                            homepageDraft = it
                            if (showInvalidError) showInvalidError = false
                        },
                        label = { Text(stringResource(R.string.settings_homepage_label)) },
                        placeholder = { Text(stringResource(R.string.settings_homepage_hint)) },
                        supportingText = {
                            Text(
                                text = if (showInvalidError) {
                                    stringResource(R.string.settings_homepage_invalid)
                                } else {
                                    stringResource(R.string.settings_homepage_helper)
                                }
                            )
                        },
                        isError = showInvalidError,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            textDirection = TextDirection.Ltr
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                showInvalidError = !onSaveHomepage(homepageDraft)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("homepage_url_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                showInvalidError = !onSaveHomepage(homepageDraft)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_homepage_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_save_homepage))
                        }

                        TextButton(
                            onClick = {
                                focusManager.clearFocus()
                                showInvalidError = false
                                homepageDraft = onResetHomepage()
                            },
                            modifier = Modifier.testTag("reset_homepage_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.settings_reset_default))
                        }
                    }
                }

                // Section 2: Rendering & Engine
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_rendering),
                    icon = Icons.Rounded.DesktopWindows
                ) {
                    SettingsToggleRow(
                        icon = Icons.Rounded.DesktopWindows,
                        title = stringResource(R.string.settings_desktop_mode_title),
                        description = stringResource(R.string.settings_desktop_mode_desc),
                        checked = settings.desktopModeEnabled,
                        onCheckedChange = onToggleDesktopMode,
                        testTag = "desktop_mode_switch"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )

                    WebsiteZoomControlRow(
                        zoomPercent = settings.websiteZoomPercent,
                        onZoomChange = onWebsiteZoomChange,
                        onResetZoom = onResetWebsiteZoom
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )

                    SettingsToggleRow(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.settings_javascript_title),
                        description = stringResource(R.string.settings_javascript_desc),
                        checked = settings.javaScriptEnabled,
                        onCheckedChange = onToggleJavaScript,
                        testTag = "javascript_switch"
                    )
                }

                // Section 3: Privacy & Session Data
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_privacy),
                    icon = Icons.Rounded.DeleteSweep
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearDataDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("clear_browsing_data_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_clear_data_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_clear_data_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.settings_clear_data_title),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Section 4: Engine Profile (Active User-Agent)
                SettingsSectionCard(
                    title = stringResource(R.string.settings_section_info),
                    icon = Icons.Rounded.Terminal
                ) {
                    Text(
                        text = stringResource(R.string.settings_active_ua_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = activeUserAgent,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                textDirection = TextDirection.Ltr
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("active_user_agent_text")
                        )
                    }
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text(stringResource(R.string.settings_clear_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_data_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        onClearBrowsingData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_data_button")
                ) {
                    Text(stringResource(R.string.settings_clear_data_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDataDialog = false },
                    modifier = Modifier.testTag("cancel_clear_data_button")
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showTikTokLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showTikTokLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_tiktok_logout_confirm_title)) },
            text = { Text(stringResource(R.string.settings_tiktok_logout_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showTikTokLogoutDialog = false
                        onLogoutTikTok()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokStudioPink,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_tiktok_logout_button")
                ) {
                    Text(stringResource(R.string.settings_tiktok_logout_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTikTokLogoutDialog = false },
                    modifier = Modifier.testTag("cancel_tiktok_logout_button")
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
private fun TikTokAccountCard(
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    TokStudioCyan.copy(alpha = 0.55f),
                    TokStudioPink.copy(alpha = 0.55f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_section_tiktok_account),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isLoggedIn) {
                        SecureEmerald.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isLoggedIn) {
                            SecureEmerald.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoggedIn) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.PersonOutline
                            },
                            contentDescription = stringResource(R.string.settings_tiktok_status_label),
                            tint = if (isLoggedIn) {
                                SecureEmerald
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(
                                if (isLoggedIn) {
                                    R.string.settings_tiktok_logged_in
                                } else {
                                    R.string.settings_tiktok_logged_out
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (isLoggedIn) {
                                SecureEmerald
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.testTag("tiktok_account_status_text")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.settings_tiktok_login_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLoginClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TokStudioCyan,
                    contentColor = TokStudioBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .height(48.dp)
                    .testTag("tiktok_login_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_tiktok_login_button),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (isLoggedIn) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onLogoutClick,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TokStudioPink.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TokStudioPink
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .height(48.dp)
                        .testTag("tiktok_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_tiktok_logout_button),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun WebsiteZoomControlRow(
    zoomPercent: Int,
    onZoomChange: (Float) -> Unit,
    onResetZoom: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ZoomIn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_zoom_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_zoom_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = zoomPercent.toFloat(),
                onValueChange = onZoomChange,
                valueRange = BrowserConstants.MIN_ZOOM_PERCENT.toFloat()..BrowserConstants.MAX_ZOOM_PERCENT.toFloat(),
                steps = 14,
                modifier = Modifier
                    .weight(1f)
                    .testTag("website_zoom_slider")
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = stringResource(R.string.settings_zoom_percent_format, zoomPercent),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        textDirection = TextDirection.Ltr
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("website_zoom_value_text")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onResetZoom,
                enabled = zoomPercent != BrowserConstants.DEFAULT_ZOOM_PERCENT,
                modifier = Modifier.testTag("reset_website_zoom_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_zoom_reset))
            }
        }
    }
}
