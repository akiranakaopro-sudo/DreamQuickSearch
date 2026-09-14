package gd.app.quicksearch.search.apps

import android.content.ComponentName
import gd.app.quicksearch.search.MatchKeys

data class InstalledApp(
    val component: ComponentName,
    val label: String,
    val keys: MatchKeys,
)
