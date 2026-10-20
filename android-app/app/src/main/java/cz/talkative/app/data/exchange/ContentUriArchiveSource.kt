package cz.talkative.app.data.exchange

import android.content.Context
import android.net.Uri
import cz.talkative.app.domain.exchange.ArchiveSource
import cz.talkative.app.domain.exchange.ArchiveSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** Archiv ze souboru vybraného uživatelem přes systémový výběr (Storage Access Framework). */
class ContentUriArchiveSource(
    private val context: Context,
    private val uri: Uri,
) : ArchiveSource {
    override fun open(): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Nelze otevřít $uri")
}

class ContentUriArchiveSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : ArchiveSourceFactory {
    override fun forUri(uri: String): ArchiveSource = ContentUriArchiveSource(context, Uri.parse(uri))
}
