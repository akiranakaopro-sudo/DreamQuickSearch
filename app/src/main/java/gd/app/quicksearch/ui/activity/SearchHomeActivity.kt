package gd.app.quicksearch.ui.activity

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import gd.app.quicksearch.R
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding
import gd.app.quicksearch.ui.home.SearchHomeBackdrop

class SearchHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchHomeBinding
    private var backdrop: SearchHomeBackdrop? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        binding = ActivitySearchHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        backdrop = SearchHomeBackdrop(this, binding).also { it.apply() }
        insetSearchBar()
        binding.searchBar.searchInput.imeOptions = EditorInfo.IME_ACTION_SEARCH
    }

    override fun onDestroy() {
        backdrop?.release()
        backdrop = null
        super.onDestroy()
    }

    private fun insetSearchBar() {
        val extraTop = resources.getDimensionPixelSize(R.dimen.search_bar_margin_top)
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchBar.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                topMargin = bars.top + extraTop
            }
            insets
        }
    }
}
