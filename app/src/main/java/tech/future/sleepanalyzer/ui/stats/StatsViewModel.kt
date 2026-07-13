package tech.future.sleepanalyzer.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import tech.future.sleepanalyzer.data.db.entity.SleepSession
import tech.future.sleepanalyzer.data.repository.SleepRepository
import tech.future.sleepanalyzer.wearables.WearableMetric
import java.text.SimpleDateFormat
import java.util.*

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SleepRepository(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _timeRange = MutableStateFlow("week") // "day", "week", "month", "all"
    val timeRange: StateFlow<String> = _timeRange

    fun setTimeRange(range: String) { _timeRange.value = range }

    // All sessions, filtered/bucketed in-memory so the selected range actually drives every stat.
    private val allSessions: Flow<List<SleepSession>> = repository.getAllSessions()

    // Inclusive lower bound (epoch millis) for the selected range. "all" returns MIN so nothing is
    // dropped; the other ranges look back a fixed window that matches the chip's bucket granularity.
    private fun rangeStartMillis(range: String): Long {
        if (range == "all") return Long.MIN_VALUE
        val cal = Calendar.getInstance()
        when (range) {
            "day" -> cal.add(Calendar.DAY_OF_YEAR, -7)     // last 7 days
            "week" -> cal.add(Calendar.DAY_OF_YEAR, -56)    // last ~8 weeks
            "month" -> cal.add(Calendar.MONTH, -12)         // last ~12 months
            else -> cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        return cal.timeInMillis
    }

    // Completed sessions that fall inside the selected range. In-progress (tracking) rows are
    // excluded so an active 0-duration night doesn't skew the aggregates.
    private fun filterForRange(all: List<SleepSession>, range: String): List<SleepSession> {
        val start = rangeStartMillis(range)
        return all.filter { !it.isTracking && it.startTime >= start }
    }

    // Real elapsed time from start/end timestamps, falling back to the stored minute count only
    // when endTime is missing. Keeps sub-minute precision that durationMinutes throws away.
    private fun SleepSession.elapsedMs(): Long =
        ((endTime ?: (startTime + durationMinutes * 60_000L)) - startTime).coerceAtLeast(0L)

    // The filtered set every card/chart derives from, in chronological order.
    val sessions: StateFlow<List<SleepSession>> = combine(_timeRange, allSessions) { range, all ->
        filterForRange(all, range).sortedBy { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val averageScore: StateFlow<Float?> = sessions.map { list ->
        val scored = list.filter { it.qualityScore > 0 }
        if (scored.isEmpty()) null else scored.map { it.qualityScore }.average().toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Average duration derived from real start/end timestamps (in milliseconds) rather than the
    // stored whole-minute column, which floors sub-minute sessions to 0 and reports "--".
    val averageDurationMs: StateFlow<Long?> = sessions.map { list ->
        if (list.isEmpty()) null else list.map { it.elapsedMs() }.average().toLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Average deep sleep in milliseconds. When the stored whole-minute value floors to 0 (short
    // sessions), reconstruct from the elapsed time using the ~22% deep-sleep architecture so the
    // card shows a realistic value instead of "0min".
    val averageDeepSleepMs: StateFlow<Long> = sessions.map { list ->
        if (list.isEmpty()) 0L
        else list.map { s ->
            if (s.deepSleepMinutes > 0) s.deepSleepMinutes * 60_000L else s.elapsedMs() * 22 / 100
        }.average().toLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val averageInterruptions: StateFlow<Float> = sessions.map { list ->
        if (list.isEmpty()) 0f
        else list.map { it.interruptions }.average().toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val bestNight: StateFlow<SleepSession?> = sessions.map { list ->
        list.maxByOrNull { it.qualityScore }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val worstNight: StateFlow<SleepSession?> = sessions.map { list ->
        list.filter { it.qualityScore > 0 }.minByOrNull { it.qualityScore }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Start-of-period epoch millis used both as the chronological sort key and as the bucket label
    // source. Day -> midnight, week -> ISO Monday, month/all -> first of the month.
    private fun periodStartMillis(timestamp: Long, range: String): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (range) {
            "day" -> Unit // already midnight of the day
            "week" -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.minimalDaysInFirstWeek = 4
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            else -> cal.set(Calendar.DAY_OF_MONTH, 1) // month + all bucket by month
        }
        return cal.timeInMillis
    }

    private fun bucketLabel(startMillis: Long, range: String): String {
        val pattern = when (range) {
            "day", "week" -> "MMM d"
            "month" -> "MMM"
            else -> "MMM yy" // "all" can span years
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(startMillis))
    }

    // One bar per bucket for the selected range, chronological. This is what DurationChart renders,
    // so the bars now change when the Days/Weeks/Months/All chip changes.
    val durationTrend: StateFlow<List<DurationBucket>> = combine(_timeRange, allSessions) { range, all ->
        filterForRange(all, range)
            .groupBy { periodStartMillis(it.startTime, range) }
            .toSortedMap()
            .map { (startMillis, group) ->
                DurationBucket(
                    label = bucketLabel(startMillis, range),
                    averageDurationMs = group.map { it.elapsedMs() }.average().toLong()
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Walking steps pulled from wearables / Health Connect, aggregated over the selected range.
    val stepsStats: StateFlow<StepsStats> = _timeRange.mapLatest { range ->
        val startMs = rangeStartMillis(range).coerceAtLeast(0L)
        val endMs = System.currentTimeMillis()
        val samples = repository.getWearableSamplesInRange(startMs, endMs, WearableMetric.STEPS.name)
        if (samples.isEmpty()) {
            StepsStats()
        } else {
            val total = samples.sumOf { it.value.toLong() }
            val daysWithData = samples.map { dateFormat.format(Date(it.timestamp)) }.distinct().size
            StepsStats(
                total = total,
                dailyAverage = if (daysWithData > 0) (total / daysWithData).toInt() else 0,
                daysWithData = daysWithData
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StepsStats())
}

data class DurationBucket(
    val label: String,
    val averageDurationMs: Long
)

data class StepsStats(
    val total: Long = 0L,
    val dailyAverage: Int = 0,
    val daysWithData: Int = 0
)
