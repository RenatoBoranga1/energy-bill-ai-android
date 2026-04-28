package br.com.energybillai.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class AchievementBadgeState {
    LOCKED,
    IN_PROGRESS,
    UNLOCKED,
}

data class AchievementBadgeUiModel(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val state: AchievementBadgeState,
    val progressLabel: String? = null,
)

enum class WeeklyReadingMarkerState {
    COMPLETE,
    PENDING,
    MISSED,
    UPCOMING,
}

data class WeeklyReadingMarkerUiModel(
    val label: String,
    val state: WeeklyReadingMarkerState,
)

@Composable
fun AchievementBadge(
    model: AchievementBadgeUiModel,
    modifier: Modifier = Modifier,
) {
    val accentColor = when (model.state) {
        AchievementBadgeState.UNLOCKED -> MaterialTheme.colorScheme.secondary
        AchievementBadgeState.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        AchievementBadgeState.LOCKED -> MaterialTheme.colorScheme.outline
    }
    val containerColor = when (model.state) {
        AchievementBadgeState.UNLOCKED -> MaterialTheme.colorScheme.secondaryContainer
        AchievementBadgeState.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer
        AchievementBadgeState.LOCKED -> MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = model.icon,
                    contentDescription = null,
                    tint = accentColor,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.progressLabel?.let { progress ->
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                )
            }
        }
    }
}

@Composable
fun WeeklyReadingStreakCard(
    streakWeeks: Int,
    lastReadingLabel: String?,
    nextReadingLabel: String?,
    pendingReading: Boolean,
    markers: List<WeeklyReadingMarkerUiModel>,
    modifier: Modifier = Modifier,
) {
    AppCard(
        title = "Ritmo semanal",
        eyebrow = "Leitura do medidor",
        modifier = modifier,
        supporting = if (pendingReading) {
            "Sua proxima leitura ja esta perto. Registrar em dia ajuda a manter a sequencia e o desafio atualizados."
        } else {
            "Voce esta com as leituras em dia. Continue alimentando o jogo com dados reais do medidor."
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricChip(
                label = "Sequencia",
                value = "$streakWeeks semanas",
                modifier = Modifier.weight(1f),
                highlighted = streakWeeks > 0,
            )
            MetricChip(
                label = "Proxima leitura",
                value = nextReadingLabel ?: "Agora",
                modifier = Modifier.weight(1f),
                supporting = lastReadingLabel?.let { "Ultima em $it" } ?: "Ainda sem leitura inicial",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            markers.forEach { marker ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = marker.state.toMarkerColor(),
                                shape = CircleShape,
                            )
                            .border(
                                width = 1.dp,
                                color = marker.state.toMarkerBorderColor(),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = marker.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyReadingMarkerState.toMarkerColor(): Color {
    return when (this) {
        WeeklyReadingMarkerState.COMPLETE -> MaterialTheme.colorScheme.secondary
        WeeklyReadingMarkerState.PENDING -> MaterialTheme.colorScheme.primary
        WeeklyReadingMarkerState.MISSED -> MaterialTheme.colorScheme.error
        WeeklyReadingMarkerState.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun WeeklyReadingMarkerState.toMarkerBorderColor(): Color {
    return when (this) {
        WeeklyReadingMarkerState.COMPLETE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.36f)
        WeeklyReadingMarkerState.PENDING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
        WeeklyReadingMarkerState.MISSED -> MaterialTheme.colorScheme.error.copy(alpha = 0.36f)
        WeeklyReadingMarkerState.UPCOMING -> MaterialTheme.colorScheme.outlineVariant
    }
}
