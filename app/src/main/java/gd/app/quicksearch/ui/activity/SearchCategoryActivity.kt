package gd.app.quicksearch.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.quicksearch.QsbApplicationWrapper
import gd.app.quicksearch.R
import gd.app.quicksearch.databinding.ActivitySearchCategoryBinding
import gd.app.quicksearch.search.SearchCategory
import gd.app.quicksearch.ui.home.SearchAppAdapter
import gd.app.quicksearch.ui.home.SearchCalendarAdapter
import gd.app.quicksearch.ui.home.SearchContactsAdapter
import gd.app.quicksearch.ui.home.SearchFilesAdapter
import gd.app.quicksearch.ui.home.SearchHomeBackdrop
import gd.app.quicksearch.ui.home.SearchMessagesAdapter
import gd.app.quicksearch.ui.home.SearchNotesAdapter
import gd.app.quicksearch.ui.home.SearchSettingsAdapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Full result list for one category, opened from a section's "More" button. */
class SearchCategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchCategoryBinding
    private lateinit var launcher: SearchLauncher
    private val main = Handler(Looper.getMainLooper())
    private var worker: ExecutorService? = null
    private var backdrop: SearchHomeBackdrop? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val category = SearchCategory.of(intent?.getStringExtra(EXTRA_CATEGORY))
        val query = intent?.getStringExtra(EXTRA_QUERY)?.trim().orEmpty()
        if (category == null || query.isEmpty()) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SearchHomeBackdrop.applyCachedToWindow(this)
        binding = ActivitySearchCategoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        backdrop = SearchHomeBackdrop(
            this,
            binding.blurBackdrop,
            binding.blurScrim,
            binding.blurLayer,
        ).also { it.apply() }
        launcher = SearchLauncher(this)
        binding.categoryTitle.text = getString(category.titleRes)
        binding.categoryBack.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )
        binding.categoryEmptyState.findViewById<TextView>(com.coui.appcompat.R.id.empty_view_title)
            ?.setTextColor(ContextCompat.getColor(this, R.color.search_bar_text))
        insetContent()
        val search = bindAdapter(category, query)
        worker = newWorker().also { it.execute(search) }
    }

    override fun finish() {
        super.finish()
        // Translucent wallpaper activities flash with the default close animation.
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        worker?.shutdownNow()
        worker = null
        main.removeCallbacksAndMessages(null)
        backdrop?.release()
        backdrop = null
        super.onDestroy()
    }

    /**
     * Installs the adapter for [category] and returns the off-main-thread task that
     * fills it with every hit the index has for [query].
     */
    private fun bindAdapter(category: SearchCategory, query: String): Runnable {
        val app = QsbApplicationWrapper.app()
        val list: RecyclerView = binding.categoryResults
        list.layoutManager = LinearLayoutManager(this)
        list.itemAnimator = null
        return when (category) {
            SearchCategory.APPS -> {
                val adapter = SearchAppAdapter(launcher::openApp)
                list.adapter = adapter
                Runnable {
                    val result = app.installedApps.search(query)
                    publish(result.size) { adapter.submit(result, query) }
                }
            }

            SearchCategory.CONTACTS -> {
                val adapter = SearchContactsAdapter(
                    launcher::openContact,
                    launcher::callContact,
                    launcher::messageContact,
                )
                list.adapter = adapter
                Runnable {
                    val result = app.contacts.search(query)
                    publish(result.size) { adapter.submit(result, query) }
                }
            }

            SearchCategory.MESSAGES -> {
                val adapter = SearchMessagesAdapter(launcher::openMessage)
                list.adapter = adapter
                Runnable {
                    val result = app.messages.search(query)
                    publish(result.size) { adapter.submit(result, query) }
                }
            }

            SearchCategory.NOTES -> {
                val adapter = SearchNotesAdapter(launcher::openNote)
                list.adapter = adapter
                Runnable {
                    val result = app.notes.search(query)
                    publish(result.size) { adapter.submit(result, query) }
                }
            }

            SearchCategory.CALENDAR -> {
                val adapter = SearchCalendarAdapter(launcher::openCalendar)
                list.adapter = adapter
                Runnable {
                    val result = app.calendar.search(query)
                    publish(result.size) { adapter.submit(result, query) }
                }
            }

            SearchCategory.FILES -> {
                val adapter = SearchFilesAdapter(launcher::openFile)
                list.adapter = adapter
                Runnable {
                    val result = app.files.search(query)
                    publish(result.size) { adapter.submit(result, query) }
                }
            }

            SearchCategory.SETTINGS -> {
                val adapter = SearchSettingsAdapter(launcher::openSetting)
                list.adapter = adapter
                Runnable {
                    val result = app.settings.search(query)
                    publish(result.size) { adapter.submit(result, query) }
                }
            }
        }
    }

    private fun publish(count: Int, submit: () -> Unit) {
        main.post {
            if (isFinishing || isDestroyed) {
                return@post
            }
            submit()
            binding.categoryEmptyState.visibility = if (count == 0) View.VISIBLE else View.GONE
        }
    }

    private fun insetContent() {
        val extraTop = resources.getDimensionPixelSize(R.dimen.search_bar_margin_top)
        ViewCompat.setOnApplyWindowInsetsListener(binding.categoryContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top + extraTop, bottom = bars.bottom)
            insets
        }
    }

    companion object {
        private const val EXTRA_CATEGORY = "gd.app.quicksearch.extra.CATEGORY"
        private const val EXTRA_QUERY = "gd.app.quicksearch.extra.QUERY"

        fun intent(context: Context, category: SearchCategory, query: String): Intent =
            Intent(context, SearchCategoryActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.key)
                .putExtra(EXTRA_QUERY, query)

        private fun newWorker(): ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "category-search").apply { isDaemon = true }
            }
    }
}
