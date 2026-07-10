package tech.future.sleepanalyzer.wearables

/** Catalog of metrics the app understands from any wearable source. */
enum class WearableMetric(val unit: String) {
    HEART_RATE("bpm"),
    HRV_RMSSD("ms"),
    RESPIRATORY_RATE("rpm"),
    SPO2("percent"),
    SKIN_TEMPERATURE("celsius"),
    BODY_TEMPERATURE("celsius"),
    CAFFEINE("mg"),
    HYDRATION("ml"),
    STEPS("count"),
    SLEEP_STAGE_SOURCE("stage"),
    RESTING_HEART_RATE("bpm");

    val key: String get() = name

    companion object {
        fun fromKey(key: String?): WearableMetric? = entries.firstOrNull { it.name == key }
    }
}
