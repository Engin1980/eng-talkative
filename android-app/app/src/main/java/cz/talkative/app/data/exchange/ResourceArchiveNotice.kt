package cz.talkative.app.data.exchange

import android.content.Context
import cz.talkative.app.R
import cz.talkative.app.domain.exchange.ArchiveNotice
import cz.talkative.app.domain.exchange.buildArasaacLicenseText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Text uvedení ARASAAC z řetězců aplikace, tedy stejný, jaký uživatel vidí v „O aplikaci“. */
class ResourceArchiveNotice @Inject constructor(
    @ApplicationContext private val context: Context,
) : ArchiveNotice {
    override fun arasaacLicenseText(): String = buildArasaacLicenseText(
        credit = context.getString(R.string.arasaac_credit),
        creditLong = context.getString(R.string.arasaac_credit_long),
        note = context.getString(R.string.arasaac_license_file_note),
    )
}
