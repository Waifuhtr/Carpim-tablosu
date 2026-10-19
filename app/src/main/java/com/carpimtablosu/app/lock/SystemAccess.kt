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

    /**
     * Hiçbir zaman kilitlenmeyecek paketler: ayarlar, telefon, sistem arayüzü ve TV'nin
     * ana ekranı/girişleri. Bunlardan biri kilitlenirse cihaz kullanılamaz hale gelebilir
     * (ör. ana ekran kilitlenirse sonsuz "ana ekrana dön" döngüsü olur).
     */
    private val NEVER_LOCK = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
        // Android TV / Google TV
        "com.android.tv.settings",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher",
        "com.android.tv",
        "com.google.android.tv",
        "com.google.android.tvrecommendations",
        "com.google.android.katniss",
        "com.google.android.apps.tv.dreamx",
        "com.android.providers.tv",
    )

    /**
     * Kilitlenmemesi gereken tüm paketler: sabit liste + bu cihazdaki ana ekran ve
     * ayarlar uygulamaları (üreticiye göre değişir) + bu uygulama + varsayılan arama uygulaması.
     */
    fun neverLockPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val out = NEVER_LOCK.toMutableSet()
        out += context.packageName
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let { out += it }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        out += query(pm, home).map { it.activityInfo.packageName }
        out += query(pm, Intent(Settings.ACTION_SETTINGS)).map { it.activityInfo.packageName }
        return out
    }

    private fun query(pm: PackageManager, intent: Intent) = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }.getOrDefault(emptyList())

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

    /**
     * Ana ekranda simgesi olan, kilitlenebilir uygulamalar. Telefon uygulamaları LAUNCHER,
     * TV uygulamaları LEANBACK_LAUNCHER kategorisindedir; ikisi de listelenir.
     */
    fun launchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val phone = query(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        val tv = query(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER))
        val never = neverLockPackages(context)
        return (tv + phone)
            .map { it.activityInfo.packageName to it }
            .distinctBy { it.first }
            .filter { (pkg, _) -> pkg !in never }
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
