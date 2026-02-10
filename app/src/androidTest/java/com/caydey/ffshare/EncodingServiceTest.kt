package com.caydey.ffshare

import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.caydey.ffshare.service.EncodingService
import com.caydey.ffshare.service.EncodingState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeoutException

/**
 * Instrumented tests for EncodingService.
 * Tests foreground service behavior and encoding state management.
 */
@RunWith(AndroidJUnit4::class)
class EncodingServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun service_canBeBound() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serviceIntent = Intent(context, EncodingService::class.java)

        try {
            val binder: IBinder = serviceRule.bindService(serviceIntent)
            val service = (binder as EncodingService.LocalBinder).getService()
            assertNotNull(service)
        } catch (e: TimeoutException) {
            fail("Service binding timed out: ${e.message}")
        }
    }

    @Test
    fun service_initialState_isIdle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serviceIntent = Intent(context, EncodingService::class.java)

        try {
            val binder: IBinder = serviceRule.bindService(serviceIntent)
            val service = (binder as EncodingService.LocalBinder).getService()

            assertEquals(EncodingState.Idle, service.encodingState.value)
        } catch (e: TimeoutException) {
            fail("Service binding timed out: ${e.message}")
        }
    }

    @Test
    fun service_canBeStartedAsForeground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serviceIntent = Intent(context, EncodingService::class.java)

        try {
            // Start the service (this will call onStartCommand which should call startForeground)
            serviceRule.startService(serviceIntent)

            // Bind to verify service is running
            val binder: IBinder = serviceRule.bindService(serviceIntent)
            val service = (binder as EncodingService.LocalBinder).getService()
            assertNotNull(service)
        } catch (e: TimeoutException) {
            fail("Service start/binding timed out: ${e.message}")
        }
    }
}
