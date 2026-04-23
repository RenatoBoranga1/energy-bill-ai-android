package br.com.energybillai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.energybillai.domain.usecase.BootstrapSessionUseCase
import br.com.energybillai.domain.usecase.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AppEntryState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
)

@HiltViewModel
class AppEntryViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
    private val bootstrapSessionUseCase: BootstrapSessionUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppEntryState())
    val state = mutableState.asStateFlow()

    init {
        if (BuildConfig.SKIP_LOGIN_FOR_DEV) {
            mutableState.value = AppEntryState(isLoading = false, isAuthenticated = true)
        } else {
            viewModelScope.launch {
                bootstrapSessionUseCase()
                observeSessionUseCase().collectLatest { session ->
                    mutableState.value = AppEntryState(
                        isLoading = false,
                        isAuthenticated = session != null,
                    )
                }
            }
        }
    }
}
