package cz.talkative.app.ui.client

import cz.talkative.app.domain.model.Profile
import cz.talkative.app.domain.repository.ProfileRepository
import cz.talkative.app.domain.usecase.ObserveCurrentProfileUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClientViewModelTest {

    private val profile = MutableStateFlow(Profile("default"))
    private val repository = object : ProfileRepository {
        override fun observeCurrentProfile(): Flow<Profile> = profile
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasNoProfile() {
        val viewModel = ClientViewModel(ObserveCurrentProfileUseCase(repository))

        assertNull(viewModel.uiState.value.profileId)
    }

    @Test
    fun uiState_reflectsCurrentProfileFromDomain() = runTest {
        val viewModel = ClientViewModel(ObserveCurrentProfileUseCase(repository))
        // stateIn s WhileSubscribed startuje až při odběru
        viewModel.uiState.launchIn(backgroundScope)
        runCurrent()

        assertEquals("default", viewModel.uiState.value.profileId)

        profile.value = Profile("other")
        runCurrent()
        assertEquals("other", viewModel.uiState.value.profileId)
    }
}
