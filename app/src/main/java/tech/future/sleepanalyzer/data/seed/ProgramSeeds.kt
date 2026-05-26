package tech.future.sleepanalyzer.data.seed

import tech.future.sleepanalyzer.data.db.entity.SleepProgram

/**
 * Centralized catalog of default sleep programs. Used at first-run seeding from
 * [tech.future.sleepanalyzer.SleepAnalyzerApp] so the Programs screen never appears empty,
 * even after destructive Room migrations.
 *
 * Keep this list stable. Adding new programs: append at the end. Editing a program's
 * `title` re-seeds it (we treat title as the natural key for de-duplication).
 */
object ProgramSeeds {
    val defaults: List<SleepProgram> = listOf(
        SleepProgram(
            title = "Stress Relief for Better Sleep",
            description = "Learn techniques to manage stress and improve your sleep quality. Includes breathing exercises, progressive muscle relaxation, and mindfulness practices.",
            category = "stress_relief",
            steps = "[\"Day 1: Introduction to sleep-stress connection\",\"Day 2: 4-7-8 breathing technique\",\"Day 3: Progressive muscle relaxation\",\"Day 4: Guided visualization\",\"Day 5: Journaling before bed\",\"Day 6: Creating a worry list\",\"Day 7: Putting it all together\"]",
            durationDays = 7
        ),
        SleepProgram(
            title = "Bedroom Environment Hacks",
            description = "Optimize your sleep environment for maximum rest. From temperature to lighting, discover what makes the perfect bedroom for sleep.",
            category = "bedroom_hacks",
            steps = "[\"Day 1: Temperature optimization (65-68°F)\",\"Day 2: Light blocking techniques\",\"Day 3: Noise management\",\"Day 4: Mattress and pillow check\",\"Day 5: Remove electronics\",\"Day 6: Aromatherapy basics\",\"Day 7: Your ideal sleep sanctuary\"]",
            durationDays = 7
        ),
        SleepProgram(
            title = "Digital Detox Before Bed",
            description = "Break free from screen addiction before sleep. Learn to create healthy boundaries with technology for better rest.",
            category = "screen_use",
            steps = "[\"Day 1: Track your screen time\",\"Day 2: Set a screen curfew (1hr before bed)\",\"Day 3: Blue light filters and night mode\",\"Day 4: Replace scrolling with reading\",\"Day 5: No-phone bedroom challenge\",\"Day 6: Evening routine without screens\",\"Day 7: Maintaining your digital boundaries\"]",
            durationDays = 7
        ),
        SleepProgram(
            title = "Sleep Hygiene Fundamentals",
            description = "Master the basics of good sleep hygiene. Build habits that promote consistent, restful sleep every night.",
            category = "sleep_hygiene",
            steps = "[\"Day 1: Set a consistent sleep schedule\",\"Day 2: Limit caffeine after 2 PM\",\"Day 3: Exercise timing for sleep\",\"Day 4: Meal timing and sleep\",\"Day 5: Create a bedtime routine\",\"Day 6: Napping guidelines\",\"Day 7: Your personalized sleep plan\"]",
            durationDays = 7
        ),
        SleepProgram(
            title = "Deep Relaxation Techniques",
            description = "Discover powerful relaxation methods to help you fall asleep faster and stay asleep longer.",
            category = "relaxation",
            steps = "[\"Day 1: Body scan meditation\",\"Day 2: Deep breathing exercises\",\"Day 3: Yoga nidra for sleep\",\"Day 4: Autogenic training\",\"Day 5: Guided imagery\",\"Day 6: Self-hypnosis basics\",\"Day 7: Creating your relaxation toolkit\"]",
            durationDays = 7
        )
    )
}
