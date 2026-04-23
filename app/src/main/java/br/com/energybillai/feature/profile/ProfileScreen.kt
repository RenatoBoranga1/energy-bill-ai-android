package br.com.energybillai.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.common.UiState
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.EmptyStatePane
import br.com.energybillai.core.designsystem.LoadingPane
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.core.network.ApiConfig
import br.com.energybillai.domain.model.AuthSession
import br.com.energybillai.domain.usecase.GetCurrentSessionUseCase
import br.com.energybillai.domain.usecase.LogoutUseCase
import br.com.energybillai.domain.usecase.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
    private val getCurrentSessionUseCase: GetCurrentSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
    val apiConfig: ApiConfig,
) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<AuthSession>>(UiState.Loading)
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSessionUseCase().collect { session ->
                mutableState.value = if (session == null) UiState.Empty else UiState.Success(session)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            when (logoutUseCase(getCurrentSessionUseCase()?.tokens?.refreshToken)) {
                is AppResult.Success -> Unit
                is AppResult.Error -> Unit
            }
        }
    }
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Perfil") {
        when (val content = state) {
            UiState.Loading -> LoadingPane("Carregando sessao", "Lendo dados locais do usuario autenticado.")
            UiState.Empty -> EmptyStatePane("Sem sessao", "Nenhum usuario autenticado foi encontrado.")
            is UiState.Error -> EmptyStatePane("Falha", content.error.message)
            is UiState.Success -> {
                AppCard(title = "Conta") {
                    androidx.compose.material3.Text(text = content.data.user.name)
                    androidx.compose.material3.Text(text = content.data.user.email)
                    androidx.compose.material3.Text(text = "Criado em ${content.data.user.createdAt}")
                }
                AppCard(title = "Ambiente") {
                    androidx.compose.material3.Text(text = "API: ${viewModel.apiConfig.environment.displayName}")
                    androidx.compose.material3.Text(text = viewModel.apiConfig.baseUrl)
                }
                AppCard(title = "Sessao") {
                    androidx.compose.material3.Text(text = "O app renova o access token automaticamente quando o refresh token ainda esta valido.")
                    PrimaryActionButton(text = "Sair da conta", onClick = viewModel::logout)
                }
            }
            UiState.Idle -> Unit
        }
    }
}
