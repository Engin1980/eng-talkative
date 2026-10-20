package cz.talkative.app.ui.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.talkative.app.domain.notice.NOTICE_VERSION
import cz.talkative.app.domain.notice.NoticeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Jestli uživatel už vzal na vědomí aktuální verzi úvodního oznámení. */
enum class NoticeState {
    /** Ještě se čte uložená hodnota; nic se nezobrazuje, aby oznámení nebliklo. */
    LOADING,

    /** Oznámení je třeba ukázat (první spuštění nebo nová verze textu). */
    REQUIRED,

    ACKNOWLEDGED,
}

@HiltViewModel
class NoticeViewModel @Inject constructor(
    private val repository: NoticeRepository,
) : ViewModel() {

    val state: StateFlow<NoticeState> = repository.acknowledgedVersion
        .map { if (it != null && it >= NOTICE_VERSION) NoticeState.ACKNOWLEDGED else NoticeState.REQUIRED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoticeState.LOADING)

    /** „Beru na vědomí“. */
    fun acknowledge() {
        viewModelScope.launch { repository.acknowledge(NOTICE_VERSION) }
    }
}
