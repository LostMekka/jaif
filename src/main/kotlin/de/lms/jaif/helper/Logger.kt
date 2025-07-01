package de.lms.jaif.helper

/**
 * Used for logging on levels that might be off.
 * Only computes the arg if toString() is called.
 * This way, a value that is expensive to compute is only evaluated if needed.
 */
internal fun lazyLogArg(get: () -> Any) = object {
    private val value by lazy(get)
    override fun toString(): String = value.toString()
}
