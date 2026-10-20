package cz.talkative.app.testing

import cz.talkative.app.domain.exchange.ArchiveNotice

/** Text uvedení pro testy; v testech exportu se hledá doslova. */
class FakeArchiveNotice(private val text: String = LICENSE_TEXT) : ArchiveNotice {
    override fun arasaacLicenseText(): String = text

    companion object {
        const val LICENSE_TEXT = "Autor piktogramů: Sergio Palao.\nČeština: ěščřžýáíé"
    }
}
