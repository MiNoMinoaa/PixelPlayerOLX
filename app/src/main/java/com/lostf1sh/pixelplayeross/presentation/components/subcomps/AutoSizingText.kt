package com.lostf1sh.pixelplayeross.presentation.components.subcomps

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutoSizingTextToFill(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    minFontSize: TextUnit = 8.sp,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    maxFontSizeLimit: TextUnit = 100.sp, // Practical upper bound for the search
    lineHeightRatio: Float = 1.2f // Line-height factor (e.g., 1.2f for 20% more spacing)
) {
    // TextMeasurer is used to measure the text efficiently.
    val textMeasurer = rememberTextMeasurer()
    // Density is needed to convert dp to px.
    val density = LocalDensity.current

    // State for the determined font size.
    var currentFontSize by remember { mutableStateOf(minFontSize) }
    // State to know whether the calculation is ready and we can draw.
    var readyToDraw by remember { mutableStateOf(false) }

    // BoxWithConstraints gives us the available maxWidth and maxHeight.
    BoxWithConstraints(modifier = modifier) {
        // Convert the dp constraints to pixels once.
        val maxWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val maxHeightPx = with(density) { maxHeight.toPx() }.toInt()

        // LaunchedEffect to recompute when the text, style, font limits,
        // line-height ratio, or container size change.
        LaunchedEffect(text, style, minFontSize, maxFontSizeLimit, lineHeightRatio, maxWidthPx, maxHeightPx) {
            readyToDraw = false // Indicate that we need to recompute.
            var bestFitFontSize = minFontSize // Start by assuming the minimum.

            // Make sure the search bounds are valid.
            var lowerBoundSp = minFontSize.value
            var upperBoundSp = maxFontSizeLimit.value.coerceAtLeast(minFontSize.value)

            // If the search range is invalid (e.g., min > max limit), use minFontSize.
            if (lowerBoundSp > upperBoundSp + 0.01f) {
                currentFontSize = minFontSize
                readyToDraw = true
                return@LaunchedEffect
            }

            // 1. Check whether the text with minFontSize (and its corresponding lineHeight) already overflows.
            val minFontEffectiveLineHeight = minFontSize * lineHeightRatio
            val minFontEffectiveStyle = style.copy(
                fontSize = minFontSize,
                lineHeight = minFontEffectiveLineHeight
            )
            val minFontLayoutResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = minFontEffectiveStyle,
                overflow = TextOverflow.Clip, // Use Clip for precise measurement.
                softWrap = true,
                maxLines = Int.MAX_VALUE, // Allow all the lines we need.
                constraints = Constraints(
                    maxWidth = maxWidthPx.coerceAtLeast(0), // Make sure it isn't negative.
                    maxHeight = maxHeightPx.coerceAtLeast(0) // Make sure it isn't negative.
                )
            )

            if (minFontLayoutResult.hasVisualOverflow) {
                // Even with minFontSize the text overflows. We'll use minFontSize and it will be truncated.
                currentFontSize = minFontSize
                readyToDraw = true
                return@LaunchedEffect
            } else {
                // minFontSize fits, so it's our initial "best fit".
                bestFitFontSize = minFontSize
            }

            // 2. Binary search to find the largest font size that fits.
            // Iterate a fixed number of times to ensure convergence.
            repeat(15) { // 15 iterations are usually enough for sp precision.
                // If the difference between the bounds is very small, we've converged.
                if (upperBoundSp - lowerBoundSp < 0.1f) {
                    currentFontSize = bestFitFontSize
                    readyToDraw = true
                    return@LaunchedEffect // Exit the LaunchedEffect.
                }

                val midSp = (lowerBoundSp + upperBoundSp) / 2f
                val candidateFontSize = midSp.sp

                // Avoid measuring sizes smaller than our known best fit, if we already passed them.
                if (candidateFontSize.value < bestFitFontSize.value && candidateFontSize.value < midSp) {
                    lowerBoundSp = midSp + 0.01f // Continue the search in the upper half.
                    return@repeat // Skip this repeat iteration.
                }

                // Compute the lineHeight dynamically based on candidateFontSize.
                val currentEffectiveLineHeight = candidateFontSize * lineHeightRatio
                val candidateStyle = style.copy(
                    fontSize = candidateFontSize,
                    lineHeight = currentEffectiveLineHeight
                )

                val layoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = candidateStyle,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    constraints = Constraints(
                        maxWidth = maxWidthPx.coerceAtLeast(0),
                        maxHeight = maxHeightPx.coerceAtLeast(0)
                    )
                )

                if (layoutResult.hasVisualOverflow) {
                    // The candidate size is too large (overflows in height or width).
                    upperBoundSp = midSp - 0.01f
                } else {
                    // The candidate size fits. It's our new "best fit".
                    // We'll try to find an even larger one.
                    bestFitFontSize = candidateFontSize
                    lowerBoundSp = midSp + 0.01f
                }
            }

            currentFontSize = bestFitFontSize
            readyToDraw = true
        }

        // Only draw the Text once we've determined the font size.
        if (readyToDraw) {
            // Apply the computed fontSize and lineHeight to the final Text.
            val finalEffectiveLineHeight = currentFontSize * lineHeightRatio
            Text(
                text = text,
                modifier = Modifier, // The Text's modifier doesn't need fillMaxSize here.
                style = style.copy(
                    fontSize = currentFontSize,
                    lineHeight = finalEffectiveLineHeight
                ),
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                overflow = TextOverflow.Ellipsis, // Truncate if it still overflows despite everything.
                softWrap = true,
                // The font size was chosen so all lines fit in height.
                maxLines = Int.MAX_VALUE
            )
        }
    }
}