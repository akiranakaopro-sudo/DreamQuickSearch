package gd.app.quicksearch.ui.activity

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import gd.app.quicksearch.QsbApplicationWrapper
import gd.app.quicksearch.R
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding
import gd.app.quicksearch.search.LocalSearch
import gd.app.quicksearch.search.SearchCategory
import gd.app.quicksearch.search.apps.InstalledApp
import gd.app.quicksearch.search.calendar.CalendarItem
import gd.app.quicksearch.search.contacts.ContactItem
import gd.app.quicksearch.search.files.FileItem
import gd.app.quicksearch.search.files.FilesIndex
import gd.app.quicksearch.search.messages.MessageItem
import gd.app.quicksearch.search.notes.NoteItem
import gd.app.quicksearch.search.settings.SettingItem
import gd.app.quicksearch.ui.home.SearchAppAdapter
import gd.app.quicksearch.ui.home.SearchCalendarAdapter
import gd.app.quicksearch.ui.home.SearchContactsAdapter
import gd.app.quicksearch.ui.home.SearchFilesAdapter
import gd.app.quicksearch.ui.home.SearchHomeBackdrop
import gd.app.quicksearch.ui.home.SearchMessagesAdapter
import gd.app.quicksearch.ui.home.SearchNotesAdapter
import gd.app.quicksearch.ui.home.SearchSettingsAdapter
import gd.app.quicksearch.ui.home.SectionHeaderAdapter
import kotlin.math.abs

class SearchHomeActivity : AppCompatActivity(), LocalSearch.Listener {

    private lateinit var binding: ActivitySearchHomeBinding
    private lateinit var localSearch: LocalSearch
    private lateinit var appAdapter: SearchAppAdapter
    private lateinit var settingsAdapter: SearchSettingsAdapter
    private lateinit var contactsAdapter: SearchContactsAdapter
    private lateinit var messagesAdapter: SearchMessagesAdapter
    private lateinit var notesAdapter: SearchNotesAdapter
    private lateinit var calendarAdapter: SearchCalendarAdapter
    private lateinit var filesAdapter: SearchFilesAdapter
    private lateinit var appsHeader: SectionHeaderAdapter
    private lateinit var settingsHeader: SectionHeaderAdapter
    private lateinit var contactsHeader: SectionHeaderAdapter
    private lateinit var messagesHeader: SectionHeaderAdapter
    private lateinit var notesHeader: SectionHeaderAdapter
    private lateinit var calendarHeader: SectionHeaderAdapter
    private lateinit var filesHeader: SectionHeaderAdapter
    private var backdrop: SearchHomeBackdrop? = null
    private var appsReady = true
    private var settingsReady = true
    private var contactsReady = true
    private var messagesReady = true
    private var notesReady = true
    private var calendarReady = true
    private var filesReady = true
    private var askedSensitivePermissions = false
    private var suppressQueryDispatch = false
    private var resultsAtTop = true
    private var restoreImeAfterResultsScroll = false
    private var leftTopDuringResultsScroll = false
    private var currentQuery = ""
    private val launcher = SearchLauncher(this) { localSearch.refreshApps() }

    private val requestSensitivePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.READ_CONTACTS] == true) {
            localSearch.onContactsPermissionChanged()
        }
        if (result[Manifest.permission.READ_SMS] == true) {
            localSearch.onSmsPermissionChanged()
        }
        if (result[Manifest.permission.READ_CALENDAR] == true) {
            localSearch.onCalendarPermissionChanged()
        }
        if (FilesIndex.neededPermissions().any { result[it] == true }) {
            localSearch.onStoragePermissionChanged()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            localSearch.refreshApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        binding = ActivitySearchHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        backdrop = SearchHomeBackdrop(this, binding.blurBackdrop, binding.blurLayer).also { it.apply() }
        setupResults()
        setupSearch()
        insetContent()
        val app = QsbApplicationWrapper.app()
        localSearch = LocalSearch(
            app.installedApps,
            app.settings,
            app.contacts,
            app.messages,
            app.notes,
            app.calendar,
            app.files,
            this,
        ).also { it.warm() }
        registerPackageChanges()
    }

    override fun onStart() {
        super.onStart()
        localSearch.onContactsPermissionChanged()
        localSearch.onSmsPermissionChanged()
        localSearch.onCalendarPermissionChanged()
        localSearch.onStoragePermissionChanged()
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
        currentQuery = query
        appsReady = false
        settingsReady = false
        contactsReady = false
        messagesReady = false
        notesReady = false
        calendarReady = false
        filesReady = false
        appAdapter.submit(emptyList(), query)
        settingsAdapter.submit(emptyList(), query)
        contactsAdapter.submit(emptyList(), query)
        messagesAdapter.submit(emptyList(), query)
        notesAdapter.submit(emptyList(), query)
        calendarAdapter.submit(emptyList(), query)
        filesAdapter.submit(emptyList(), query)
        appsHeader.hide()
        settingsHeader.hide()
        contactsHeader.hide()
        messagesHeader.hide()
        notesHeader.hide()
        calendarHeader.hide()
        filesHeader.hide()
        binding.emptyState.visibility = View.GONE
        captureResultsScroll()
    }

    override fun onApps(query: String, apps: List<InstalledApp>) {
        if (isDestroyed || isFinishing) {
            return
        }
        val started = System.nanoTime()
        appsReady = true
        appAdapter.submit(collapse(apps), query)
        bindSection(appsHeader, R.string.search_section_apps, apps.size)
        logBind("apps", apps.size, started)
        updateEmptyState(query)
    }

    override fun onSettings(query: String, settings: List<SettingItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        val started = System.nanoTime()
        settingsReady = true
        settingsAdapter.submit(collapse(settings), query)
        bindSection(settingsHeader, R.string.search_section_settings, settings.size)
        logBind("settings", settings.size, started)
        updateEmptyState(query)
    }

    override fun onContacts(query: String, contacts: List<ContactItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        val started = System.nanoTime()
        contactsReady = true
        contactsAdapter.submit(collapse(contacts), query)
        bindSection(contactsHeader, R.string.search_section_contacts, contacts.size)
        logBind("contacts", contacts.size, started)
        updateEmptyState(query)
    }

    override fun onMessages(query: String, messages: List<MessageItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        val started = System.nanoTime()
        messagesReady = true
        messagesAdapter.submit(collapse(messages), query)
        bindSection(messagesHeader, R.string.search_section_messages, messages.size)
        logBind("messages", messages.size, started)
        updateEmptyState(query)
    }

    override fun onNotes(query: String, notes: List<NoteItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        val started = System.nanoTime()
        notesReady = true
        notesAdapter.submit(collapse(notes), query)
        bindSection(notesHeader, R.string.search_section_notes, notes.size)
        logBind("notes", notes.size, started)
        updateEmptyState(query)
    }

    override fun onCalendar(query: String, events: List<CalendarItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        val started = System.nanoTime()
        calendarReady = true
        calendarAdapter.submit(collapse(events), query)
        bindSection(calendarHeader, R.string.search_section_calendar, events.size)
        logBind("calendar", events.size, started)
        updateEmptyState(query)
    }

    override fun onFiles(query: String, files: List<FileItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        val started = System.nanoTime()
        filesReady = true
        filesAdapter.submit(collapse(files), query)
        bindSection(filesHeader, R.string.search_section_files, files.size)
        logBind("files", files.size, started)
        updateEmptyState(query)
    }

    override fun onCleared() {
        if (isDestroyed || isFinishing) {
            return
        }
        currentQuery = ""
        appsReady = true
        settingsReady = true
        contactsReady = true
        messagesReady = true
        notesReady = true
        calendarReady = true
        filesReady = true
        appAdapter.submit(emptyList(), "")
        settingsAdapter.submit(emptyList(), "")
        contactsAdapter.submit(emptyList(), "")
        messagesAdapter.submit(emptyList(), "")
        notesAdapter.submit(emptyList(), "")
        calendarAdapter.submit(emptyList(), "")
        filesAdapter.submit(emptyList(), "")
        appsHeader.hide()
        settingsHeader.hide()
        contactsHeader.hide()
        messagesHeader.hide()
        notesHeader.hide()
        calendarHeader.hide()
        filesHeader.hide()
        binding.emptyState.visibility = View.GONE
        captureResultsScroll()
    }

    private fun setupResults() {
        appsHeader = SectionHeaderAdapter { openCategory(SearchCategory.APPS) }
        contactsHeader = SectionHeaderAdapter { openCategory(SearchCategory.CONTACTS) }
        messagesHeader = SectionHeaderAdapter { openCategory(SearchCategory.MESSAGES) }
        notesHeader = SectionHeaderAdapter { openCategory(SearchCategory.NOTES) }
        calendarHeader = SectionHeaderAdapter { openCategory(SearchCategory.CALENDAR) }
        filesHeader = SectionHeaderAdapter { openCategory(SearchCategory.FILES) }
        settingsHeader = SectionHeaderAdapter { openCategory(SearchCategory.SETTINGS) }
        appAdapter = SearchAppAdapter(launcher::openApp)
        contactsAdapter = SearchContactsAdapter(
            launcher::openContact,
            launcher::callContact,
            launcher::messageContact,
        )
        messagesAdapter = SearchMessagesAdapter(launcher::openMessage)
        notesAdapter = SearchNotesAdapter(launcher::openNote)
        calendarAdapter = SearchCalendarAdapter(launcher::openCalendar)
        filesAdapter = SearchFilesAdapter(launcher::openFile)
        settingsAdapter = SearchSettingsAdapter(launcher::openSetting)
        binding.searchResults.apply {
            layoutManager = LinearLayoutManager(this@SearchHomeActivity)
            adapter = ConcatAdapter(
                appsHeader,
                appAdapter,
                contactsHeader,
                contactsAdapter,
                messagesHeader,
                messagesAdapter,
                notesHeader,
                notesAdapter,
                calendarHeader,
                calendarAdapter,
                filesHeader,
                filesAdapter,
                settingsHeader,
                settingsAdapter,
            )
            itemAnimator = null
            setHasFixedSize(true)
            var dragStartY = 0f
            var dragHidIme = false
            val touchSlop = ViewConfiguration.get(this@SearchHomeActivity).scaledTouchSlop
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            dragStartY = e.y
                            dragHidIme = false
                        }

                        MotionEvent.ACTION_MOVE -> {
                            // Short result lists never leave the top, so scroll
                            // callbacks stay silent — still dismiss on a drag.
                            if (!dragHidIme && abs(e.y - dragStartY) > touchSlop) {
                                dragHidIme = true
                                restoreImeAfterResultsScroll = true
                                hideIme()
                            }
                        }
                    }
                    return false
                }
            })
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val atTop = !recyclerView.canScrollVertically(-1)
                    if (!atTop) {
                        leftTopDuringResultsScroll = true
                    }
                    resultsAtTop = atTop
                    if (dy > 0) {
                        restoreImeAfterResultsScroll = true
                        hideIme()
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            restoreImeAfterResultsScroll = true
                            hideIme()
                        }

                        RecyclerView.SCROLL_STATE_IDLE -> {
                            resultsAtTop = !recyclerView.canScrollVertically(-1)
                            // Only reopen after the list had actually left the top;
                            // short lists stay at the top and should keep the IME away.
                            if (restoreImeAfterResultsScroll &&
                                resultsAtTop &&
                                leftTopDuringResultsScroll
                            ) {
                                showIme()
                            }
                            restoreImeAfterResultsScroll = false
                            leftTopDuringResultsScroll = false
                        }
                    }
                }
            })
        }
        binding.emptyState.setAnimFileName("no_search_results_dark.json")
        binding.emptyState.findViewById<TextView>(com.coui.appcompat.R.id.empty_view_title)
            ?.setTextColor(ContextCompat.getColor(this, R.color.search_bar_text))
    }

    private fun setupSearch() {
        val input = binding.searchBar.searchInput
        val voiceOrClear = binding.searchBar.searchVoice
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            updateVoiceOrClear(query)
            if (suppressQueryDispatch) {
                suppressQueryDispatch = false
                return@doAfterTextChanged
            }
            maybeAskSensitivePermissions()
            localSearch.onQueryChanged(query)
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                localSearch.submitNow(input.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        voiceOrClear.setOnClickListener {
            if (input.text.isNullOrEmpty()) {
                return@setOnClickListener
            }
            suppressQueryDispatch = true
            input.text?.clear()
        }
        binding.searchBar.searchAction.setOnClickListener {
            localSearch.submitNow(input.text?.toString().orEmpty())
        }
        updateVoiceOrClear(input.text?.toString().orEmpty())
        input.requestFocus()
        input.post { showIme() }
    }

    private fun updateVoiceOrClear(query: String) {
        val voiceOrClear = binding.searchBar.searchVoice
        if (query.isEmpty()) {
            voiceOrClear.setImageResource(R.drawable.ic_search_voice)
            voiceOrClear.contentDescription = getString(R.string.search_voice)
        } else {
            voiceOrClear.setImageResource(R.drawable.ic_search_clear)
            voiceOrClear.contentDescription = getString(R.string.search_clear)
        }
    }

    private fun bindSection(header: SectionHeaderAdapter, titleRes: Int, total: Int) {
        if (total > 0) {
            header.show(getString(titleRes), total > SearchCategory.COLLAPSED_COUNT)
        } else {
            header.hide()
        }
    }

    private fun logBind(source: String, count: Int, started: Long) {
        Log.d(TAG, "$source bind=$count ${(System.nanoTime() - started) / 1_000_000}ms")
    }

    private fun openCategory(category: SearchCategory) {
        if (currentQuery.isEmpty()) {
            return
        }
        hideIme()
        startActivity(SearchCategoryActivity.intent(this, category, currentQuery))
    }

    private fun updateEmptyState(query: String) {
        val empty = query.isNotEmpty() &&
            appsReady &&
            settingsReady &&
            contactsReady &&
            messagesReady &&
            notesReady &&
            calendarReady &&
            filesReady &&
            appAdapter.itemCount == 0 &&
            settingsAdapter.itemCount == 0 &&
            contactsAdapter.itemCount == 0 &&
            messagesAdapter.itemCount == 0 &&
            notesAdapter.itemCount == 0 &&
            calendarAdapter.itemCount == 0 &&
            filesAdapter.itemCount == 0
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
    }

    /** Inline sections stay short; the rest is one tap away behind "More". */
    private fun <T> collapse(items: List<T>): List<T> =
        if (items.size <= SearchCategory.COLLAPSED_COUNT) {
            items
        } else {
            items.subList(0, SearchCategory.COLLAPSED_COUNT)
        }

    private fun maybeAskSensitivePermissions() {
        if (askedSensitivePermissions) {
            return
        }
        val missing = ArrayList<String>(2)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.READ_CONTACTS
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.READ_SMS
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.READ_CALENDAR
        }
        for (permission in FilesIndex.neededPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                missing += permission
            }
        }
        if (missing.isEmpty()) {
            return
        }
        askedSensitivePermissions = true
        requestSensitivePermissions.launch(missing.toTypedArray())
    }

    private fun insetContent() {
        val extraTop = resources.getDimensionPixelSize(R.dimen.search_bar_margin_top)
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top + extraTop, bottom = bars.bottom)
            insets
        }
    }

    private fun captureResultsScroll() {
        binding.searchResults.post {
            resultsAtTop = !binding.searchResults.canScrollVertically(-1)
            leftTopDuringResultsScroll = false
        }
    }

    private fun showIme() {
        val input = binding.searchBar.searchInput
        input.requestFocus()
        WindowCompat.getInsetsController(window, input).show(WindowInsetsCompat.Type.ime())
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideIme() {
        val input = binding.searchBar.searchInput
        // Drop focus so windowSoftInputMode=stateVisible cannot reopen the IME.
        if (input.hasFocus()) {
            input.clearFocus()
        }
        WindowCompat.getInsetsController(window, input).hide(WindowInsetsCompat.Type.ime())
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(input.windowToken, 0)
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

    companion object {
        private const val TAG = "SearchHome"
    }
}
