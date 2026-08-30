package com.ikasle.scrollkill.ui.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
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
 * interactions performScrollTo() first. Each ChoiceRow merges its Text but keeps the RadioButton
 * as a separate node, so selection is asserted on the RadioButton via the unmerged tree.
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
        limit: DailyLimit = DailyLimit.OFF,
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

    /** All Switches on the screen, in tree order: [0] master intervene, then Watch, Nudge per app row. */
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
    fun defaultDailyLimit_marksCurrentAndReportsPick() {
        var picked: DailyLimit? = null
        setScreen(
            SettingsUiState(defaultDailyLimit = DailyLimit.MIN_30),
            onPickDefaultDailyLimit = { picked = it },
        )

        radioFor(DailyLimit.MIN_30.label).assertIsSelected()
        radioFor(DailyLimit.OFF.label).assertIsNotSelected()

        compose.onNodeWithText(DailyLimit.MIN_60.label).performScrollTo().performClick()
        assertEquals(DailyLimit.MIN_60, picked)
    }

    @Test
    fun perAppDailyLimit_sectionHidden_whenNoWatchedApp() {
        setScreen(SettingsUiState(apps = listOf(app(watched = false))))

        compose.onNodeWithText(str(R.string.settings_section_daily_limit_per_app))
            .assertDoesNotExist()
    }

    @Test
    fun perAppDailyLimit_sectionShown_useDefaultSelected_whenNoOverride() {
        setScreen(
            SettingsUiState(
                defaultDailyLimit = DailyLimit.MIN_10,
                apps = listOf(app(watched = true, override = false)),
            ),
        )

        compose.onNodeWithText(str(R.string.settings_section_daily_limit_per_app)).assertExists()
        radioFor(str(R.string.settings_daily_limit_use_default, DailyLimit.MIN_10.label))
            .assertIsSelected()
    }

    @Test
    fun perAppDailyLimit_pickPreset_reportsOverride() {
        var picked: Pair<String, DailyLimit?>? = null
        setScreen(
            SettingsUiState(
                defaultDailyLimit = DailyLimit.OFF,
                apps = listOf(app(watched = true, override = false)),
            ),
            onPickAppDailyLimit = { p, l -> picked = p to l },
        )

        // "30 min/day" renders in both the default list and the per-app list; index 1 is the
        // per-app row (declared, and laid out, after the default section).
        compose.onAllNodesWithText(DailyLimit.MIN_30.label)[1].performScrollTo().performClick()

        assertEquals(pkg to DailyLimit.MIN_30, picked)
    }

    @Test
    fun perAppDailyLimit_pickUseDefault_reportsClear() {
        var picked: Pair<String, DailyLimit?>? = null
        setScreen(
            SettingsUiState(
                defaultDailyLimit = DailyLimit.MIN_15,
                apps = listOf(app(watched = true, override = true, limit = DailyLimit.MIN_5)),
            ),
            onPickAppDailyLimit = { p, l -> picked = p to l },
        )

        compose.onNodeWithText(str(R.string.settings_daily_limit_use_default, DailyLimit.MIN_15.label))
            .performScrollTo()
            .performClick()

        assertEquals(pkg to null, picked)
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
