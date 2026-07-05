package com.spatialkit.engine

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class FallbackEngineTest {
    @Test
    fun startsUninitialized() {
        assertEquals(EngineState.Uninitialized, FallbackEngine().state.value)
    }

    @Test
    fun capabilitiesReportNothingSupported() {
        val capabilities = FallbackEngine().capabilities
        assertFalse(capabilities.supportsAR)
        assertFalse(capabilities.supportsVR)
        assertFalse(capabilities.supportsPlaneDetection)
        assertFalse(capabilities.supportsSpatialAnchors)
    }

    @Test
    fun initializeTransitionsToNotSupportedError() =
        runTest {
            val engine = FallbackEngine()
            engine.initialize(EngineConfig(preferredMode = SessionMode.AR))
            val state = assertIs<EngineState.Error>(engine.state.value)
            assertEquals(EngineError.NotSupported, state.error)
        }

    @Test
    fun startSessionTransitionsToNotSupportedError() =
        runTest {
            val engine = FallbackEngine()
            engine.startSession(SessionMode.AR)
            val state = assertIs<EngineState.Error>(engine.state.value)
            assertEquals(EngineError.NotSupported, state.error)
        }

    @Test
    fun stopSessionStaysInError() =
        runTest {
            // Regression test: stopSession must not report Ready — a
            // never-supported engine reaching Ready would defeat
            // SpatialRuntime's state checks.
            val engine = FallbackEngine()
            engine.initialize(EngineConfig(preferredMode = SessionMode.AR))
            engine.stopSession()
            val state = assertIs<EngineState.Error>(engine.state.value)
            assertEquals(EngineError.NotSupported, state.error)
        }

    @Test
    fun disposeTransitionsToDisposed() =
        runTest {
            val engine = FallbackEngine()
            engine.initialize(EngineConfig(preferredMode = SessionMode.AR))
            engine.dispose()
            assertEquals(EngineState.Disposed, engine.state.value)
        }

    @Test
    fun queriesReturnEmptyResults() {
        val engine = FallbackEngine()
        assertNull(engine.performHitTest(0.5f, 0.5f))
        assertNull(engine.performRaycast(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f)))
    }
}
