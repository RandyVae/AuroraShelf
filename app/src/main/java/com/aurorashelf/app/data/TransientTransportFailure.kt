package com.aurorashelf.app.data

import java.io.EOFException
import javax.net.ssl.SSLException

/**
 * True when this failure or any cause in its chain is a transient TLS/EOF transport failure.
 * Idempotent requests may be retried once; every other failure is propagated unchanged.
 */
internal fun Throwable.isTransientTransportFailure(): Boolean =
    generateSequence(this) { it.cause }.any { it is SSLException || it is EOFException }
