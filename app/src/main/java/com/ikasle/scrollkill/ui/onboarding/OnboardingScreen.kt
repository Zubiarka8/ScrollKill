package com.ikasle.scrollkill.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ikasle.scrollkill.R
import com.ikasle.scrollkill.ui.theme.ScrollKillTheme

/**
 * First-run rationale shown once before the system Accessibility dialog. This is the
 * prominent in-app disclosure required by the Google Play "Use of the AccessibilityService
 * API" policy: it names the data the service reads, says how it is used, and both buttons
 * are an affirmative choice that records consent ([onEnableDetection] / [onSkip]).
 *
 * Stateless: the caller owns whether onboarding is still pending and what each action does.
 */
@Composable
fun OnboardingScreen(
    onEnableDetection: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyLarge,
            )

            Section(
                heading = stringResource(R.string.onboarding_data_heading),
                body = stringResource(R.string.onboarding_data_body),
            )
            Section(
                heading = stringResource(R.string.onboarding_privacy_heading),
                body = stringResource(R.string.onboarding_privacy_body),
            )

            Text(
                text = stringResource(R.string.onboarding_consent_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onEnableDetection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_enable))
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

@Composable
private fun Section(heading: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    ScrollKillTheme {
        OnboardingScreen(onEnableDetection = {}, onSkip = {})
    }
}
