package io.rotaskat.app.ui.eval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.rotaskat.app.ui.theme.RotaskatTheme
import io.rotaskat.app.ui.theme.seriesStyleFor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PointsChartSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TalkBack liest den Stand statt eines stummen Bildes`() {
        val series = listOf(
            ChartSeries("Lars", seriesStyleFor(0), listOf(0L, -36L)),
            ChartSeries("Johannes", seriesStyleFor(1), listOf(0L, 72L)),
        )
        compose.setContent { RotaskatTheme { PointsChart(series = series) } }
        compose.onNodeWithContentDescription("Punkteverlauf über 1 Runde: Johannes +36, Lars -18").assertIsDisplayed()
    }
}
