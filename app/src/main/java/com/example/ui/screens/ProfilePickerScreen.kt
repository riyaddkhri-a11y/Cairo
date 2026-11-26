package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.BrowserProfile
import com.example.ui.components.ProfileAvatar
import com.example.ui.theme.NotoSansArabicFontFamily
import com.example.ui.theme.SecureEmerald
import com.example.ui.theme.TokStudioBlack
import com.example.ui.theme.TokStudioCyan
import com.example.ui.theme.TokStudioPink

private val PickerObsidian = Color(0xFF0A0D14)
private val PickerCardSurface = Color(0xFF111622)
private val PickerTextPrimary = Color(0xFFF8FAFC)
private val PickerTextSecondary = Color(0xFFCBD5E1)

@Composable
fun ProfilePickerScreen(
    profiles: List<BrowserProfile>,
    lastActiveProfileId: String?,
    loginStatusByProfile: Map<String, Boolean>,
    tabCountByProfile: Map<String, Int>,
    isMultiProfileIsolated: Boolean,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (String) -> Unit,
    onManageProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(TokStudioBlack, PickerObsidian, TokStudioBlack)
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(TokStudioCyan.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.16f, size.height * 0.14f),
                        radius = size.minDimension * 0.6f
                    ),
                    radius = size.minDimension * 0.6f,
                    center = Offset(size.width * 0.16f, size.height * 0.14f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(TokStudioPink.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.85f),
                        radius = size.minDimension * 0.65f
                    ),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width * 0.86f, size.height * 0.85f)
                )
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TokStudioBlack)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(listOf(TokStudioCyan, TokStudioPink)),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(3.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_tok_studio_1795690311352),
                        contentDescription = stringResource(R.string.welcome_icon_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(11.dp))
                    )
                }

                Text(
                    text = stringResource(R.string.profile_picker_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = NotoSansArabicFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = PickerTextPrimary,
                    modifier = Modifier.testTag("profile_picker_title")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.profile_picker_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = NotoSansArabicFontFamily
                ),
                color = PickerTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isMultiProfileIsolated) {
                    SecureEmerald.copy(alpha = 0.14f)
                } else {
                    TokStudioPink.copy(alpha = 0.14f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isMultiProfileIsolated) {
                        SecureEmerald.copy(alpha = 0.4f)
                    } else {
                        TokStudioPink.copy(alpha = 0.4f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = if (isMultiProfileIsolated) SecureEmerald else TokStudioPink,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            if (isMultiProfileIsolated) {
                                R.string.profile_isolation_active
                            } else {
                                R.string.profile_isolation_limited
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = PickerTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = PickerCardSurface.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonOutline,
                                contentDescription = null,
                                tint = TokStudioCyan,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = stringResource(R.string.profile_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = PickerTextPrimary
                            )
                            Text(
                                text = stringResource(R.string.profile_empty_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PickerTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 152.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val isLastUsed = profile.id == lastActiveProfileId
                        val isLoggedIn = loginStatusByProfile[profile.id] == true
                        val tabCount = (tabCountByProfile[profile.id] ?: profile.tabs.size)
                            .coerceAtLeast(1)

                        ProfileCardTile(
                            profile = profile,
                            isLastUsed = isLastUsed,
                            isLoggedIn = isLoggedIn,
                            tabCount = tabCount,
                            onClick = { onSelectProfile(profile.id) },
                            onEditClick = { onEditProfile(profile.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAddProfile,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TokStudioCyan,
                        contentColor = TokStudioBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("picker_add_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_add_new),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                OutlinedButton(
                    onClick = onManageProfiles,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PickerTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("picker_manage_profiles_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ManageAccounts,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.profile_manage_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCardTile(
    profile: BrowserProfile,
    isLastUsed: Boolean,
    isLoggedIn: Boolean,
    tabCount: Int,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("profile_card_${profile.id}"),
        shape = RoundedCornerShape(22.dp),
        color = PickerCardSurface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = if (isLastUsed) 1.6.dp else 1.dp,
            color = if (isLastUsed) {
                profile.profileColor.color
            } else {
                Color.White.copy(alpha = 0.14f)
            }
        )
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .minimumInteractiveComponentSize()
                    .size(32.dp)
                    .clip(CircleShape)
                    .testTag("edit_profile_tile_${profile.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.profile_edit_action),
                    tint = PickerTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileAvatar(
                    profileColor = profile.profileColor,
                    profileIcon = profile.profileIcon,
                    size = 64.dp,
                    iconSize = 32.dp
                )

                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = PickerTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isLoggedIn) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.PersonOutline
                        },
                        contentDescription = null,
                        tint = if (isLoggedIn) SecureEmerald else PickerTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(
                            if (isLoggedIn) {
                                R.string.settings_tiktok_logged_in
                            } else {
                                R.string.settings_tiktok_logged_out
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLoggedIn) SecureEmerald else PickerTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = stringResource(R.string.profile_tabs_count, tabCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = PickerTextSecondary.copy(alpha = 0.8f)
                )
            }
        }
    }
}
