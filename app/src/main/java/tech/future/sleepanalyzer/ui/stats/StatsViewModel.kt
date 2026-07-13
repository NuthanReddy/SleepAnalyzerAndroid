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

    private val _timeRange = MutableStateFlow("week") // "week", "month", "all"
    val timeRange: StateFlow<String> = _timeRange

    private fun getDateRange(): Pair<String, String> {
        val cal = Calendar.getInstance()
        val end = dateFormat.format(cal.time)
        when (_timeRange.value) {
            "day" -> cal.add(Calendar.DAY_OF_YEAR, -1)
            "week" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "month" -> cal.add(Calendar.MONTH, -1)
            "all" -> cal.add(Calendar.YEAR, -10)
        }
        return dateFormat.format(cal.time) to end
    }

    private fun getMillisRange(): Pair<Long, Long> {
        val end = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        when (_timeRange.value) {
            "day" -> cal.add(Calendar.DAY_OF_YEAR, -1)
            "week" -> cal.add(Calendar.DAY_OF_YEAR, -7)
            "month" -> cal.add(Calendar.MONTH, -1)
            "all" -> cal.add(Calendar.YEAR, -10)
        }
        return cal.timeInMillis to end
    }

    val sessions: StateFlow<List<SleepSession>> = _timeRange.flatMapLatest { _ ->
        val (start, end) = getDateRange()
        repository.getSessionsBetween(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val averageScore: StateFlow<Float?> = _timeRange.flatMapLatest { _ ->
        val (start, end) = getDateRange()
        repository.getAverageScore(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Average duration derived from real start/end timestamps (in milliseconds) rather than the
    // stored whole-minute column, which floors sub-minute sessions to 0 and reports "--".
    val averageDurationMs: StateFlow<Long?> = sessions.map { list ->
        if (list.isEmpty()) null else list.map { it.elapsedMs() }.average().toLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTimeRange(range: String) { _timeRange.value = range }

    // Real elapsed time from start/end timestamps, falling back to the stored minute count only
    // when endTime is missing. Keeps sub-minute precision that durationMinutes throws away.
    private fun SleepSession.elapsedMs(): Long =
        ((endTime ?: (startTime + durationMinutes * 60_000L)) - startTime).coerceAtLeast(0L)

    // Computed stats
    val bestNight: StateFlow<SleepSession?> = sessions.map { list ->
        list.maxByOrNull { it.qualityScore }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val worstNight: StateFlow<SleepSession?> = sessions.map { list ->
        list.filter { it.qualityScore > 0 }.minByOrNull { it.qualityScore }
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

    // Walking steps pulled from wearables / Health Connect, aggregated over the selected range.
    val stepsStats: StateFlow<StepsStats> = _timeRange.mapLatest {
        val (startMs, endMs) = getMillisRange()
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

data class StepsStats(
    val total: Long = 0L,
    val dailyAverage: Int = 0,
    val daysWithData: Int = 0
)
