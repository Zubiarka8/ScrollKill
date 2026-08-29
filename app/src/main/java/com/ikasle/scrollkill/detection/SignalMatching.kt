package com.ikasle.scrollkill.detection

/**
 * Case-insensitive "does any observed value contain any of these tokens" test,
 * shared by [AppDetector] implementations for token-based signal matching.
 */
internal fun Iterable<String>.containsAnyToken(tokens: List<String>): Boolean =
    any { value -> tokens.any { value.contains(it, ignoreCase = true) } }
