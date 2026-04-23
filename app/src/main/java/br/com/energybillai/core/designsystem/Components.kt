package br.com.energybillai.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.energybillai.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyScreen(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text(text = title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                if (showBack && onBack != null) {
                    TextButton(onClick = onBack) {
                        Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "Voltar")
                    }
                }
            },
            actions = {
                actions?.invoke()
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun HeroCard(
    eyebrow: String,
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ),
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = eyebrow, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Text(text = title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(text = supporting, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun AppBrandLockup(
    title: String = "Energy Bill AI",
    subtitle: String = "Leitura inteligente de contas de energia",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                        shape = RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_brand_mark),
                    contentDescription = "Logo do app",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppCard(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (!supporting.isNullOrBlank()) {
                Text(text = supporting, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            content()
        }
    }
}

@Composable
fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun InlineWarning(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = text, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    supporting: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = { Text(text = label) },
            singleLine = singleLine,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            shape = RoundedCornerShape(18.dp),
        )
        if (!supporting.isNullOrBlank()) {
            Text(text = supporting, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(18.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text)
        }
    }
}

@Composable
fun EmptyStatePane(
    title: String,
    message: String,
) {
    AppCard(title = title, supporting = message) {}
}

@Composable
fun ErrorStatePane(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    AppCard(title = title, supporting = message) {
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(text = "Tentar novamente")
            }
        }
    }
}

@Composable
fun LoadingPane(
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
) {
    AssistChip(
        onClick = {},
        label = { Text(text = text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.16f),
            labelColor = color,
        ),
    )
}

@Composable
fun SimpleLineChart(
    points: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.isEmpty()) return
    val chartLineColor = lineColor
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val maxValue = points.maxOrNull() ?: 0.0
        val minValue = points.minOrNull() ?: 0.0
        val range = (maxValue - minValue).takeIf { it > 0 } ?: 1.0
        val horizontalStep = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = index * horizontalStep
            val normalized = ((value - minValue) / range).toFloat()
            val y = size.height - (normalized * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = chartLineColor,
            style = Stroke(width = 8f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun ForecastBandChart(
    predictions: List<Triple<Double, Double, Double>>,
    modifier: Modifier = Modifier,
) {
    if (predictions.isEmpty()) return
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val upper = predictions.maxOf { it.third }
        val lower = predictions.minOf { it.second }
        val range = (upper - lower).takeIf { it > 0 } ?: 1.0
        val step = size.width / (predictions.size - 1).coerceAtLeast(1)

        val envelope = Path()
        predictions.forEachIndexed { index, value ->
            val x = index * step
            val normalized = ((value.third - lower) / range).toFloat()
            val y = size.height - (normalized * size.height)
            if (index == 0) envelope.moveTo(x, y) else envelope.lineTo(x, y)
        }
        predictions.asReversed().forEachIndexed { reverseIndex, value ->
            val index = predictions.lastIndex - reverseIndex
            val x = index * step
            val normalized = ((value.second - lower) / range).toFloat()
            val y = size.height - (normalized * size.height)
            envelope.lineTo(x, y)
        }
        envelope.close()
        drawPath(
            path = envelope,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.28f),
                    primaryColor.copy(alpha = 0.08f),
                ),
            ),
        )

        predictions.forEachIndexed { index, value ->
            val x = index * step
            val normalized = ((value.first - lower) / range).toFloat()
            val y = size.height - (normalized * size.height)
            drawCircle(
                color = primaryColor,
                radius = 6f,
                center = Offset(x, y),
            )
            if (index < predictions.lastIndex) {
                val next = predictions[index + 1]
                val nextX = (index + 1) * step
                val nextNormalized = ((next.first - lower) / range).toFloat()
                val nextY = size.height - (nextNormalized * size.height)
                drawLine(
                    color = primaryColor,
                    start = Offset(x, y),
                    end = Offset(nextX, nextY),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
