package com.example.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class ProfileColor(
    val id: String,
    val color: Color,
    val onColor: Color
) {
    CYAN("cyan", Color(0xFF00F2EA), Color(0xFF000000)),
    PINK("pink", Color(0xFFFF0050), Color(0xFFFFFFFF)),
    EMERALD("emerald", Color(0xFF10B981), Color(0xFF000000)),
    INDIGO("indigo", Color(0xFF6366F1), Color(0xFFFFFFFF)),
    AMBER("amber", Color(0xFFF59E0B), Color(0xFF000000)),
    PURPLE("purple", Color(0xFFA855F7), Color(0xFFFFFFFF)),
    SKY("sky", Color(0xFF38BDF8), Color(0xFF002233)),
    CORAL("coral", Color(0xFFFB7185), Color(0xFF000000));

    companion object {
        fun fromId(id: String?): ProfileColor =
            entries.firstOrNull { it.id == id } ?: CYAN
    }
}

enum class ProfileIcon(
    val id: String,
    val imageVector: ImageVector
) {
    PERSON("person", Icons.Rounded.Person),
    VIDEOCAM("videocam", Icons.Rounded.Videocam),
    STORE("store", Icons.Rounded.Storefront),
    WORK("work", Icons.Rounded.Work),
    STAR("star", Icons.Rounded.Star),
    VERIFIED("verified", Icons.Rounded.Verified),
    BOLT("bolt", Icons.Rounded.Bolt),
    BRUSH("brush", Icons.Rounded.Brush);

    companion object {
        fun fromId(id: String?): ProfileIcon =
            entries.firstOrNull { it.id == id } ?: PERSON
    }
}

data class SavedTabState(
    val id: String,
    val url: String,
    val title: String,
    val isChildTab: Boolean = false,
    val parentTabId: String? = null
)

data class BrowserProfile(
    val id: String,
    val name: String,
    val webViewProfileName: String,
    val colorId: String = ProfileColor.CYAN.id,
    val iconId: String = ProfileIcon.PERSON.id,
    val settings: BrowserSettings = BrowserSettings(),
    val tabs: List<SavedTabState> = emptyList(),
    val activeTabId: String? = null,
    val createdAtMillis: Long = 0L
) {
    val profileColor: ProfileColor
        get() = ProfileColor.fromId(colorId)

    val profileIcon: ProfileIcon
        get() = ProfileIcon.fromId(iconId)
}

data class AppPreferencesState(
    val profiles: List<BrowserProfile> = emptyList(),
    val lastActiveProfileId: String? = null,
    val hasCompletedOnboarding: Boolean = false
)
