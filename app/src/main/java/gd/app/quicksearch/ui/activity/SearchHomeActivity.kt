package gd.app.quicksearch.ui.activity

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import gd.app.quicksearch.QsbApplicationWrapper
import gd.app.quicksearch.R
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding
import gd.app.quicksearch.search.apps.AppSearch
import gd.app.quicksearch.search.apps.InstalledApp
import gd.app.quicksearch.ui.home.SearchAppAdapter
import gd.app.quicksearch.ui.home.SearchHomeBackdrop

class SearchHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchHomeBinding
    private lateinit var appSearch: AppSearch
    private lateinit var appAdapter: SearchAppAdapter
    private var backdrop: SearchHomeBackdrop? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appSearch.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        binding = ActivitySearchHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        backdrop = SearchHomeBackdrop(this, binding).also { it.apply() }
        setupResults()
        setupSearch()
        insetContent()
        appSearch = AppSearch(QsbApplicationWrapper.app().installedApps, ::bindApps).also { it.warm() }
        registerPackageChanges()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        appSearch.release()
        backdrop?.release()
        backdrop = null
        super.onDestroy()
    }

    private fun setupResults() {
        appAdapter = SearchAppAdapter(::openApp)
        binding.searchResults.apply {
            layoutManager = LinearLayoutManager(this@SearchHomeActivity)
            adapter = appAdapter
            itemAnimator = null
            setHasFixedSize(true)
        }
        binding.emptyState.setAnimFileName("no_search_results_dark.json")
        binding.emptyState.findViewById<TextView>(com.coui.appcompat.R.id.empty_view_title)
            ?.setTextColor(ContextCompat.getColor(this, R.color.search_bar_text))
    }

    private fun setupSearch() {
        val input = binding.searchBar.searchInput
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.doAfterTextChanged { text ->
            appSearch.onQueryChanged(text?.toString().orEmpty())
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                appSearch.submitNow(input.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        binding.searchBar.searchAction.setOnClickListener {
            appSearch.submitNow(input.text?.toString().orEmpty())
        }
    }

    private fun bindApps(query: String, apps: List<InstalledApp>) {
        if (isDestroyed || isFinishing) {
            return
        }
        appAdapter.submit(apps)
        val hasQuery = query.isNotEmpty()
        binding.appsSectionTitle.visibility = if (hasQuery && apps.isNotEmpty()) View.VISIBLE else View.GONE
        binding.searchResults.visibility = if (apps.isNotEmpty()) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasQuery && apps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openApp(app: InstalledApp) {
        val launch = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(launch)
            hideIme()
            finish()
        } catch (_: ActivityNotFoundException) {
            appSearch.refresh()
        }
    }

    private fun insetContent() {
        val extraTop = resources.getDimensionPixelSize(R.dimen.search_bar_margin_top)
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top + extraTop, bottom = bars.bottom)
            insets
        }
    }

    private fun hideIme() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(binding.searchBar.searchInput.windowToken, 0)
    }

    private fun registerPackageChanges() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
