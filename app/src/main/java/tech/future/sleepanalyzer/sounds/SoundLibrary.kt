package tech.future.sleepanalyzer.sounds

data class SoundCategory(
    val id: String,
    val name: String,
    val description: String,
    val icon: String, // emoji
    val sounds: List<SoundItem>
)

data class SoundItem(
    val id: String,
    val name: String,
    val description: String,
    val icon: String
)

object SoundLibrary {
    val categories = listOf(
        SoundCategory(
            id = "white_noise",
            name = "White Noise",
            description = "Consistent, soothing background noise",
            icon = "🌫️",
            sounds = listOf(
                SoundItem("white_noise_classic", "Classic White Noise", "Pure white noise", "🔊"),
                SoundItem("white_noise_soft", "Soft White Noise", "Gentle background hum", "🔉"),
                SoundItem("fan_noise", "Fan Sound", "Calming fan noise", "🌀"),
                SoundItem("ac_hum", "AC Hum", "Air conditioner sound", "❄️")
            )
        ),
        SoundCategory(
            id = "pink_noise",
            name = "Pink Noise",
            description = "Deeper, balanced frequencies",
            icon = "🌸",
            sounds = listOf(
                SoundItem("pink_noise_classic", "Classic Pink Noise", "Balanced frequency noise", "🎵"),
                SoundItem("pink_noise_deep", "Deep Pink Noise", "Lower frequency emphasis", "🎶"),
                SoundItem("waterfall", "Waterfall", "Rushing waterfall", "💧")
            )
        ),
        SoundCategory(
            id = "green_noise",
            name = "Green Noise",
            description = "Mid-range, nature-like frequencies",
            icon = "🌿",
            sounds = listOf(
                SoundItem("green_noise_classic", "Classic Green Noise", "Natural mid-range noise", "🍃"),
                SoundItem("forest_ambient", "Forest Ambient", "Forest atmosphere", "🌲"),
                SoundItem("meadow_wind", "Meadow Wind", "Gentle breeze through fields", "🌾")
            )
        ),
        SoundCategory(
            id = "rain",
            name = "Rain Sounds",
            description = "Calming rain in various settings",
            icon = "🌧️",
            sounds = listOf(
                SoundItem("light_rain", "Light Rain", "Gentle rainfall", "🌦️"),
                SoundItem("heavy_rain", "Heavy Rain", "Strong downpour", "⛈️"),
                SoundItem("rain_on_roof", "Rain on Roof", "Cozy roof rain", "🏠"),
                SoundItem("thunderstorm", "Thunderstorm", "Rain with distant thunder", "⚡"),
                SoundItem("rain_on_window", "Rain on Window", "Drops on glass", "🪟")
            )
        ),
        SoundCategory(
            id = "nature",
            name = "Nature",
            description = "Peaceful natural environments",
            icon = "🌊",
            sounds = listOf(
                SoundItem("ocean_waves", "Ocean Waves", "Rhythmic ocean surf", "🏖️"),
                SoundItem("river_stream", "River Stream", "Babbling brook", "🏞️"),
                SoundItem("birds_morning", "Morning Birds", "Dawn chorus", "🐦"),
                SoundItem("crickets_night", "Night Crickets", "Evening cricket sounds", "🦗"),
                SoundItem("campfire", "Campfire", "Crackling fire", "🔥")
            )
        ),
        SoundCategory(
            id = "asmr",
            name = "ASMR",
            description = "Autonomous sensory meridian response",
            icon = "✨",
            sounds = listOf(
                SoundItem("asmr_whisper", "Whisper", "Soft whispering sounds", "🤫"),
                SoundItem("asmr_tapping", "Tapping", "Gentle tapping rhythms", "👆"),
                SoundItem("asmr_page_turning", "Page Turning", "Book pages rustling", "📖"),
                SoundItem("asmr_brushing", "Brushing", "Soft brush strokes", "🖌️")
            )
        ),
        SoundCategory(
            id = "meditation",
            name = "Guided Meditation",
            description = "Sleep meditation and relaxation",
            icon = "🧘",
            sounds = listOf(
                SoundItem("meditation_breathing", "Breathing Exercise", "Guided breathing for sleep", "🫁"),
                SoundItem("meditation_body_scan", "Body Scan", "Progressive relaxation", "🧘‍♀️"),
                SoundItem("meditation_mindfulness", "Mindfulness", "Mindful awareness", "🕯️"),
                SoundItem("singing_bowls", "Singing Bowls", "Tibetan singing bowls", "🔔")
            )
        ),
        SoundCategory(
            id = "stories",
            name = "Bedtime Stories",
            description = "Calming stories to drift off to sleep",
            icon = "📚",
            sounds = listOf(
                SoundItem("story_enchanted_forest", "The Enchanted Forest", "A journey through a magical woodland", "🌳"),
                SoundItem("story_ocean_voyage", "Ocean Voyage", "A peaceful sail across calm seas", "⛵"),
                SoundItem("story_stargazing", "Stargazing Night", "Exploring the night sky", "⭐"),
                SoundItem("story_mountain_lodge", "Mountain Lodge", "A cozy evening in the mountains", "🏔️")
            )
        ),
        SoundCategory(
            id = "music",
            name = "Sleep Music",
            description = "Relaxing music for deep sleep",
            icon = "🎹",
            sounds = listOf(
                SoundItem("music_piano", "Gentle Piano", "Soft piano melodies", "🎹"),
                SoundItem("music_ambient", "Ambient Music", "Ethereal ambient soundscapes", "🎧"),
                SoundItem("music_lullaby", "Lullaby", "Classic lullaby melodies", "🌙"),
                SoundItem("music_binaural", "Binaural Beats", "Delta wave binaural beats", "🧠")
            )
        )
    )

    fun getCategoryById(id: String): SoundCategory? = categories.find { it.id == id }
    fun getSoundById(soundId: String): Pair<SoundCategory, SoundItem>? {
        for (category in categories) {
            val sound = category.sounds.find { it.id == soundId }
            if (sound != null) return category to sound
        }
        return null
    }
}
