package com.aurorashelf.app.ui

/** Layout uses the actual app window, not a guessed Samsung model name or physical pixel count. */
internal data class WindowLayout(
    val heroAspectRatio: Float,
    val rowThumbnailFraction: Float,
    val sectionGap: Float,
    val compactDock: Boolean,
) {
    companion object {
        fun calculate(widthDp: Float, availableHeightDp: Float, isLandscape: Boolean): WindowLayout {
            val contentWidth = (widthDp.coerceAtMost(680f) - 40f).coerceAtLeast(160f)
            val thumbnailFraction = if (isLandscape) 0.32f else 0.46f
            val rowHeight = contentWidth * thumbnailFraction / 1.6f + 8f
            // Reserve the header, category strip, two rows and section gaps at default text size.
            // Larger fonts remain scrollable; text is never scaled down to force a fit.
            // Expressive feeds keep the lead story prominent without pushing the first
            // two actionable rows below the fold on tall phones.
            val heroHeight = if (isLandscape) 128f else
                (availableHeightDp - 106f - 2f * rowHeight).coerceIn(214f, 248f)
            return WindowLayout(contentWidth / heroHeight, thumbnailFraction, if (isLandscape) 10f else 16f, isLandscape)
        }
    }
}
