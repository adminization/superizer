package cx.m42.superizer.host.platform

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Platform
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public actual fun currentPlatform(): Platform = Platform.Android

/**
 * Everything on Android that needs a `Context`, wired once by the host's activity or application.
 *
 * A single init rather than one per service: each of these is a process-wide system object, and
 * three separate `init(context)` calls is three chances to forget one.
 */
public object AndroidHost {
    internal var vibrator: Vibrator? = null
    internal var appContext: Context? = null

    public fun init(context: Context) {
        // The application context, not the activity: these outlive any one activity, and holding
        // an activity here would leak it across a rotation.
        val app = context.applicationContext
        appContext = app
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // getSystemService(VIBRATOR_SERVICE) still works on API 31+ but is deprecated, and on a
            // multi-motor device it picks an arbitrary one; the manager names the default.
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        PlatformLifecycle.attach()
        Connectivity.attach(app)
    }
}

internal actual fun platformHapticTick() {
    val vibrator = AndroidHost.vibrator ?: return
    // hasVibrator() is false on tablets and emulators without a motor; vibrate() would be a silent
    // no-op there anyway, but checking keeps the intent clear.
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // A predefined effect is tuned per device, so it feels like the rest of the system rather
        // than a raw buzz of a duration we picked.
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(20L)
    }
}

/**
 * `Locale.getDefault()` follows the system setting, including a per-app language chosen in Android
 * 13's own settings — which is exactly the switch a reader would expect to work.
 */
public actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()

public actual object PlatformLifecycle {
    private val _state = MutableStateFlow(HostLifecycle.Foreground)
    public actual val state: StateFlow<HostLifecycle> = _state.asStateFlow()

    private var attached = false

    internal fun attach() {
        if (attached) return
        attached = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                // ON_START / ON_STOP, not RESUME / PAUSE: a permission dialog pauses the activity,
                // and a snapshot written on every one of those is a snapshot written on every tap.
                override fun onStart(owner: LifecycleOwner) {
                    _state.value = HostLifecycle.Foreground
                }

                override fun onStop(owner: LifecycleOwner) {
                    _state.value = HostLifecycle.Background
                }
            },
        )
    }
}

public actual fun openExternalUrl(url: String): Boolean {
    val context = AndroidHost.appContext ?: return false
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}

public actual object Connectivity {
    private val _online = MutableStateFlow(true)
    public actual val online: StateFlow<Boolean> = _online.asStateFlow()

    private var attached = false

    internal fun attach(context: Context) {
        if (attached) return
        attached = true
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        _online.value = manager.activeNetwork
            ?.let { manager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        runCatching {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _online.value = true
                    }

                    override fun onLost(network: Network) {
                        _online.value = false
                    }
                },
            )
        }
    }
}
