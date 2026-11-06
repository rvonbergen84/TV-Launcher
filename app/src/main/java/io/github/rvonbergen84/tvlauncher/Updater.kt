package io.github.rvonbergen84.tvlauncher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates the app from the APK published in the GitHub repo, so new test builds
 * can be installed from the Setup screen without typing a URL on the TV.
 */
object Updater {
    private const val BASE =
        "https://raw.githubusercontent.com/rvonbergen84/tv-launcher/claude/home-button-spike/dist/"
    private const val VERSION_URL = BASE + "version.txt"
    private const val APK_URL = BASE + "tv-launcher.apk"

    data class Release(val versionCode: Long, val versionName: String)

    fun installedVersion(context: Context): Release {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return Release(code, info.versionName ?: "?")
    }

    /** Reads dist/version.txt: line 1 is the version code, line 2 the version name. Network call. */
    fun fetchLatest(): Release? = runCatching {
        val lines = get(VERSION_URL).readBytes().decodeToString().lines()
        Release(lines[0].trim().toLong(), lines.getOrElse(1) { "" }.trim())
    }.getOrNull()

    /** True if the user has let this app install updates. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the one-time "allow this app to install apps" switch for TV Launcher. */
    fun openInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Prefs.log(context, "Couldn't open install permission screen") }
    }

    /** Downloads the APK and hands it to the system installer. Network call. */
    fun downloadAndInstall(context: Context) {
        val file = File(context.cacheDir, "update.apk")
        Prefs.log(context, "Update: downloading")
        get(APK_URL).use { input -> file.outputStream().use { input.copyTo(it) } }
        Prefs.log(context, "Update: downloaded ${file.length() / 1024} KB, installing")

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            // Skips the confirm prompt once this app is the one that installed itself.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java), flags,
            )
            session.commit(pending.intentSender)
        }
    }

    private fun get(url: String) =
        // GitHub's CDN caches files for a few minutes; a unique query string skips that.
        (URL("$url?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }.inputStream
}

/** Receives the installer's result; shows the confirm screen when the system asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        Prefs.log(
            context,
            if (status == PackageInstaller.STATUS_SUCCESS) "Update: installed" else "Update failed ($status) $message",
        )
    }
}
