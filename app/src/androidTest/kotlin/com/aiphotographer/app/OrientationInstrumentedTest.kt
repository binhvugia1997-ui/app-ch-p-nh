package com.aiphotographer.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationInstrumentedTest {
    @Test fun launcherRecreationAndObsoleteFlagsNeverLockOrientation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        for (obsoleteFlag in listOf(false, true)) {
            context.getSharedPreferences("phase1_verification", Context.MODE_PRIVATE)
                .edit(commit = true) { putBoolean("allowLandscape", obsoleteFlag) }
            val intent = Intent(context, MainActivity::class.java).putExtra("allowLandscape", obsoleteFlag)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                repeat(3) {
                    scenario.onActivity { activity ->
                        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
                        assertEquals(context.resources.configuration.orientation, activity.resources.configuration.orientation)
                    }
                    scenario.recreate()
                }
                scenario.onActivity { activity ->
                    activity.startActivity(Intent(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("allowLandscape", !obsoleteFlag))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, it.requestedOrientation) }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, it.requestedOrientation) }
        }
    }
}
