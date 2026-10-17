package cz.talkative.app.domain.util

/** Zdroj času, aby šel v testech nahradit pevným časem. */
fun interface Clock {
    fun nowMillis(): Long
}
