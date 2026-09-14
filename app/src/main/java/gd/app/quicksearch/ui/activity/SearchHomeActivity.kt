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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import gd.app.quicksearch.QsbApplicationWrapper
import gd.app.quicksearch.R
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding
import gd.app.quicksearch.search.LocalSearch
import gd.app.quicksearch.search.apps.InstalledApp
import gd.app.quicksearch.search.settings.SettingItem
import gd.app.quicksearch.ui.home.SearchAppAdapter
import gd.app.quicksearch.ui.home.SearchHomeBackdrop
import gd.app.quicksearch.ui.home.SearchSettingsAdapter
import gd.app.quicksearch.ui.home.SectionHeaderAdapter

class SearchHomeActivity : AppCompatActivity(), LocalSearch.Listener {

    private lateinit var binding: ActivitySearchHomeBinding
    private lateinit var localSearch: LocalSearch
    private lateinit var appAdapter: SearchAppAdapter
    private lateinit var settingsAdapter: SearchSettingsAdapter
    private lateinit var appsHeader: SectionHeaderAdapter
    private lateinit var settingsHeader: SectionHeaderAdapter
    private var backdrop: SearchHomeBackdrop? = null
    private var appsReady = true
    private var settingsReady = true

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            localSearch.refresh()
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
        val app = QsbApplicationWrapper.app()
        localSearch = LocalSearch(app.installedApps, app.settings, this).also { it.warm() }
        registerPackageChanges()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        localSearch.release()
        backdrop?.release()
        backdrop = null
        super.onDestroy()
    }

    override fun onQueryStarted(query: String) {
        if (isDestroyed || isFinishing) {
            return
        }
        appsReady = false
        settingsReady = false
        appAdapter.submit(emptyList())
        settingsAdapter.submit(emptyList())
        appsHeader.hide()
        settingsHeader.hide()
        binding.emptyState.visibility = View.GONE
    }

    override fun onApps(query: String, apps: List<InstalledApp>) {
        if (isDestroyed || isFinishing) {
            return
        }
        appsReady = true
        appAdapter.submit(apps)
        if (apps.isEmpty()) {
            appsHeader.hide()
        } else {
            appsHeader.show(getString(R.string.search_section_apps))
        }
        updateEmptyState(query)
    }

    override fun onSettings(query: String, settings: List<SettingItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        settingsReady = true
        settingsAdapter.submit(settings)
        if (settings.isEmpty()) {
            settingsHeader.hide()
        } else {
            settingsHeader.show(getString(R.string.search_section_settings))
        }
        updateEmptyState(query)
    }

    override fun onCleared() {
        if (isDestroyed || isFinishing) {
            return
        }
        appsReady = true
        settingsReady = true
        appAdapter.submit(emptyList())
        settingsAdapter.submit(emptyList())
        appsHeader.hide()
        settingsHeader.hide()
        binding.emptyState.visibility = View.GONE
    }

    private fun setupResults() {
        appsHeader = SectionHeaderAdapter()
        settingsHeader = SectionHeaderAdapter()
        appAdapter = SearchAppAdapter(::openApp)
        settingsAdapter = SearchSettingsAdapter(::openSetting)
        binding.searchResults.apply {
            layoutManager = LinearLayoutManager(this@SearchHomeActivity)
            adapter = ConcatAdapter(appsHeader, appAdapter, settingsHeader, settingsAdapter)
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
            localSearch.onQueryChanged(text?.toString().orEmpty())
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                localSearch.submitNow(input.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        binding.searchBar.searchAction.setOnClickListener {
            localSearch.submitNow(input.text?.toString().orEmpty())
        }
    }

    private fun updateEmptyState(query: String) {
        val empty = query.isNotEmpty() &&
            appsReady &&
            settingsReady &&
            appAdapter.itemCount == 0 &&
            settingsAdapter.itemCount == 0
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun openApp(app: InstalledApp) {
        val launch = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        launchAndFinish(launch)
    }

    private fun openSetting(item: SettingItem) {
        val launch = item.launchIntent() ?: return
        launchAndFinish(launch)
    }

    private fun launchAndFinish(intent: Intent) {
        try {
            startActivity(intent)
            hideIme()
            finish()
        } catch (_: ActivityNotFoundException) {
            localSearch.refresh()
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
