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

    val averageDuration: StateFlow<Float?> = _timeRange.flatMapLatest { _ ->
        val (start, end) = getDateRange()
        repository.getAverageDuration(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTimeRange(range: String) { _timeRange.value = range }

    // Computed stats
    val bestNight: StateFlow<SleepSession?> = sessions.map { list ->
        list.maxByOrNull { it.qualityScore }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val worstNight: StateFlow<SleepSession?> = sessions.map { list ->
        list.filter { it.qualityScore > 0 }.minByOrNull { it.qualityScore }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val averageDeepSleep: StateFlow<Int> = sessions.map { list ->
        if (list.isEmpty()) 0
        else list.map { it.deepSleepMinutes }.average().toInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
