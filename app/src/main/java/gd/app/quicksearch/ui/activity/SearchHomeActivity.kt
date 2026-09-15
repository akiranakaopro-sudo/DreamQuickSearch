package gd.app.quicksearch.ui.activity

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
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
import gd.app.quicksearch.QsbApplicationWrapper
import gd.app.quicksearch.R
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding
import gd.app.quicksearch.search.LocalSearch
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
        backdrop = SearchHomeBackdrop(this, binding).also { it.apply() }
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
    }

    override fun onApps(query: String, apps: List<InstalledApp>) {
        if (isDestroyed || isFinishing) {
            return
        }
        appsReady = true
        appAdapter.submit(apps, query)
        bindSection(appsHeader, R.string.search_section_apps, apps.isNotEmpty())
        updateEmptyState(query)
    }

    override fun onSettings(query: String, settings: List<SettingItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        settingsReady = true
        settingsAdapter.submit(settings, query)
        bindSection(settingsHeader, R.string.search_section_settings, settings.isNotEmpty())
        updateEmptyState(query)
    }

    override fun onContacts(query: String, contacts: List<ContactItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        contactsReady = true
        contactsAdapter.submit(contacts, query)
        bindSection(contactsHeader, R.string.search_section_contacts, contacts.isNotEmpty())
        updateEmptyState(query)
    }

    override fun onMessages(query: String, messages: List<MessageItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        messagesReady = true
        messagesAdapter.submit(messages, query)
        bindSection(messagesHeader, R.string.search_section_messages, messages.isNotEmpty())
        updateEmptyState(query)
    }

    override fun onNotes(query: String, notes: List<NoteItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        notesReady = true
        notesAdapter.submit(notes, query)
        bindSection(notesHeader, R.string.search_section_notes, notes.isNotEmpty())
        updateEmptyState(query)
    }

    override fun onCalendar(query: String, events: List<CalendarItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        calendarReady = true
        calendarAdapter.submit(events, query)
        bindSection(calendarHeader, R.string.search_section_calendar, events.isNotEmpty())
        updateEmptyState(query)
    }

    override fun onFiles(query: String, files: List<FileItem>) {
        if (isDestroyed || isFinishing) {
            return
        }
        filesReady = true
        filesAdapter.submit(files, query)
        bindSection(filesHeader, R.string.search_section_files, files.isNotEmpty())
        updateEmptyState(query)
    }

    override fun onCleared() {
        if (isDestroyed || isFinishing) {
            return
        }
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
    }

    private fun setupResults() {
        appsHeader = SectionHeaderAdapter()
        contactsHeader = SectionHeaderAdapter()
        messagesHeader = SectionHeaderAdapter()
        notesHeader = SectionHeaderAdapter()
        calendarHeader = SectionHeaderAdapter()
        filesHeader = SectionHeaderAdapter()
        settingsHeader = SectionHeaderAdapter()
        appAdapter = SearchAppAdapter(::openApp)
        contactsAdapter = SearchContactsAdapter(::openContact)
        messagesAdapter = SearchMessagesAdapter(::openMessage)
        notesAdapter = SearchNotesAdapter(::openNote)
        calendarAdapter = SearchCalendarAdapter(::openCalendar)
        filesAdapter = SearchFilesAdapter(::openFile)
        settingsAdapter = SearchSettingsAdapter(::openSetting)
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
        }
        binding.emptyState.setAnimFileName("no_search_results_dark.json")
        binding.emptyState.findViewById<TextView>(com.coui.appcompat.R.id.empty_view_title)
            ?.setTextColor(ContextCompat.getColor(this, R.color.search_bar_text))
    }

    private fun setupSearch() {
        val input = binding.searchBar.searchInput
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.doAfterTextChanged { text ->
            maybeAskSensitivePermissions()
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

    private fun bindSection(header: SectionHeaderAdapter, titleRes: Int, visible: Boolean) {
        if (visible) {
            header.show(getString(titleRes))
        } else {
            header.hide()
        }
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

    private fun openContact(item: ContactItem) {
        launchAndFinish(item.viewIntent())
    }

    private fun openFile(item: FileItem) {
        launchAndFinish(item.viewIntent())
    }

    private fun openNote(item: NoteItem) {
        for (intent in item.viewIntents()) {
            try {
                startActivity(intent)
                hideIme()
                finish()
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    private fun openCalendar(item: CalendarItem) {
        for (intent in item.viewIntents()) {
            try {
                startActivity(intent)
                hideIme()
                finish()
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    private fun openMessage(item: MessageItem) {
        for (intent in item.conversationIntents(this)) {
            try {
                startActivity(intent)
                hideIme()
                finish()
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    private fun launchAndFinish(intent: Intent) {
        try {
            startActivity(intent)
            hideIme()
            finish()
        } catch (_: ActivityNotFoundException) {
            localSearch.refreshApps()
        }
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
