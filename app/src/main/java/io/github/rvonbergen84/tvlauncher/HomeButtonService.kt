package io.github.rvonbergen84.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewConfiguration
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

    private val handler = Handler(Looper.getMainLooper())
    private var holdFired = false
    private val onHold = Runnable {
        holdFired = true
        Prefs.log(this, "Home held -> opening quick panel")
        openQuickPanel(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // Log button codes only, so we learn what the remote's buttons send.
            Prefs.log(this, "Key ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})")
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME && Prefs.catchHomeKey(this)) {
            // We swallow Home so the stock home screen never opens. That also hides
            // the press from the system, so we handle tap vs. hold ourselves.
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                    holdFired = false
                    handler.postDelayed(onHold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(onHold)
                    if (!holdFired) {
                        Prefs.log(this, "Home caught -> opening TV Launcher")
                        openLauncher(this)
                    }
                }
            }
            return true
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

        /**
         * What holding Home normally does on Google TV: the side panel with settings,
         * inputs and profiles. It lives inside the stock launcher, so we look for an
         * activity there that other apps may open. If there isn't one, open TV settings.
         */
        fun openQuickPanel(context: Context) {
            val panel = findStockPanel(context)
            if (panel != null) {
                // The panel may be drawn by the stock home activity; don't redirect away from it.
                Prefs.startBypass(context, 5_000)
                val opened = runCatching {
                    context.startActivity(
                        Intent().setComponent(panel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.isSuccess
                Prefs.log(context, "Panel ${panel.shortClassName}: ${if (opened) "opened" else "refused"}")
                if (opened) return
            }
            Prefs.log(context, "No stock panel available -> TV settings")
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        private fun findStockPanel(context: Context): ComponentName? {
            val pm = context.packageManager
            for (pkg in STOCK_LAUNCHERS) {
                val info = runCatching {
                    pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                }.getOrNull() ?: continue
                val match = info.activities.orEmpty().firstOrNull {
                    it.exported && it.permission == null &&
                        it.name.contains("dashboard", ignoreCase = true)
                }
                if (match != null) return ComponentName(pkg, match.name)
            }
            return null
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
