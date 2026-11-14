package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.adblock.AdBlockEngine
import com.example.adblock.BlockCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and branding strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Rehan Browser", context.getString(R.string.app_name))
        assertEquals("Made by Mohammad Rehan Yousuf", context.getString(R.string.brand_attribution))
    }

    @Test
    fun `adblock engine loads bundled easylist and blocks ads and trackers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = AdBlockEngine(context)
        engine.initialize(lastUpdatedMs = System.currentTimeMillis(), autoUpdate = false) {}
        // Allow brief initialization of bundled assets
        Thread.sleep(300)

        val adResult = engine.evaluateRequest("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", "example.com")
        assertTrue(adResult.blocked)
        assertEquals(BlockCategory.AD, adResult.category)

        val trackerResult = engine.evaluateRequest("https://www.google-analytics.com/analytics.js", "example.com")
        assertTrue(trackerResult.blocked)
        assertEquals(BlockCategory.TRACKER, trackerResult.category)

        val safeResult = engine.evaluateRequest("https://example.com/assets/app.js", "example.com")
        assertFalse(safeResult.blocked)
    }
}
