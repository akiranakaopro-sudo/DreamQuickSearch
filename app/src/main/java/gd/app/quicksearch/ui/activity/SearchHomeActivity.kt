package gd.app.quicksearch.ui.activity

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.coui.appcompat.darkmode.COUIDarkModeUtil
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding

class SearchHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (COUIDarkModeUtil.isNightMode(this)) {
            binding.emptyState.setAnimFileName("no_search_results_dark.json")
        }
        binding.searchBar.searchEditText?.apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            requestFocus()
        }
    }
}
