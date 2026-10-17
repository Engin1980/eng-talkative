package cz.talkative.app.domain.model

/** Profil slovníku. Zatím existuje jen [DEFAULT_ID], model je ale připravený na více profilů. */
data class Profile(val id: String) {
    companion object {
        const val DEFAULT_ID = "default"
    }
}
