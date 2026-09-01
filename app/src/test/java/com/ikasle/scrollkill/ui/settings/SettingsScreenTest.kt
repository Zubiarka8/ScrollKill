package com.ikasle.scrollkill.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.ikasle.scrollkill.R
import com.ikasle.scrollkill.data.settings.DailyLimit
import com.ikasle.scrollkill.data.settings.RetentionWindow
import com.ikasle.scrollkill.data.settings.StatsWindow
import com.ikasle.scrollkill.ui.theme.ScrollKillTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the stateless [SettingsScreen], run on the JVM through Robolectric.
 *
 * Notes on matching: the screen is a verticalScroll Column, so content below the synthetic
 * Robolectric viewport exists but is not "displayed" - presence checks use assertExists() and
 * interactions performScrollTo() first. The stats-window and retention pickers are still
 * ChoiceRows (Text + a sibling RadioButton, asserted via [radioFor] on the unmerged tree);
 * the daily-limit picker is a pill that opens a DropdownMenu, so a pick is: tap the pill,
 * then tap the menu item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private val pkg = "com.instagram.android"

    private fun setScreen(
        state: SettingsUiState,
        onBack: () -> Unit = {},
        onToggleIntervene: (Boolean) -> Unit = {},
        onToggleApp: (String, Boolean) -> Unit = { _, _ -> },
        onToggleWatchApp: (String, Boolean) -> Unit = { _, _ -> },
        onPickDefaultDailyLimit: (DailyLimit) -> Unit = {},
        onPickAppDailyLimit: (String, DailyLimit?) -> Unit = { _, _ -> },
        onPickWindow: (StatsWindow) -> Unit = {},
        onPickRetention: (RetentionWindow) -> Unit = {},
    ) {
        compose.setContent {
            ScrollKillTheme {
                SettingsScreen(
                    state = state,
                    onBack = onBack,
                    onToggleIntervene = onToggleIntervene,
                    onToggleApp = onToggleApp,
                    onToggleWatchApp = onToggleWatchApp,
                    onPickDefaultDailyLimit = onPickDefaultDailyLimit,
                    onPickAppDailyLimit = onPickAppDailyLimit,
                    onPickWindow = onPickWindow,
                    onPickRetention = onPickRetention,
                )
            }
        }
    }

    private fun app(
        watched: Boolean = true,
        blocking: Boolean = true,
        limit: DailyLimit = DailyLimit.Off,
        override: Boolean = false,
    ) = AppToggleUi(
        packageName = pkg,
        displayName = "Instagram",
        blockingEnabled = blocking,
        watchedEnabled = watched,
        dailyLimit = limit,
        dailyLimitIsOverride = override,
    )

    /** The RadioButton whose sibling label is [label] (unmerged tree; the merged Text drops Selected). */
    private fun radioFor(label: String) =
        compose.onNode(isSelectable() and hasAnySibling(hasText(label)), useUnmergedTree = true)

    /** The compact limit-pill / menu caption for [limit] ("No limit", "15 min", "1h", "1h 30m"). */
    private fun pill(limit: DailyLimit): String = when (limit) {
        DailyLimit.Off -> str(R.string.settings_daily_limit_pill_off)
        is DailyLimit.Minutes -> {
            val h = limit.value / 60
            val m = limit.value % 60
            when {
                h == 0 -> str(R.string.settings_daily_limit_pill_minutes, m)
                m == 0 -> str(R.string.settings_daily_limit_pill_hours, h)
                else -> str(R.string.settings_daily_limit_pill_hours_minutes, h, m)
            }
        }
    }

    /**
     * All Switches on the screen, in tree order: [0] master intervene, then Watch and Nudge
     * per app in the "Watched apps" section, then the enforce switch on each per-app limit row.
     */
    private fun toggles() = compose.onAllNodes(isToggleable())

    @Test
    fun masterToggle_reflectsStateAndReportsFlip() {
        var reported: Boolean? = null
        setScreen(
            SettingsUiState(interveneEnabled = true),
            onToggleIntervene = { reported = it },
        )

        val toggle = compose.onNode(isToggleable())
        toggle.assertIsOn()
        toggle.performClick()

        assertEquals(false, reported)
    }

    @Test
    fun appRow_watched_showsNameAndBothSwitches_noHint() {
        setScreen(SettingsUiState(apps = listOf(app(watched = true))))

        // "Instagram" also labels the per-app daily-limit block, so there are two.
        compose.onAllNodesWithText("Instagram").onFirst().assertExists()
        compose.onNodeWithText(str(R.string.settings_app_watch)).assertExists()
        compose.onNodeWithText(str(R.string.settings_app_nudge)).assertExists()
        compose.onNodeWithText(str(R.string.settings_app_unwatched_hint)).assertDoesNotExist()
    }

    @Test
    fun appRow_unwatched_disablesNudgeAndShowsHint() {
        setScreen(SettingsUiState(apps = listOf(app(watched = false, blocking = false))))

        compose.onNode(isToggleable() and isNotEnabled()).assertIsOff()
        compose.onNodeWithText(str(R.string.settings_app_unwatched_hint)).assertExists()
    }

    @Test
    fun appRow_watchSwitch_reportsFlip() {
        var reported: Pair<String, Boolean>? = null
        setScreen(
            SettingsUiState(apps = listOf(app(watched = true))),
            onToggleWatchApp = { p, e -> reported = p to e },
        )

        toggles()[1].performScrollTo().performClick()

        assertEquals(pkg to false, reported)
    }

    @Test
    fun appRow_nudgeSwitch_reportsFlip() {
        var reported: Pair<String, Boolean>? = null
        setScreen(
            SettingsUiState(apps = listOf(app(watched = true, blocking = false))),
            onToggleApp = { p, e -> reported = p to e },
        )

        toggles()[2].performScrollTo().performClick()

        assertEquals(pkg to true, reported)
    }

    @Test
    fun defaultDailyLimit_pillShowsValue_menuPickReports() {
        var picked: DailyLimit? = null
        setScreen(
            SettingsUiState(defaultDailyLimit = DailyLimit.Minutes(30)),
            onPickDefaultDailyLimit = { picked = it },
        )

        compose.onNodeWithText(pill(DailyLimit.Minutes(30))).performScrollTo().performClick()
        // Menu now open: pick the "1h" preset.
        compose.onNodeWithText(pill(DailyLimit.Minutes(60))).performClick()

        assertEquals(DailyLimit.Minutes(60), picked)
    }

    @Test
    fun perAppLimit_rowHidden_whenAppUnwatched() {
        setScreen(SettingsUiState(apps = listOf(app(watched = false))))

        // Only the "Watched apps" entry, no per-app limit row.
        compose.onAllNodesWithText("Instagram").assertCountEquals(1)
    }

    @Test
    fun perAppLimit_rowShown_whenWatched() {
        setScreen(
            SettingsUiState(
                defaultDailyLimit = DailyLimit.Minutes(10),
                apps = listOf(app(watched = true, override = false)),
            ),
        )

        compose.onNodeWithText(str(R.string.settings_daily_limit_subtitle)).assertExists()
        // "Watched apps" row + the limit row.
        compose.onAllNodesWithText("Instagram").assertCountEquals(2)
    }

    @Test
    fun perAppLimit_menuPickPreset_reportsOverride() {
        var picked: Pair<String, DailyLimit?>? = null
        setScreen(
            SettingsUiState(
                defaultDailyLimit = DailyLimit.Off,
                apps = listOf(app(watched = true, override = false)),
            ),
            onPickAppDailyLimit = { p, l -> picked = p to l },
        )

        // Default pill and the per-app pill both read "No limit"; index 1 is the per-app row.
        compose.onAllNodesWithText(pill(DailyLimit.Off))[1].performScrollTo().performClick()
        compose.onNodeWithText(pill(DailyLimit.Minutes(30))).performClick()

        assertEquals(pkg to DailyLimit.Minutes(30), picked)
    }

    @Test
    fun perAppLimit_menuUseDefault_reportsClear() {
        var picked: Pair<String, DailyLimit?>? = null
        setScreen(
            SettingsUiState(
                defaultDailyLimit = DailyLimit.Minutes(15),
                apps = listOf(app(watched = true, override = true, limit = DailyLimit.Minutes(5))),
            ),
            onPickAppDailyLimit = { p, l -> picked = p to l },
        )

        compose.onNodeWithText(pill(DailyLimit.Minutes(5))).performScrollTo().performClick()
        compose.onNodeWithText(
            str(R.string.settings_daily_limit_use_default, pill(DailyLimit.Minutes(15))),
        ).performClick()

        assertEquals(pkg to null, picked)
    }

    @Test
    fun perAppLimit_enforceSwitch_reportsFlip() {
        var reported: Pair<String, Boolean>? = null
        setScreen(
            SettingsUiState(apps = listOf(app(watched = true, blocking = false))),
            onToggleApp = { p, e -> reported = p to e },
        )

        // [0] master, [1] Watch, [2] Nudge (Watched apps section), [3] enforce (limit row).
        toggles()[3].performScrollTo().performClick()

        assertEquals(pkg to true, reported)
    }

    @Test
    fun defaultDailyLimit_menuCustom_opensDialog_andReportsTypedMinutes() {
        var picked: DailyLimit? = null
        setScreen(
            SettingsUiState(defaultDailyLimit = DailyLimit.Off),
            onPickDefaultDailyLimit = { picked = it },
        )

        compose.onNodeWithText(pill(DailyLimit.Off)).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.settings_daily_limit_custom)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("42")
        compose.onNodeWithText(str(R.string.settings_daily_limit_custom_dialog_confirm)).performClick()

        assertEquals(DailyLimit.Minutes(42), picked)
    }

    @Test
    fun customDailyLimitDialog_confirmDisabled_forOutOfRangeMinutes() {
        setScreen(SettingsUiState(defaultDailyLimit = DailyLimit.Off))

        compose.onNodeWithText(pill(DailyLimit.Off)).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.settings_daily_limit_custom)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("999")

        compose.onNodeWithText(str(R.string.settings_daily_limit_custom_dialog_confirm)).assertIsNotEnabled()
    }

    @Test
    fun statsWindow_marksCurrentAndReportsPick() {
        var picked: StatsWindow? = null
        setScreen(
            SettingsUiState(statsWindow = StatsWindow.LAST_7_DAYS),
            onPickWindow = { picked = it },
        )

        radioFor(StatsWindow.LAST_7_DAYS.label).assertIsSelected()
        compose.onNodeWithText(StatsWindow.LAST_30_DAYS.label).performScrollTo().performClick()

        assertEquals(StatsWindow.LAST_30_DAYS, picked)
    }

    @Test
    fun retention_marksCurrentAndReportsPick() {
        var picked: RetentionWindow? = null
        setScreen(
            SettingsUiState(historyRetention = RetentionWindow.DAYS_90),
            onPickRetention = { picked = it },
        )

        radioFor(RetentionWindow.DAYS_90.label).assertIsSelected()
        compose.onNodeWithText(RetentionWindow.DAYS_365.label).performScrollTo().performClick()

        assertEquals(RetentionWindow.DAYS_365, picked)
    }

    @Test
    fun backButton_reportsClick() {
        var backed = false
        setScreen(SettingsUiState(), onBack = { backed = true })

        compose.onNodeWithText(str(R.string.settings_back)).performClick()

        assertTrue(backed)
    }
}
