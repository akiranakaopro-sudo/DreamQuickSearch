package gd.app.quicksearch

import android.app.Application
import android.content.Context
import gd.app.quicksearch.search.apps.InstalledAppIndex

class QsbApplicationWrapper : Application() {

    val installedApps by lazy { InstalledAppIndex(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: QsbApplicationWrapper

        fun app(): QsbApplicationWrapper = instance

        fun appContext(): Context = instance.applicationContext
    }
}
