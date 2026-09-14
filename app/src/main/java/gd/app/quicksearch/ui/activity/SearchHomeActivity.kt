package gd.app.quicksearch.ui.activity

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.coui.appcompat.R as CouiR
import com.coui.appcompat.searchview.COUISearchBar
import gd.app.quicksearch.R
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding
import gd.app.quicksearch.ui.home.SearchHomeBackdrop

class SearchHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchHomeBinding
    private var backdrop: SearchHomeBackdrop? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        binding = ActivitySearchHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        backdrop = SearchHomeBackdrop(this, binding).also { it.apply() }
        insetSearchBar()
        setupSearchBar()
    }

    override fun onDestroy() {
        backdrop?.release()
        backdrop = null
        super.onDestroy()
    }

    private fun insetSearchBar() {
        val extraTop = resources.getDimensionPixelSize(CouiR.dimen.coui_search_view_anim_margin_normal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
                topMargin = bars.top + extraTop
            }
            insets
        }
    }

    private fun setupSearchBar() {
        val searchBar = binding.searchBar
        searchBar.setInputMethodAnimationEnabled(false)
        searchBar.setSearchAnimateType(COUISearchBar.TYPE_NON_INSTANT_SEARCH)
        searchBar.setSearchBackgroundColor(ColorStateList.valueOf(SEARCH_PILL_COLOR))
        setupVoiceButton(searchBar)
        searchBar.changeStateImmediately(COUISearchBar.STATE_EDIT)
        setupSearchActionButton(searchBar)
        searchBar.searchEditText?.imeOptions = EditorInfo.IME_ACTION_SEARCH
    }

    private fun setupVoiceButton(searchBar: COUISearchBar) {
        val voice = ContextCompat.getDrawable(this, CouiR.drawable.coui_search_view_voice_icon)?.mutate() ?: return
        searchBar.setInnerPrimaryButton(voice)
        (searchBar.innerPrimaryButton as? ImageView)?.apply {
            contentDescription = getString(CouiR.string.support_abc_searchview_description_voice)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(VOICE_ICON_COLOR)
            val inset = resources.getDimensionPixelSize(CouiR.dimen.coui_search_view_cancel_margin_small)
            setPadding(inset, inset, inset, inset)
        }
    }

    private fun setupSearchActionButton(searchBar: COUISearchBar) {
        searchBar.functionalButton?.apply {
            setBackgroundResource(R.drawable.bg_search_action)
            setTextAppearance(CouiR.style.couiTextAppearanceButton)
            setTextColor(
                ContextCompat.getColor(this@SearchHomeActivity, CouiR.color.coui_searchview_cancel_text_color_light),
            )
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = resources.getDimensionPixelSize(CouiR.dimen.coui_search_view_wrapper_height) -
                resources.getDimensionPixelSize(CouiR.dimen.coui_search_view_cancel_margin_small) * 2
            val horizontal = resources.getDimensionPixelSize(CouiR.dimen.coui_search_view_cancel_btn_margin)
            val vertical = resources.getDimensionPixelSize(CouiR.dimen.coui_search_view_cancel_margin_small)
            setPadding(horizontal, vertical, horizontal, vertical)
        }
    }

    companion object {
        private const val SEARCH_PILL_COLOR = 0x33FFFFFF
        private const val VOICE_ICON_COLOR = 0xD9FFFFFF.toInt()
    }
}
