package gd.app.quicksearch.search.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import gd.app.quicksearch.R
import gd.app.quicksearch.search.SearchText
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executor

class SettingsIndex(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    @Volatile
    private var dirty = true
    private var indexedLocale: String = ""
    private var snapshot: List<SettingItem> = emptyList()

    fun invalidate() {
        dirty = true
    }

    fun warm(executor: Executor) {
        executor.execute { items() }
    }

    fun search(query: String): List<SettingItem> {
        val needle = SearchText.needle(query)
        if (needle.isEmpty()) {
            return emptyList()
        }
        val compact = SearchText.compact(needle)
        val ranked = ArrayList<Pair<Int, SettingItem>>()
        for (item in items()) {
            val rank = SearchText.rank(item.keys, needle, compact) ?: continue
            ranked += rank to item
        }
        ranked.sortWith { a, b ->
            val rankCmp = a.first.compareTo(b.first)
            if (rankCmp != 0) rankCmp else collator.compare(a.second.label, b.second.label)
        }
        if (ranked.size <= MAX_RESULTS) {
            return ranked.map { it.second }
        }
        return ranked.subList(0, MAX_RESULTS).map { it.second }
    }

    private fun items(): List<SettingItem> {
        val locale = Locale.getDefault().toString()
        if (!dirty && locale == indexedLocale) {
            return snapshot
        }
        synchronized(lock) {
            val currentLocale = Locale.getDefault().toString()
            if (!dirty && currentLocale == indexedLocale) {
                return snapshot
            }
            dirty = false
            indexedLocale = currentLocale
            snapshot = loadLocked()
            return snapshot
        }
    }

    private fun loadLocked(): List<SettingItem> {
        val started = System.nanoTime()
        val settingsLabel = genericSettingsLabel(appContext.packageManager)
        val merged = LinkedHashMap<String, SettingItem>()
        for (item in loadFromProviders(settingsLabel)) {
            merged.putIfAbsent(mergeKey(item), item)
        }
        for (item in loadFromCatalog(settingsLabel)) {
            merged.putIfAbsent(mergeKey(item), item)
        }
        val items = merged.values.toMutableList()
        items.sortWith { a, b -> collator.compare(a.label, b.label) }
        Log.d(TAG, "indexed ${items.size} settings in ${(System.nanoTime() - started) / 1_000_000}ms")
        return items
    }

    private fun mergeKey(item: SettingItem): String {
        return item.action?.takeIf { it.isNotEmpty() } ?: item.id
    }

    private fun loadFromProviders(settingsLabel: String): List<SettingItem> {
        val items = ArrayList<SettingItem>()
        val seen = HashSet<String>()
        for (authority in PROVIDER_AUTHORITIES) {
            val uri = Uri.parse("content://$authority/$INDEXABLES_RAW_PATH")
            val cursor = runCatching {
                appContext.contentResolver.query(uri, null, null, null, null)
            }.getOrNull() ?: continue
            val siteMap = loadSiteMap(authority)
            cursor.use { rows ->
                val titleIdx = rows.getColumnIndex(COL_TITLE)
                if (titleIdx < 0) {
                    return@use
                }
                val keyIdx = rows.getColumnIndex(COL_KEY)
                val keywordsIdx = rows.getColumnIndex(COL_KEYWORDS)
                val actionIdx = rows.getColumnIndex(COL_INTENT_ACTION)
                val packageIdx = rows.getColumnIndex(COL_INTENT_TARGET_PACKAGE)
                val classIdx = rows.getColumnIndex(COL_INTENT_TARGET_CLASS)
                val iconIdx = rows.getColumnIndex(COL_ICON_RESID)
                val screenIdx = rows.getColumnIndex(COL_SCREEN_TITLE)
                val classNameIdx = rows.getColumnIndex(COL_CLASS_NAME)
                val pathIdx = rows.getColumnIndex(COL_PATH)
                while (rows.moveToNext()) {
                    val title = rows.stringAt(titleIdx)?.trim().orEmpty()
                    if (title.isEmpty()) {
                        continue
                    }
                    val action = rows.stringAt(actionIdx)
                    val targetPackage = rows.stringAt(packageIdx)
                    val targetClass = rows.stringAt(classIdx)
                    if (action.isNullOrEmpty() && (targetPackage.isNullOrEmpty() || targetClass.isNullOrEmpty())) {
                        continue
                    }
                    val key = rows.stringAt(keyIdx)
                    val id = if (!key.isNullOrEmpty()) {
                        key
                    } else {
                        listOfNotNull(action, targetPackage, targetClass).joinToString("/")
                    }
                    if (!seen.add(id)) {
                        continue
                    }
                    val path = settingPath(
                        settingsLabel,
                        rawPath = rows.stringAt(pathIdx),
                        className = rows.stringAt(classNameIdx),
                        screenTitle = rows.stringAt(screenIdx),
                        siteMap = siteMap,
                    )
                    val keywords = listOfNotNull(rows.stringAt(keywordsIdx), path)
                        .filter { it.isNotBlank() }
                        .joinToString(",")
                    items += SettingItem(
                        id = id,
                        label = title,
                        path = path,
                        action = action,
                        targetPackage = targetPackage,
                        targetClass = targetClass,
                        iconPackage = targetPackage,
                        iconRes = if (iconIdx >= 0) rows.getInt(iconIdx) else 0,
                        keys = SearchText.keys(title, keywords),
                    )
                }
            }
            if (items.isNotEmpty()) {
                Log.d(TAG, "loaded ${items.size} settings from $authority")
                break
            }
        }
        return items
    }

    private fun Cursor.stringAt(index: Int): String? {
        if (index < 0 || isNull(index)) {
            return null
        }
        return getString(index)
    }

    private fun loadFromCatalog(settingsLabel: String): List<SettingItem> {
        val pm = appContext.packageManager
        val items = ArrayList<SettingItem>(CATALOG.size)
        for (spec in CATALOG) {
            if (Build.VERSION.SDK_INT < spec.minSdk) {
                continue
            }
            val intent = Intent(spec.action)
            val resolved = resolve(pm, intent) ?: continue
            val activity = resolved.activityInfo ?: continue
            val resolvedLabel = resolved.loadLabel(pm)?.toString()?.trim().orEmpty()
            val label = if (resolvedLabel.isNotEmpty() && !resolvedLabel.equals(settingsLabel, ignoreCase = true)) {
                resolvedLabel
            } else {
                appContext.getString(spec.labelRes)
            }
            val parent = if (spec.pathParentRes != 0) appContext.getString(spec.pathParentRes) else ""
            val path = joinPath(settingsLabel, parent, label)
            val keywords = listOf(
                if (spec.keywordsRes != 0) appContext.getString(spec.keywordsRes) else "",
                path,
            ).filter { it.isNotBlank() }.joinToString(",")
            items += SettingItem(
                id = spec.action,
                label = label,
                path = path,
                action = spec.action,
                targetPackage = activity.packageName,
                targetClass = null,
                iconPackage = activity.packageName,
                iconRes = 0,
                keys = SearchText.keys(label, keywords),
            )
        }
        return items
    }

    private fun loadSiteMap(authority: String): Map<String, SiteParent> {
        val uri = Uri.parse("content://$authority/$SITE_MAP_PAIRS_PATH")
        val cursor = runCatching {
            appContext.contentResolver.query(uri, null, null, null, null)
        }.getOrNull() ?: return emptyMap()
        val map = HashMap<String, SiteParent>()
        cursor.use { rows ->
            val childClassIdx = rows.getColumnIndex(COL_CHILD_CLASS)
            val parentClassIdx = rows.getColumnIndex(COL_PARENT_CLASS)
            val parentTitleIdx = rows.getColumnIndex(COL_PARENT_TITLE)
            if (childClassIdx < 0 || parentClassIdx < 0) {
                return@use
            }
            while (rows.moveToNext()) {
                val childClass = rows.stringAt(childClassIdx)?.trim().orEmpty()
                if (childClass.isEmpty() || map.containsKey(childClass)) {
                    continue
                }
                map[childClass] = SiteParent(
                    className = rows.stringAt(parentClassIdx)?.trim().orEmpty(),
                    title = rows.stringAt(parentTitleIdx)?.trim().orEmpty(),
                )
            }
        }
        return map
    }

    private fun settingPath(
        settingsLabel: String,
        rawPath: String?,
        className: String?,
        screenTitle: String?,
        siteMap: Map<String, SiteParent>,
    ): String {
        if (!rawPath.isNullOrBlank()) {
            return joinPath(settingsLabel, *splitPath(rawPath))
        }
        val chain = ArrayList<String>()
        var current = className?.trim().orEmpty()
        val seen = HashSet<String>()
        while (current.isNotEmpty() && seen.add(current)) {
            val parent = siteMap[current] ?: break
            if (parent.title.isNotBlank()) {
                chain += parent.title
            }
            current = parent.className
        }
        chain.reverse()
        return joinPath(settingsLabel, *chain.toTypedArray(), screenTitle.orEmpty())
    }

    private fun splitPath(raw: String): Array<String> {
        return raw.split(*PATH_SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()
    }

    private fun joinPath(vararg segments: String): String {
        val out = ArrayList<String>(segments.size)
        for (segment in segments) {
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) {
                continue
            }
            if (out.lastOrNull()?.equals(trimmed, ignoreCase = true) == true) {
                continue
            }
            out += trimmed
        }
        return out.joinToString(" > ")
    }

    private fun genericSettingsLabel(pm: PackageManager): String {
        val resolved = resolve(pm, Intent(Settings.ACTION_SETTINGS))
        val appLabel = resolved?.activityInfo?.applicationInfo?.loadLabel(pm)?.toString()
        val activityLabel = resolved?.loadLabel(pm)?.toString()
        return appLabel ?: activityLabel ?: appContext.getString(R.string.search_section_settings)
    }

    private fun resolve(pm: PackageManager, intent: Intent) = if (Build.VERSION.SDK_INT >= 33) {
        pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.resolveActivity(intent, 0)
    }

    private class Spec(
        val action: String,
        val labelRes: Int,
        val keywordsRes: Int = 0,
        val minSdk: Int = 26,
        val pathParentRes: Int = 0,
    )

    private class SiteParent(
        val className: String,
        val title: String,
    )

    companion object {
        private const val TAG = "SettingsSearch"
        private const val MAX_RESULTS = 20
        private const val INDEXABLES_RAW_PATH = "settings/indexables_raw"
        private const val SITE_MAP_PAIRS_PATH = "settings/site_map_pairs"
        private const val COL_TITLE = "title"
        private const val COL_KEY = "key"
        private const val COL_KEYWORDS = "keywords"
        private const val COL_INTENT_ACTION = "intentAction"
        private const val COL_INTENT_TARGET_PACKAGE = "intentTargetPackage"
        private const val COL_INTENT_TARGET_CLASS = "intentTargetClass"
        private const val COL_ICON_RESID = "iconResId"
        private const val COL_SCREEN_TITLE = "screenTitle"
        private const val COL_CLASS_NAME = "className"
        private const val COL_PATH = "path"
        private const val COL_PARENT_CLASS = "parent_class"
        private const val COL_CHILD_CLASS = "child_class"
        private const val COL_PARENT_TITLE = "parent_title"
        private val PATH_SEPARATORS = arrayOf(">", "/", "›", "／", "|")
        private val PROVIDER_AUTHORITIES = arrayOf(
            "com.android.settings",
            "com.oplus.settings",
            "com.coloros.settings",
        )
        private val CATALOG = listOf(
            Spec(Settings.ACTION_WIFI_SETTINGS, R.string.setting_wifi, R.string.setting_wifi_keywords, pathParentRes = R.string.setting_wireless),
            Spec(Settings.ACTION_BLUETOOTH_SETTINGS, R.string.setting_bluetooth, R.string.setting_bluetooth_keywords),
            Spec(Settings.ACTION_AIRPLANE_MODE_SETTINGS, R.string.setting_airplane, R.string.setting_airplane_keywords, pathParentRes = R.string.setting_wireless),
            Spec(Settings.ACTION_WIRELESS_SETTINGS, R.string.setting_wireless, R.string.setting_wireless_keywords),
            Spec(Settings.ACTION_DATA_ROAMING_SETTINGS, R.string.setting_mobile_network, R.string.setting_mobile_network_keywords, pathParentRes = R.string.setting_wireless),
            Spec(Settings.ACTION_NETWORK_OPERATOR_SETTINGS, R.string.setting_network_operators, R.string.setting_mobile_network_keywords, pathParentRes = R.string.setting_wireless),
            Spec(Settings.ACTION_APN_SETTINGS, R.string.setting_apn, R.string.setting_apn_keywords, pathParentRes = R.string.setting_wireless),
            Spec(Settings.ACTION_NFC_SETTINGS, R.string.setting_nfc, R.string.setting_nfc_keywords, pathParentRes = R.string.setting_wireless),
            Spec("android.settings.TETHER_SETTINGS", R.string.setting_hotspot, R.string.setting_hotspot_keywords, pathParentRes = R.string.setting_wireless),
            Spec(Settings.ACTION_DISPLAY_SETTINGS, R.string.setting_display, R.string.setting_display_keywords),
            Spec(Settings.ACTION_NIGHT_DISPLAY_SETTINGS, R.string.setting_night_light, R.string.setting_night_light_keywords, pathParentRes = R.string.setting_display),
            Spec("android.settings.DARK_THEME_SETTINGS", R.string.setting_dark_mode, R.string.setting_dark_mode_keywords, minSdk = 29, pathParentRes = R.string.setting_display),
            Spec(Settings.ACTION_SOUND_SETTINGS, R.string.setting_sound, R.string.setting_sound_keywords),
            Spec(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, R.string.setting_storage, R.string.setting_storage_keywords),
            Spec(Settings.ACTION_APPLICATION_SETTINGS, R.string.setting_apps, R.string.setting_apps_keywords),
            Spec(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, R.string.setting_all_apps, R.string.setting_apps_keywords, pathParentRes = R.string.setting_apps),
            Spec(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, R.string.setting_default_apps, R.string.setting_default_apps_keywords, pathParentRes = R.string.setting_apps),
            Spec(Settings.ACTION_BATTERY_SAVER_SETTINGS, R.string.setting_battery, R.string.setting_battery_keywords),
            Spec(Settings.ACTION_LOCATION_SOURCE_SETTINGS, R.string.setting_location, R.string.setting_location_keywords),
            Spec(Settings.ACTION_SECURITY_SETTINGS, R.string.setting_security, R.string.setting_security_keywords),
            Spec(Settings.ACTION_PRIVACY_SETTINGS, R.string.setting_privacy, R.string.setting_privacy_keywords),
            Spec(Settings.ACTION_SYNC_SETTINGS, R.string.setting_accounts, R.string.setting_accounts_keywords),
            Spec(Settings.ACTION_DATE_SETTINGS, R.string.setting_date, R.string.setting_date_keywords),
            Spec(Settings.ACTION_LOCALE_SETTINGS, R.string.setting_language, R.string.setting_language_keywords),
            Spec(Settings.ACTION_INPUT_METHOD_SETTINGS, R.string.setting_keyboard, R.string.setting_keyboard_keywords, pathParentRes = R.string.setting_language),
            Spec(Settings.ACTION_ACCESSIBILITY_SETTINGS, R.string.setting_accessibility, R.string.setting_accessibility_keywords),
            Spec(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, R.string.setting_dnd, R.string.setting_dnd_keywords, pathParentRes = R.string.setting_notifications),
            Spec("android.settings.NOTIFICATION_SETTINGS", R.string.setting_notifications, R.string.setting_notifications_keywords),
            Spec(Settings.ACTION_HOME_SETTINGS, R.string.setting_home, R.string.setting_home_keywords, pathParentRes = R.string.setting_apps),
            Spec(Settings.ACTION_DEVICE_INFO_SETTINGS, R.string.setting_about, R.string.setting_about_keywords),
            Spec(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, R.string.setting_developer, R.string.setting_developer_keywords, pathParentRes = R.string.setting_about),
            Spec(Settings.ACTION_SEARCH_SETTINGS, R.string.setting_search, R.string.setting_search_keywords),
            Spec(Settings.ACTION_PRINT_SETTINGS, R.string.setting_print, R.string.setting_print_keywords),
            Spec(Settings.ACTION_CAPTIONING_SETTINGS, R.string.setting_captions, R.string.setting_captions_keywords, pathParentRes = R.string.setting_accessibility),
            Spec(Settings.ACTION_DREAM_SETTINGS, R.string.setting_screensaver, R.string.setting_screensaver_keywords, pathParentRes = R.string.setting_display),
            Spec(Settings.ACTION_USAGE_ACCESS_SETTINGS, R.string.setting_usage_access, R.string.setting_usage_access_keywords, pathParentRes = R.string.setting_apps),
            Spec(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, R.string.setting_battery_optimization, R.string.setting_battery_keywords, pathParentRes = R.string.setting_battery),
            Spec(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, R.string.setting_overlay, R.string.setting_overlay_keywords, pathParentRes = R.string.setting_apps),
            Spec(Settings.ACTION_DATA_USAGE_SETTINGS, R.string.setting_data_usage, R.string.setting_data_usage_keywords, minSdk = 28, pathParentRes = R.string.setting_wireless),
            Spec(Settings.ACTION_AUTO_ROTATE_SETTINGS, R.string.setting_auto_rotate, R.string.setting_auto_rotate_keywords, minSdk = 31, pathParentRes = R.string.setting_display),
        )
    }
}
