package com.aurorashelf.app.data

import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientTransportFailureTest {
    @Test
    fun directSslAndEofFailuresAreTransient() {
        assertTrue(SSLException("TLS session was aborted").isTransientTransportFailure())
        assertTrue(SSLHandshakeException("handshake failed").isTransientTransportFailure())
        assertTrue(EOFException("unexpected end of stream").isTransientTransportFailure())
    }

    @Test
    fun nestedCauseChainIsDetected() {
        val nestedSsl = IOException("fetch failed", IllegalStateException("middle", SSLException("inner TLS abort")))
        assertTrue(nestedSsl.isTransientTransportFailure())

        val wrappedEof = IOException("read failed", EOFException("unexpected EOF"))
        assertTrue(wrappedEof.isTransientTransportFailure())
    }

    @Test
    fun unrelatedFailuresAreNotTransient() {
        assertFalse(IOException("connection refused").isTransientTransportFailure())
        assertFalse(SocketTimeoutException("read timed out").isTransientTransportFailure())
        assertFalse(IllegalStateException("parse error").isTransientTransportFailure())
    }
}
