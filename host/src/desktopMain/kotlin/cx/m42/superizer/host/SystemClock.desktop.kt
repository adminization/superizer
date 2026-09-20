package cx.m42.superizer.host

public actual object SystemClock : cx.m42.superizer.runtime.Clock {
    actual override fun now(): Long = System.currentTimeMillis()
}
