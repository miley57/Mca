package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.IridescentMint
import com.example.ui.theme.LiquidCyan

/**
 * Configuration specification for the Apple VisionOS / iOS inspired Liquid Glass surface.
 */
@Immutable
data class LiquidGlassSpec(
    val cornerRadius: Dp = 24.dp,
    val accentTint: Color = LiquidCyan,
    val frostAlpha: Float = 0.78f,
    val specularIntensity: Float = 0.42f,
    val borderWidth: Dp = 1.dp,
    val ambientBloomAlpha: Float = 0.16f
)

/**
 * Custom translucent background modifier for the 'Liquid Glass' design system.
 *
 * Renders:
 * 1. Frosted multi-stop translucent glass base layer (adapting to dark and light themes)
 * 2. Radial ambient color bloom anchored near the top-left light source
 * 3. Diagonal VisionOS specular light sheen across the glass surface
 * 4. Dual-tone refractive rim border (illuminated top-left edge, subtle bottom-right edge)
 */
fun Modifier.liquidGlassBackground(
    spec: LiquidGlassSpec = LiquidGlassSpec(),
    isDarkTheme: Boolean? = null
): Modifier = composed {
    val resolvedDark = isDarkTheme ?: (MaterialTheme.colorScheme.background.red < 0.2f)
    val shape = RoundedCornerShape(spec.cornerRadius)

    this
        .clip(shape)
        .drawWithCache {
            val radiusPx = spec.cornerRadius.toPx()
            val strokePx = spec.borderWidth.toPx()
            val cornerRadius = CornerRadius(radiusPx, radiusPx)

            // 1. Base frosted glass gradient
            val baseColors = if (resolvedDark) {
                listOf(
                    Color(0xFF1A243D).copy(alpha = (spec.frostAlpha + 0.08f).coerceIn(0f, 0.96f)),
                    Color(0xFF0E1526).copy(alpha = spec.frostAlpha.coerceIn(0f, 0.94f))
                )
            } else {
                listOf(
                    Color(0xFFFFFFFF).copy(alpha = (spec.frostAlpha + 0.12f).coerceIn(0f, 0.98f)),
                    Color(0xFFEDF3FC).copy(alpha = spec.frostAlpha.coerceIn(0f, 0.94f))
                )
            }
            val baseBrush = Brush.linearGradient(
                colors = baseColors,
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )

            // 2. Ambient top-left light refraction bloom
            val bloomRadius = size.maxDimension.coerceAtLeast(1f) * 0.85f
            val bloomBrush = Brush.radialGradient(
                colors = listOf(
                    spec.accentTint.copy(alpha = spec.ambientBloomAlpha.coerceIn(0f, 0.6f)),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.18f, 0f),
                radius = bloomRadius
            )

            // 3. Diagonal VisionOS specular highlight sheen
            val sheenAlpha = if (resolvedDark) {
                (spec.specularIntensity * 0.22f).coerceIn(0f, 0.35f)
            } else {
                (spec.specularIntensity * 0.55f).coerceIn(0f, 0.65f)
            }
            val sheenBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = sheenAlpha),
                    Color.White.copy(alpha = sheenAlpha * 0.15f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.7f, size.height * 0.65f)
            )

            // 4. Dual-tone refractive rim border
            val rimBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = (spec.specularIntensity * 0.9f).coerceIn(0.15f, 0.85f)),
                    spec.accentTint.copy(alpha = (spec.specularIntensity * 0.85f).coerceIn(0.15f, 0.75f)),
                    Color.White.copy(alpha = 0.08f),
                    spec.accentTint.copy(alpha = 0.18f)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )

            onDrawBehind {
                drawRoundRect(
                    brush = baseBrush,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    brush = bloomBrush,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    brush = sheenBrush,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    brush = rimBrush,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = strokePx)
                )
            }
        }
}

/**
 * Full-screen ambient backdrop modifier that paints subtle glowing light orbs behind
 * translucent Liquid Glass cards for true layered depth.
 */
fun Modifier.liquidGlassCanvasBackdrop(
    isDarkTheme: Boolean = true
): Modifier = this.drawWithCache {
    val bgBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF060911),
                Color(0xFF0C1426),
                Color(0xFF070A12)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF4F7FD),
                Color(0xFFE7EFFA),
                Color(0xFFF2F6FC)
            )
        )
    }

    val cyanOrb = Brush.radialGradient(
        colors = listOf(
            LiquidCyan.copy(alpha = if (isDarkTheme) 0.14f else 0.10f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.85f, size.height * 0.12f),
        radius = (size.minDimension * 0.75f).coerceAtLeast(1f)
    )

    val violetOrb = Brush.radialGradient(
        colors = listOf(
            ElectricViolet.copy(alpha = if (isDarkTheme) 0.13f else 0.08f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.12f, size.height * 0.55f),
        radius = (size.minDimension * 0.8f).coerceAtLeast(1f)
    )

    val mintOrb = Brush.radialGradient(
        colors = listOf(
            IridescentMint.copy(alpha = if (isDarkTheme) 0.08f else 0.06f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.75f, size.height * 0.88f),
        radius = (size.minDimension * 0.65f).coerceAtLeast(1f)
    )

    onDrawBehind {
        drawRect(brush = bgBrush)
        drawRect(brush = cyanOrb)
        drawRect(brush = violetOrb)
        drawRect(brush = mintOrb)
    }
}

/**
 * Glassmorphic Pill Button with tactile spring scale animation and refractive border.
 */
@Composable
fun LiquidGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = LiquidCyan
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "glass_btn_scale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .liquidGlassBackground(
                spec = LiquidGlassSpec(
                    cornerRadius = 18.dp,
                    accentTint = accentColor,
                    frostAlpha = 0.82f,
                    specularIntensity = 0.65f,
                    ambientBloomAlpha = 0.28f
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Circular Frosted Glass Icon Button with VisionOS specular highlight.
 */
@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LiquidCyan
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .liquidGlassBackground(
                spec = LiquidGlassSpec(
                    cornerRadius = 22.dp,
                    accentTint = accentColor,
                    frostAlpha = 0.76f,
                    specularIntensity = 0.55f,
                    ambientBloomAlpha = 0.22f
                )
            )
            .clickable(
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Floating VisionOS-style translucent bar container for navigation or quick actions.
 */
@Composable
fun LiquidGlassFloatingBar(
    modifier: Modifier = Modifier,
    accentColor: Color = LiquidCyan,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .liquidGlassBackground(
                spec = LiquidGlassSpec(
                    cornerRadius = 28.dp,
                    accentTint = accentColor,
                    frostAlpha = 0.86f,
                    specularIntensity = 0.55f,
                    ambientBloomAlpha = 0.20f
                )
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

/**
 * Interactive showcase dialog allowing users to inspect and tune the Liquid Glass
 * base component library (translucent modifiers, refraction intensity, frost opacity, and cards).
 */
@Composable
fun LiquidGlassShowcaseDialog(
    onDismiss: () -> Unit
) {
    var frostAlpha by remember { mutableFloatStateOf(0.78f) }
    var specularIntensity by remember { mutableFloatStateOf(0.48f) }
    var bloomAlpha by remember { mutableFloatStateOf(0.22f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Layers, contentDescription = null, tint = LiquidCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Liquid Glass Design System")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Live interactive preview of the custom Modifier.liquidGlassBackground(...) and glassmorphic Card components.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Tunable Glassmorphic Card Preview
                LiquidGlassCard(
                    accentGlow = LiquidCyan,
                    frostAlpha = frostAlpha,
                    specularIntensity = specularIntensity,
                    ambientBloomAlpha = bloomAlpha,
                    header = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = LiquidCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refractive Glass Card", style = MaterialTheme.typography.titleMedium)
                            }
                            PriorityBadge("HIGH")
                        }
                    }
                ) {
                    Text(
                        text = "This card uses the custom drawWithCache translucent modifier with dual-tone specular borders, radial top-left light bloom, and diagonal sheen.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassButton(
                            text = "Cyan Action",
                            icon = Icons.Default.Bolt,
                            accentColor = LiquidCyan,
                            onClick = {}
                        )
                        LiquidGlassButton(
                            text = "Violet Action",
                            icon = Icons.Default.Tune,
                            accentColor = ElectricViolet,
                            onClick = {}
                        )
                    }
                }

                // Interactive Optical Controls
                Text("Frost Opacity (${(frostAlpha * 100).toInt()}%)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = frostAlpha,
                    onValueChange = { frostAlpha = it },
                    valueRange = 0.35f..0.95f
                )

                Text("Specular Rim & Sheen (${(specularIntensity * 100).toInt()}%)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = specularIntensity,
                    onValueChange = { specularIntensity = it },
                    valueRange = 0.1f..0.95f
                )

                Text("Ambient Light Bloom (${(bloomAlpha * 100).toInt()}%)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = bloomAlpha,
                    onValueChange = { bloomAlpha = it },
                    valueRange = 0.05f..0.5f
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close Preview")
            }
        }
    )
}
