package com.bitchat.android.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.getSystemService
import kotlin.math.sqrt

object DeviceUtils {

    /**
     * Determines if the current device is a tablet based on screen size and density.
     * Uses multiple criteria to accurately detect tablets vs phones.
     */
    fun isTablet(context: Context): Boolean {
        val windowManager = context.getSystemService<WindowManager>()
        val displayMetrics = context.resources.displayMetrics
        val diagonalInches: Double

        // Use modern API if available, fallback to deprecated API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val widthPixels = bounds.width()
            val heightPixels = bounds.height()
            val xdpi = displayMetrics.xdpi
            val ydpi = displayMetrics.ydpi

            val widthInches = widthPixels / xdpi
            val heightInches = heightPixels / ydpi
            diagonalInches = sqrt((widthInches * widthInches + heightInches * heightInches).toDouble())
        } else {
            @Suppress("DEPRECATION")
            val oldMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(oldMetrics)

            val widthInches = oldMetrics.widthPixels / oldMetrics.xdpi
            val heightInches = oldMetrics.heightPixels / oldMetrics.ydpi
            diagonalInches = sqrt((widthInches * widthInches + heightInches * heightInches).toDouble())
        }

        // Check if device has tablet configuration
        val configuration = context.resources.configuration
        val isLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val isXLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE

        // A device is considered a tablet if:
        // 1. Screen diagonal is 7 inches or larger, OR
        // 2. Configuration indicates large or xlarge screen, OR
        // 3. Smallest width is 600dp or more (sw600dp)
        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp

        return diagonalInches >= 7.0 || isLargeScreen || isXLargeScreen || smallestWidthDp >= 600
    }
}
