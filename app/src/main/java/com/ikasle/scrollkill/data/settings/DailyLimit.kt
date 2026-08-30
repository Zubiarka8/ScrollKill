package com.ikasle.scrollkill.data.settings

/**
 * A per-app daily time budget for infinite-content surfaces, evaluated over a rolling 24-hour
 * window by [com.ikasle.scrollkill.blocking.DailyUsageMeter].
 *
 * [Off] means no budget: the BlockingEngine blocks the surface on sight (the pre-daily-limits
 * behavior). [Minutes] is a positive whole-minute budget; [PRESETS] are the values the picker
 * offers as a single tap, and the user can type any other value in the range
 * [MIN_CUSTOM_MINUTES]..[MAX_CUSTOM_MINUTES] ("Custom").
 *
 * Serialized to DataStore as a short stable token ([storageToken]); [parse] also still reads
 * the legacy `OFF` / `MIN_5`..`MIN_60` enum names written by builds up to 2026-08-30.
 */
sealed interface DailyLimit {

    /** Budget in milliseconds, or null for [Off]. */
    val budgetMs: Long?

    /** Label for the picker and the home screen ("No limit", "5 min/day", "12 min/day"). */
    val label: String

    /** Stable string form for DataStore. */
    val storageToken: String

    data object Off : DailyLimit {
        override val budgetMs: Long? = null
        override val label: String = "No limit"
        override val storageToken: String = "OFF"
    }

    data class Minutes(val value: Int) : DailyLimit {
        init { require(value > 0) { "daily-limit minutes must be positive, was $value" } }
        override val budgetMs: Long get() = value * 60_000L
        override val label: String get() = "$value min/day"
        override val storageToken: String get() = "MIN:$value"
    }

    companion object {
        const val MIN_CUSTOM_MINUTES = 1
        const val MAX_CUSTOM_MINUTES = 600

        /** One-tap options offered by the picker, in display order. */
        val PRESETS: List<DailyLimit> = listOf(
            Off,
            Minutes(5),
            Minutes(10),
            Minutes(15),
            Minutes(30),
            Minutes(60),
        )

        /** True when [limit] is a whole-minute budget the picker has no preset row for. */
        fun isCustom(limit: DailyLimit): Boolean = limit is Minutes && limit !in PRESETS

        /**
         * Parse a [storageToken], also accepting the legacy `OFF` / `MIN_<n>` enum names.
         * Returns null for anything unrecognised so the caller can fall back to a default.
         */
        fun parse(token: String): DailyLimit? = when {
            token == "OFF" -> Off
            token.startsWith("MIN:") -> minutesOrNull(token.removePrefix("MIN:"))
            token.startsWith("MIN_") -> minutesOrNull(token.removePrefix("MIN_")) // legacy
            else -> null
        }

        private fun minutesOrNull(raw: String): Minutes? =
            raw.toIntOrNull()?.takeIf { it > 0 }?.let(::Minutes)
    }
}

/**
 * The daily budget in effect for [packageName]: its per-app override if set, otherwise the
 * global default. Single source of truth for the BlockingEngine wiring and the home screen.
 */
fun ScrollKillSettings.dailyLimitFor(packageName: String): DailyLimit =
    dailyLimitOverrides[packageName] ?: defaultDailyLimit
