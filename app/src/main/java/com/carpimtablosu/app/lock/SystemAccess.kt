package com.carpimtablosu.app.lock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.TextUtils

data class InstalledApp(val pkg: String, val label: String, val icon: Drawable?)

/** Sistem ayarları ve izinlerle ilgili yardımcılar. */
object SystemAccess {

    /** Kilit listesine hiçbir zaman girmemesi gereken paketler (ayarlar, telefon). */
    private val NEVER_LOCK = setOf(
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
    )

    /** İçinde uygulamamızın adı görünürse şifre istenecek sistem ekranları. */
    val PROTECTED_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.miui.securitycenter",
        "com.miui.packageinstaller",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.huawei.systemmanager",
    )

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppLockService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val cn = ComponentName.unflattenFromString(splitter.next()) ?: continue
            if (cn == expected) return true
        }
        return false
    }

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Android 13+: "Kısıtlanmış ayarlara izin ver" seçeneği bu ekrandaki ⋮ menüsündedir. */
    fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:" + context.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isIgnoringBattery(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun adminComponent(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent(context))

    fun addAdminIntent(context: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Uygulamanın ebeveyn şifresi olmadan kaldırılmasını engeller."
        )

    fun removeAdmin(context: Context) {
        context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(adminComponent(context))
    }

    fun notificationsAllowed(context: Context): Boolean =
        context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()

    fun isXiaomi(): Boolean = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun xiaomiAutostart(): Intent = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun needsRestrictedSettingsHint(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Ana ekranda simgesi olan, kilitlenebilir uygulamalar. */
    fun launchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val dialer = runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
        return resolved
            .map { it.activityInfo.packageName to it }
            .distinctBy { it.first }
            .filter { (pkg, _) -> pkg != context.packageName && pkg != dialer && pkg !in NEVER_LOCK }
            .map { (pkg, info) ->
                InstalledApp(pkg, info.loadLabel(pm).toString(), runCatching { info.loadIcon(pm) }.getOrNull())
            }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
