package io.github.rvonbergen84.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service that tries two ways of making the Home button open our launcher:
 *
 * 1. Key catch: swallow KEYCODE_HOME before the system sees it and open our launcher.
 *    Some Google TV builds never pass the Home key to accessibility services.
 * 2. Redirect: when the stock home screen comes to the front, open ours on top of it.
 *    This is roughly what Projectivy's "override" option does.
 *
 * Both write to the diagnostic log so we can tell which one the TV allows.
 */
class HomeButtonService : AccessibilityService() {

    private var lastPackage = ""
    private var lastClass = ""

    override fun onServiceConnected() {
        Prefs.log(this, "Service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // Log button codes only, so we learn what the remote's Home button sends.
            Prefs.log(this, "Key ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})")
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME && Prefs.catchHomeKey(this)) {
            if (event.action == KeyEvent.ACTION_UP) {
                Prefs.log(this, "Home caught -> opening TV Launcher")
                openLauncher(this)
            }
            return true // swallow both down and up so the stock home doesn't open
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""
        if (pkg != lastPackage || cls != lastClass) {
            // Shows what comes to the front, e.g. which app Home really opens on this TV.
            if (pkg != packageName) Prefs.log(this, "Front: $pkg / $cls")
            lastPackage = pkg
            lastClass = cls
        }
        if (pkg !in STOCK_LAUNCHERS) return

        // Only replace the home screen itself. The stock launcher package also
        // draws things like the settings and input panels, which must keep working.
        if (isStockHomeScreen(pkg, cls) && Prefs.redirectStockHome(this) && !Prefs.bypassActive(this)) {
            Prefs.log(this, "Stock home seen -> opening TV Launcher")
            openLauncher(this)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx", // Google TV
            "com.google.android.tvlauncher",        // Android TV (2018+)
            "com.google.android.leanbacklauncher",  // Older Android TV
        )

        fun isStockHomeScreen(pkg: String, cls: String): Boolean = when (pkg) {
            "com.google.android.apps.tv.launcherx" -> cls.endsWith("HomeActivity")
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher" -> cls.endsWith(".MainActivity")
            else -> false
        }

        fun openLauncher(context: Context) {
            val intent = Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            context.startActivity(intent)
        }

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeButtonService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
