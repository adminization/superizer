package cx.m42.superizer.host

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/** Seconds since 1970 as a Double, which is what Foundation keeps; milliseconds are what the rest of the host speaks. */
public actual object SystemClock : cx.m42.superizer.runtime.Clock {
    actual override fun now(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}
