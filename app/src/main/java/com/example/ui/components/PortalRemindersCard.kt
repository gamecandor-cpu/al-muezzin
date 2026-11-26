package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberStar
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SilverText
import com.example.ui.theme.WarmHighlight
import com.example.util.AthkarAudioPlaybackState
import com.example.util.PortalReminder
import java.util.Locale

@Composable
fun PortalRemindersSection(
    reminders: List<PortalReminder>,
    athkarAudioState: AthkarAudioPlaybackState,
    onPlayReminder: (PortalReminder) -> Unit,
    onStopAudio: () -> Unit,
    onToggleActive: (String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reminders.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("portal_reminders_section"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NavyDeep.copy(alpha = 0.85f)),
        border = BorderStroke(
            1.dp,
            if (reminders.any { it.isActive }) AmberStar.copy(alpha = 0.65f) else NavyBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = AppIcons.Campaign,
                        contentDescription = null,
                        tint = AmberStar,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "التذكيرات الصوتية والنصية",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = IceWhite
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = CrimsonContainer,
                        border = BorderStroke(1.dp, CrimsonBright.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "${reminders.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarmHighlight,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            reminders.forEach { item ->
                val isThisPlaying = athkarAudioState.isPlaying && athkarAudioState.remoteId == item.id
                val scheduleLabel = when (item.deliveryMode) {
                    "daily" -> "يومياً ${item.scheduledTimeHHmm}"
                    "scheduled" -> "مجدول ${item.scheduledTimeHHmm}"
                    else -> "فوري"
                }
                val sizeLabel = if (item.audioTotalBytes > 0L) {
                    String.format(Locale.US, " (%.1f MB)", item.audioTotalBytes / (1024.0 * 1024.0))
                } else ""

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = NavyPrimary.copy(alpha = if (item.isActive) 0.80f else 0.42f),
                    border = BorderStroke(
                        1.dp,
                        if (isThisPlaying) AmberStar else if (item.isActive) NavyBorder else NavyBorder.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("portal_reminder_item_${item.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = IceWhite
                                )
                                if (item.body.isNotBlank()) {
                                    Text(
                                        text = item.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SilverText
                                    )
                                }
                                Text(
                                    text = buildString {
                                        append(scheduleLabel)
                                        if (item.hasPlayableAudio()) {
                                            append(" • 🔊 صوتي")
                                            append(sizeLabel)
                                        }
                                        if (item.persistenceMode == "pinned") {
                                            append(" • مثبت")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmHighlight
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onToggleActive(item.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.isActive) AppIcons.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (item.isActive) "إيقاف" else "تفعيل",
                                        tint = if (item.isActive) AmberStar else SilverText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteReminder(item.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.DeleteOutline,
                                        contentDescription = "حذف",
                                        tint = CrimsonBright,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (item.hasPlayableAudio()) {
                            Button(
                                onClick = {
                                    if (isThisPlaying) onStopAudio() else onPlayReminder(item)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isThisPlaying) CrimsonRed else NavyDeep
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) AppIcons.Stop else AppIcons.VolumeUp,
                                    contentDescription = null,
                                    tint = if (isThisPlaying) Color.White else AmberStar,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isThisPlaying) "إيقاف الصوت" else "تشغيل الصوت",
                                    color = IceWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
