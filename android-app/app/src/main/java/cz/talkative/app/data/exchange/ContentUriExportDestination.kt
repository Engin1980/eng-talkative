package cz.talkative.app.data.exchange

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import cz.talkative.app.domain.exchange.ExportDestination
import cz.talkative.app.domain.exchange.ExportDestinationFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/** Soubor vybraný uživatelem přes systémový výběr (Storage Access Framework). */
class ContentUriExportDestination(
    private val context: Context,
    private val uri: Uri,
) : ExportDestination {

    override fun open(): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Nelze otevřít $uri")

    /** Systém soubor při výběru už vytvořil (prázdný); po neúspěchu ho smažeme, aby nezůstal poškozený. */
    override suspend fun discard() {
        withContext(Dispatchers.IO) {
            val deleted = try {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } catch (e: Exception) {
                false // poskytovatel nepodporuje mazání dokumentů, zkusí se obyčejné smazání
            }
            if (!deleted) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    // Soubor už neexistuje nebo ho poskytovatel smazat nedovolí; víc se dělat nedá.
                }
            }
        }
    }
}

class ContentUriExportDestinationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExportDestinationFactory {
    override fun forUri(uri: String): ExportDestination = ContentUriExportDestination(context, Uri.parse(uri))
}
