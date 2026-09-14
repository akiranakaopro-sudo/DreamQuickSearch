package gd.app.quicksearch.search.apps

import android.content.ComponentName

data class InstalledApp(
    val component: ComponentName,
    val label: String,
    val labelLower: String,
    val labelCompact: String,
    val pinyin: String,
    val pinyinCompact: String,
    val initials: String,
) {
    val packageName: String get() = component.packageName
}
