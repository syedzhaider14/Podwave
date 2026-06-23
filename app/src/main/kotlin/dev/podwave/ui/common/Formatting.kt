package dev.podwave.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import dev.podwave.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** `1:02:03` or `2:03`, for scrubber labels. Negative input is treated as zero. */
fun formatClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    val mmss = "${minutes.pad()}:${seconds.pad()}"
    return if (hours > 0) "$hours:$mmss" else "$minutes:${seconds.pad()}"
}

private fun Long.pad() = toString().padStart(2, '0')

/** `1×`, `1.5×`, `1.75×`: no trailing zeros. */
fun formatSpeed(speed: Float): String {
    val rounded = Math.round(speed * 100) / 100.0
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().trimEnd('0')
    return "$text×"
}

/** Hours and minutes for "1 h 5 min" style labels. Anything under a minute rounds up to one. */
data class DurationParts(val hours: Int, val minutes: Int)

fun durationParts(ms: Long): DurationParts {
    val totalMinutes = ((ms.coerceAtLeast(0) + 59_999) / 60_000).toInt().coerceAtLeast(1)
    return DurationParts(hours = totalMinutes / 60, minutes = totalMinutes % 60)
}

/** How long ago an episode came out, decided in the listener's time zone. */
sealed interface PublishedLabel {
    data object Today : PublishedLabel

    data object Yesterday : PublishedLabel

    data class DaysAgo(val days: Int) : PublishedLabel

    data class OnDate(val date: LocalDate, val showYear: Boolean) : PublishedLabel
}

fun publishedLabel(published: Instant, now: Instant, zone: ZoneId): PublishedLabel {
    val today = now.atZone(zone).toLocalDate()
    val date = published.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        // Feeds sometimes carry slightly-future timestamps; treat them as today.
        days <= 0 -> PublishedLabel.Today
        days == 1L -> PublishedLabel.Yesterday
        days < 7 -> PublishedLabel.DaysAgo(days.toInt())
        else -> PublishedLabel.OnDate(date, showYear = date.year != today.year)
    }
}

@Composable
@ReadOnlyComposable
fun durationText(ms: Long): String {
    val parts = durationParts(ms)
    return when {
        parts.hours == 0 -> stringResource(R.string.duration_minutes, parts.minutes)
        parts.minutes == 0 -> stringResource(R.string.duration_hours, parts.hours)
        else -> stringResource(R.string.duration_hours_minutes, parts.hours, parts.minutes)
    }
}

@Composable
@ReadOnlyComposable
fun publishedText(published: Instant, now: Instant = Instant.now()): String =
    when (val label = publishedLabel(published, now, ZoneId.systemDefault())) {
        PublishedLabel.Today -> stringResource(R.string.published_today)
        PublishedLabel.Yesterday -> stringResource(R.string.published_yesterday)
        is PublishedLabel.DaysAgo -> pluralStringResource(R.plurals.published_days_ago, label.days, label.days)
        is PublishedLabel.OnDate -> {
            val locale = currentLocale()
            val skeleton = if (label.showYear) "dMMMyyyy" else "dMMM"
            label.date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
        }
    }

@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
