package com.aurorashelf.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchPolicyTest {
    @Test fun prefetchRunsOnlyWhenEnabledAndUnmetered() {
        assertTrue(shouldPrefetch(enabled = true, isMetered = false))
    }

    @Test fun disabledSettingAlwaysBlocksPrefetch() {
        assertFalse(shouldPrefetch(enabled = false, isMetered = false))
        assertFalse(shouldPrefetch(enabled = false, isMetered = true))
    }

    @Test fun meteredNetworkAlwaysBlocksPrefetch() {
        assertFalse(shouldPrefetch(enabled = true, isMetered = true))
    }
}
