package gd.app.quicksearch.search

import android.icu.text.Transliterator
import android.text.TextUtils
import java.text.Normalizer
import java.util.Locale

data class MatchKeys(
    val labelLower: String,
    val labelCompact: String,
    val pinyin: String,
    val pinyinCompact: String,
    val initials: String,
    val keywords: List<String>,
)

object SearchText {

    private const val MAX_HIGHLIGHTS = 8

    fun needle(query: String): String = query.trim().lowercase(Locale.getDefault())

    fun compact(raw: String): String = raw.replace(" ", "").replace("-", "")

    fun keys(label: String, keywords: String = ""): MatchKeys {
        val labelLower = label.lowercase(Locale.getDefault())
        val pinyin = HanLatin.of(label)
        val parts = keywords.split(',', '，', ';', '、')
            .map { it.trim().lowercase(Locale.getDefault()) }
            .filter { it.isNotEmpty() }
        return MatchKeys(
            labelLower = labelLower,
            labelCompact = compact(labelLower),
            pinyin = pinyin,
            pinyinCompact = compact(pinyin),
            initials = initialsOf(pinyin),
            keywords = parts,
        )
    }

    fun rank(keys: MatchKeys, needle: String, compactNeedle: String): Int? {
        return when {
            keys.labelLower.startsWith(needle) -> 0
            keys.labelCompact.startsWith(compactNeedle) -> 1
            keys.pinyin.startsWith(needle) || keys.pinyinCompact.startsWith(compactNeedle) -> 2
            compactNeedle.length >= 2 && keys.initials.startsWith(compactNeedle) -> 3
            keys.keywords.any { it.startsWith(needle) || compact(it).startsWith(compactNeedle) } -> 4
            keys.labelLower.contains(needle) || keys.labelCompact.contains(compactNeedle) -> 5
            keys.pinyin.contains(needle) || keys.pinyinCompact.contains(compactNeedle) -> 6
            keys.keywords.any { it.contains(needle) || compact(it).contains(compactNeedle) } -> 7
            else -> null
        }
    }

    fun matchRanges(text: CharSequence, query: String): List<IntRange> {
        val needle = needle(query)
        if (needle.isEmpty() || text.isEmpty()) {
            return emptyList()
        }
        val lower = text.toString().lowercase(Locale.getDefault())
        val ranges = ArrayList<IntRange>(2)
        var from = 0
        while (ranges.size < MAX_HIGHLIGHTS) {
            val start = lower.indexOf(needle, from)
            if (start < 0) {
                break
            }
            ranges += start until (start + needle.length)
            from = start + needle.length
        }
        if (ranges.isNotEmpty()) {
            return ranges
        }
        val compactNeedle = compact(needle)
        if (compactNeedle.length < 2) {
            return emptyList()
        }
        return compactMatch(lower, compactNeedle)
    }

    private fun compactMatch(lower: String, compactNeedle: String): List<IntRange> {
        val compact = StringBuilder(lower.length)
        val origin = IntArray(lower.length)
        var n = 0
        for (i in lower.indices) {
            val ch = lower[i]
            if (ch == ' ' || ch == '-') {
                continue
            }
            origin[n] = i
            compact.append(ch)
            n++
        }
        val at = compact.toString().indexOf(compactNeedle)
        if (at < 0) {
            return emptyList()
        }
        val start = origin[at]
        val end = origin[at + compactNeedle.length - 1] + 1
        return listOf(start until end)
    }

    private object HanLatin {
        private val transliterator = runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
        private val marks = "\\p{M}+".toRegex()

        fun of(label: String): String {
            if (TextUtils.isEmpty(label)) {
                return ""
            }
            val latin = transliterator?.transliterate(label) ?: label
            return Normalizer.normalize(latin, Normalizer.Form.NFD)
                .replace(marks, "")
                .lowercase(Locale.getDefault())
                .trim()
        }
    }

    private fun initialsOf(pinyin: String): String {
        if (pinyin.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        for (part in pinyin.split(' ', '\t')) {
            val first = part.firstOrNull { it.isLetter() } ?: continue
            builder.append(first)
        }
        return builder.toString()
    }
}
