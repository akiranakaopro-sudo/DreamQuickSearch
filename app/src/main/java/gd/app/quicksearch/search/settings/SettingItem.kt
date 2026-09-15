package gd.app.quicksearch.search.settings

import android.content.ComponentName
import android.content.Intent
import gd.app.quicksearch.search.MatchKeys

data class SettingItem(
    val id: String,
    val label: String,
    val path: String,
    val action: String?,
    val targetPackage: String?,
    val targetClass: String?,
    val iconPackage: String?,
    val iconRes: Int,
    val keys: MatchKeys,
) {
    fun launchIntent(): Intent? {
        val intent = when {
            !action.isNullOrEmpty() -> Intent(action)
            !targetPackage.isNullOrEmpty() && !targetClass.isNullOrEmpty() -> Intent()
            else -> return null
        }
        if (!targetPackage.isNullOrEmpty() && !targetClass.isNullOrEmpty()) {
            intent.component = ComponentName(targetPackage, targetClass)
        } else if (!targetPackage.isNullOrEmpty()) {
            intent.setPackage(targetPackage)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
