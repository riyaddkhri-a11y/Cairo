package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.UrlUtils
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NotoSansArabicFontFamily
import com.example.ui.theme.SecureEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun BrowserToolbar(
    currentUrl: String,
    progress: Int,
    isLoading: Boolean,
    desktopModeEnabled: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    isWideLayout: Boolean,
    onNavigateUrl: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefreshOrStop: () -> Unit,
    onHome: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(TextFieldValue(currentUrl)) }

    LaunchedEffect(currentUrl, isFocused) {
        if (!isFocused && textValue.text != currentUrl) {
            textValue = TextFieldValue(currentUrl)
        }
    }

    val isSecure = UrlUtils.isSecureUrl(currentUrl)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isWideLayout) {
                    IconButton(
                        onClick = onBack,
                        enabled = canGoBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolbar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                    IconButton(
                        onClick = onForward,
                        enabled = canGoForward,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolbar_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = stringResource(R.string.cd_forward)
                        )
                    }
                    IconButton(
                        onClick = onHome,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolbar_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = stringResource(R.string.cd_home)
                        )
                    }
                }

                // Address Bar Pill
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSecure) Icons.Rounded.Lock else Icons.Rounded.Public,
                            contentDescription = stringResource(
                                if (isSecure) R.string.cd_security_https else R.string.cd_security_http
                            ),
                            tint = if (isSecure) SecureEmerald else WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (textValue.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.address_bar_placeholder),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = NotoSansArabicFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = textValue,
                                onValueChange = { textValue = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textDirection = TextDirection.Ltr
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        focusManager.clearFocus()
                                        onNavigateUrl(textValue.text)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("address_bar_input")
                                    .onFocusChanged { focusState ->
                                        val nowFocused = focusState.isFocused
                                        if (nowFocused && !isFocused) {
                                            textValue = textValue.copy(
                                                selection = TextRange(0, textValue.text.length)
                                            )
                                        }
                                        isFocused = nowFocused
                                    }
                            )
                        }

                        if (isFocused && textValue.text.isNotEmpty()) {
                            IconButton(
                                onClick = { textValue = TextFieldValue("") },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("clear_address_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_url),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                            ModeStatusBadge(
                                desktopModeEnabled = desktopModeEnabled,
                                onClick = onToggleDesktopMode
                            )
                        }
                    }
                }

                // Refresh / Stop Button
                IconButton(
                    onClick = onRefreshOrStop,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_button")
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                        contentDescription = stringResource(
                            if (isLoading) R.string.cd_stop_loading else R.string.cd_refresh
                        ),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isWideLayout) {
                    TabCountButton(
                        tabCount = tabCount,
                        onClick = onOpenTabs,
                        modifier = Modifier.testTag("toolbar_tabs_button")
                    )
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                }
            }

            // Loading progress indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val loadingDescription = stringResource(R.string.loading_status)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoading && progress in 1..99,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        progress = { (progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .semantics { contentDescription = loadingDescription }
                            .testTag("loading_progress_bar"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            )
        }
    }
}

@Composable
private fun ModeStatusBadge(
    desktopModeEnabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (desktopModeEnabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (desktopModeEnabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("mode_badge_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (desktopModeEnabled) {
                Icons.Rounded.DesktopWindows
            } else {
                Icons.Rounded.Smartphone
            },
            contentDescription = stringResource(
                if (desktopModeEnabled) R.string.cd_desktop_badge else R.string.cd_mobile_badge
            ),
            tint = contentColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = stringResource(
                if (desktopModeEnabled) R.string.badge_desktop else R.string.badge_mobile
            ),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = NotoSansArabicFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = contentColor
        )
    }
}
