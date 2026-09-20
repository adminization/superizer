package cx.m42.superizer.host

public actual object SystemClock : cx.m42.superizer.runtime.Clock {
    override fun now(): Long = System.currentTimeMillis()
}
