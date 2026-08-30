package com.ikasle.scrollkill.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ikasle.scrollkill.R
import com.ikasle.scrollkill.ui.theme.ScrollKillTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the stateless [HomeScreen]. Runs on the JVM through Robolectric (no
 * device), matching the project's other unit tests. Each case feeds a hand-built [HomeUiState]
 * and asserts what the screen renders plus that the three callbacks fire with the right values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun str(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Determinate and indeterminate progress indicators both expose this semantics key. */
    private val progressBars =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun setScreen(
        state: HomeUiState,
        onToggleIntervene: (Boolean) -> Unit = {},
        onOpenAccessibilitySettings: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) {
        compose.setContent {
            ScrollKillTheme {
                HomeScreen(
                    state = state,
                    onToggleIntervene = onToggleIntervene,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }

    @Test
    fun todayCard_showsPreformattedTotal_whenPresent() {
        setScreen(HomeUiState(loading = false, todayTotalDuration = "37m"))

        compose.onNodeWithText("37m").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.home_today_label)).assertIsDisplayed()
    }

    @Test
    fun todayCard_fallsBackToZeroDuration_whenTotalBlank() {
        setScreen(HomeUiState(loading = false, todayTotalDuration = ""))

        compose.onNodeWithText(str(R.string.home_today_zero_duration)).assertIsDisplayed()
    }

    @Test
    fun todayCard_showsEmptyHint_whenNoTodayApps() {
        setScreen(HomeUiState(loading = false, todayApps = emptyList()))

        compose.onNodeWithText(str(R.string.home_today_empty)).assertIsDisplayed()
    }

    @Test
    fun todayCard_rendersRowFieldsAndOneBarPerLimitedApp() {
        setScreen(
            HomeUiState(
                loading = false,
                todayApps = listOf(
                    TodayAppUi(
                        packageName = "com.instagram.android",
                        displayName = "Instagram",
                        usedToday = "22m",
                        limitCaption = "22m / 30m",
                        progress = 0.73f,
                        overLimit = false,
                    ),
                    TodayAppUi(
                        packageName = "com.google.android.youtube",
                        displayName = "YouTube",
                        usedToday = "0s",
                        limitCaption = "No limit set",
                        progress = null,
                        overLimit = false,
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Instagram").assertIsDisplayed()
        compose.onNodeWithText("22m").assertIsDisplayed()
        compose.onNodeWithText("22m / 30m").assertIsDisplayed()
        compose.onNodeWithText("YouTube").assertIsDisplayed()
        compose.onNodeWithText("No limit set").assertIsDisplayed()
        // Only the Instagram row carries a daily limit, so exactly one bar is drawn.
        compose.onAllNodes(progressBars).assertCountEquals(1)
    }

    @Test
    fun serviceCard_off_showsRationaleAndOpensSettings() {
        var opened = false
        setScreen(
            HomeUiState(loading = false, serviceEnabled = false),
            onOpenAccessibilitySettings = { opened = true },
        )

        compose.onNodeWithText(str(R.string.home_service_off_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.home_service_off_body)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.home_service_open_settings)).performClick()

        assertTrue(opened)
    }

    @Test
    fun serviceCard_active_hidesOpenSettingsButton() {
        setScreen(HomeUiState(loading = false, serviceEnabled = true))

        compose.onNodeWithText(str(R.string.home_service_active_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.home_service_open_settings)).assertDoesNotExist()
    }

    @Test
    fun interveneToggle_reflectsStateAndReportsFlip() {
        var reported: Boolean? = null
        setScreen(
            HomeUiState(loading = false, interveneEnabled = true),
            onToggleIntervene = { reported = it },
        )

        val toggle = compose.onNode(isToggleable())
        toggle.assertIsOn()
        toggle.performClick()

        assertEquals(false, reported)
    }

    @Test
    fun interveneToggle_rendersOff_whenDisabled() {
        setScreen(HomeUiState(loading = false, interveneEnabled = false))

        compose.onNode(isToggleable()).assertIsOff()
    }

    // The screen is a verticalScroll Column: it composes every child, but content below the
    // synthetic Robolectric viewport is not "displayed", so presence checks use assertExists().

    @Test
    fun historyCard_loading_showsSpinnerNotTotals() {
        setScreen(HomeUiState(loading = true, windowLabel = "Last 7 days", totalDuration = "1h"))

        compose.onNodeWithText("Last 7 days").assertExists()
        compose.onNodeWithText(str(R.string.home_history_total, "1h")).assertDoesNotExist()
        assertTrue(compose.onAllNodes(progressBars).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun historyCard_empty_showsEmptyHint() {
        setScreen(HomeUiState(loading = false, windowLabel = "Last 7 days", apps = emptyList()))

        compose.onNodeWithText(str(R.string.home_history_empty)).assertExists()
    }

    @Test
    fun historyCard_populated_showsTotalAndPerAppStats() {
        setScreen(
            HomeUiState(
                loading = false,
                windowLabel = "Last 7 days",
                totalDuration = "1h 12m",
                apps = listOf(AppUsageUi("com.instagram.android", "Instagram", "48m", 6, 4)),
            ),
        )

        compose.onNodeWithText(str(R.string.home_history_total, "1h 12m")).assertExists()
        compose.onNodeWithText("Instagram").assertExists()
        compose.onNodeWithText("48m").assertExists()
        compose.onNodeWithText(str(R.string.home_app_usage_stats, 6, 4)).assertExists()
    }

    @Test
    fun topBar_settingsAction_reportsClick() {
        var opened = false
        setScreen(HomeUiState(loading = false), onOpenSettings = { opened = true })

        compose.onNodeWithText(str(R.string.home_action_settings)).performClick()

        assertTrue(opened)
    }
}
