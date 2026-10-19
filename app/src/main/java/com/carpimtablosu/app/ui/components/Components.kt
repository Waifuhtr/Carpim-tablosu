package com.carpimtablosu.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.carpimtablosu.app.R
import com.carpimtablosu.app.logic.BadgeIcon
import com.carpimtablosu.app.ui.theme.Palette

/**
 * Duolingo tarzı "basılabilir" düğme: altında koyu bir gölge katmanı vardır,
 * basınca yüzey aşağı iner.
 */
@Composable
fun PressableSurface(
    onClick: () -> Unit,
    color: Color,
    shadow: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    depth: Dp = 4.dp,
    border: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop = if (pressed && enabled) depth else 0.dp
    Box(modifier.padding(bottom = depth)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = depth)
                .clip(shape)
                .background(shadow)
        )
        Box(
            Modifier
                .offset(y = drop)
                .fillMaxWidth()
                .clip(shape)
                .background(color)
                .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

enum class ButtonStyle { PRIMARY, SUCCESS, DANGER, SECONDARY, INFO, DISABLED }

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    val s = if (enabled) style else ButtonStyle.DISABLED
    val (bg, sh, fg) = when (s) {
        ButtonStyle.PRIMARY -> Triple(Palette.Amber, Palette.AmberDark, Color(0xFF231900))
        ButtonStyle.SUCCESS -> Triple(Palette.Green, Palette.GreenDark, Color(0xFF00210F))
        ButtonStyle.DANGER -> Triple(Palette.Red, Palette.RedDark, Color.White)
        ButtonStyle.INFO -> Triple(Palette.Blue, Palette.BlueDark, Color(0xFF001E33))
        ButtonStyle.SECONDARY -> Triple(Palette.Surface, Palette.Outline, Palette.Text)
        ButtonStyle.DISABLED -> Triple(Palette.Locked, Palette.LockedDark, Palette.TextDim)
    }
    PressableSurface(
        onClick = onClick,
        color = bg,
        shadow = sh,
        enabled = enabled,
        border = if (s == ButtonStyle.SECONDARY) Palette.Outline else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text.uppercase(java.util.Locale.forLanguageTag("tr")), color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Glyph(icon: BadgeIcon, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(badgeRes(icon)),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

@DrawableRes
fun badgeRes(icon: BadgeIcon): Int = when (icon) {
    BadgeIcon.STAR -> R.drawable.ic_star
    BadgeIcon.FLAME -> R.drawable.ic_flame
    BadgeIcon.TROPHY -> R.drawable.ic_trophy
    BadgeIcon.TIMER -> R.drawable.ic_timer
    BadgeIcon.BULB -> R.drawable.ic_bulb
    BadgeIcon.BOOK -> R.drawable.ic_book
    BadgeIcon.CHECK -> R.drawable.ic_check
    BadgeIcon.LOCK -> R.drawable.ic_lock
}

enum class Mood(@DrawableRes val res: Int) {
    HAPPY(R.drawable.mascot_happy),
    THINKING(R.drawable.mascot_thinking),
    ENCOURAGE(R.drawable.mascot_encourage),
    SAD(R.drawable.mascot_sad),
    LOCKED(R.drawable.mascot_locked),
}

@Composable
fun Mascot(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(mood.res), contentDescription = null, modifier = modifier.size(size))
}

/** Maskotun yanında konuşma balonu. */
@Composable
fun SpeechBubble(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface)
            .border(2.dp, Palette.Outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) { content() }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface)
            .border(2.dp, Palette.Outline, RoundedCornerShape(18.dp))
            .padding(padding)
    ) { content() }
}

@Composable
fun StatChip(icon: BadgeIcon, text: String, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, tint, 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = tint, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Palette.Locked)
    ) {
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .height(height)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            ) {
                // Duolingo'daki parlak şerit
                Box(
                    Modifier
                        .padding(start = 8.dp, end = 8.dp, top = 3.dp)
                        .fillMaxWidth()
                        .height(height / 4)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Palette.Text,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Palette.Green,
                uncheckedThumbColor = Palette.TextDim,
                uncheckedTrackColor = Palette.Locked,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
}

/** Seçenek çipleri (ör. günlük hedef). */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Palette.Blue.copy(alpha = 0.18f) else Palette.Surface)
                    .border(2.dp, if (on) Palette.Blue else Palette.Outline, RoundedCornerShape(12.dp))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) Palette.Blue else Palette.Text,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Sol tarafta ikon kutusu olan liste satırı. */
@Composable
fun NavRow(icon: BadgeIcon, tint: Color, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(icon, tint, 24.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = Palette.TextDim)
    }
}
