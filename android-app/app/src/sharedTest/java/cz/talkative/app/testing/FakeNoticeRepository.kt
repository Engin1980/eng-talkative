package cz.talkative.app.testing

import cz.talkative.app.domain.notice.NoticeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Odsouhlasená verze oznámení v paměti. */
class FakeNoticeRepository(initial: Int? = null) : NoticeRepository {
    private val state = MutableStateFlow(initial)

    val current: Int? get() = state.value

    override val acknowledgedVersion: Flow<Int?> = state

    override suspend fun acknowledge(version: Int) {
        state.value = version
    }
}
