package gd.app.quicksearch.search.files

import android.content.Intent
import android.net.Uri
import gd.app.quicksearch.search.MatchKeys

data class FileItem(
    val id: String,
    val name: String,
    val mime: String,
    val uri: Uri,
    val path: String,
    val date: Long,
    val keys: MatchKeys,
) {
    fun viewIntent(): Intent {
        val type = mime.ifEmpty { "*/*" }
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
    }
}
