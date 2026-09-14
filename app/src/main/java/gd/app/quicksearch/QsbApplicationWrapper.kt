package gd.app.quicksearch

import android.app.Application
import android.content.Context

class QsbApplicationWrapper : Application() {

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
