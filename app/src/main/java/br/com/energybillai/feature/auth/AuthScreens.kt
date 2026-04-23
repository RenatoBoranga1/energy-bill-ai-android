package br.com.energybillai.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.core.designsystem.AppBrandLockup
import br.com.energybillai.core.designsystem.AppCard
import br.com.energybillai.core.designsystem.AppTextField
import br.com.energybillai.core.designsystem.EnergyScreen
import br.com.energybillai.core.designsystem.HeroCard
import br.com.energybillai.core.designsystem.InlineWarning
import br.com.energybillai.core.designsystem.PrimaryActionButton
import br.com.energybillai.domain.usecase.LoginUseCase
import br.com.energybillai.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
)

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state = mutableState.asStateFlow()

    fun updateEmail(value: String) {
        mutableState.value = mutableState.value.copy(email = value, error = null)
    }

    fun updatePassword(value: String) {
        mutableState.value = mutableState.value.copy(password = value, error = null)
    }

    fun submit() {
        val current = mutableState.value
        if (current.email.isBlank() || current.password.length < 8) {
            mutableState.value = current.copy(
                error = AppError(
                    code = "validation_error",
                    message = "Preencha um e-mail válido e uma senha com pelo menos 8 caracteres.",
                ),
            )
            return
        }

        viewModelScope.launch {
            mutableState.value = current.copy(isSubmitting = true, error = null)
            when (val result = loginUseCase(current.email.trim(), current.password)) {
                is AppResult.Success -> mutableState.value = mutableState.value.copy(isSubmitting = false, error = null)
                is AppResult.Error -> mutableState.value = mutableState.value.copy(isSubmitting = false, error = result.error)
            }
        }
    }
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RegisterUiState())
    val state = mutableState.asStateFlow()

    fun updateName(value: String) {
        mutableState.value = mutableState.value.copy(name = value, error = null)
    }

    fun updateEmail(value: String) {
        mutableState.value = mutableState.value.copy(email = value, error = null)
    }

    fun updatePassword(value: String) {
        mutableState.value = mutableState.value.copy(password = value, error = null)
    }

    fun updateConfirmPassword(value: String) {
        mutableState.value = mutableState.value.copy(confirmPassword = value, error = null)
    }

    fun submit() {
        val current = mutableState.value
        when {
            current.name.trim().length < 2 -> {
                mutableState.value = current.copy(error = AppError("validation_error", "Informe um nome válido."))
            }

            current.password.length < 8 -> {
                mutableState.value = current.copy(error = AppError("validation_error", "A senha precisa ter pelo menos 8 caracteres."))
            }

            current.password != current.confirmPassword -> {
                mutableState.value = current.copy(error = AppError("validation_error", "As senhas não coincidem."))
            }

            else -> viewModelScope.launch {
                mutableState.value = current.copy(isSubmitting = true, error = null)
                when (val result = registerUseCase(current.name.trim(), current.email.trim(), current.password)) {
                    is AppResult.Success -> mutableState.value = mutableState.value.copy(isSubmitting = false, error = null)
                    is AppResult.Error -> mutableState.value = mutableState.value.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    onNavigateRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(title = "Acessar", modifier = modifier) {
        AppBrandLockup()
        HeroCard(
            eyebrow = "Energia com inteligência",
            title = "Entenda sua conta com mais clareza",
            supporting = "Acompanhe consumo, revise leituras automáticas e antecipe gastos em uma experiência confiável, moderna e fácil de usar.",
        )

        AppCard(
            title = "Entrar",
            supporting = "Use sua conta para enviar faturas, revisar dados e acompanhar projeções de consumo.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { InlineWarning(text = it.message) }
                AppTextField(
                    value = state.email,
                    onValueChange = viewModel::updateEmail,
                    label = "E-mail",
                )
                AppTextField(
                    value = state.password,
                    onValueChange = viewModel::updatePassword,
                    label = "Senha",
                    isPassword = true,
                )
                PrimaryActionButton(
                    text = "Entrar",
                    onClick = viewModel::submit,
                    loading = state.isSubmitting,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    androidx.compose.material3.TextButton(onClick = onNavigateRegister) {
                        androidx.compose.material3.Text(text = "Criar conta")
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EnergyScreen(
        title = "Criar conta",
        modifier = modifier,
        showBack = true,
        onBack = onNavigateBack,
    ) {
        AppBrandLockup(
            subtitle = "Crie sua conta para acompanhar consumo, custos e projeções em um só lugar.",
        )
        AppCard(
            title = "Criar sua conta",
            supporting = "Seu perfil reúne histórico, revisões manuais e projeções para manter a gestão da energia mais simples e segura.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { InlineWarning(text = it.message) }
                AppTextField(value = state.name, onValueChange = viewModel::updateName, label = "Nome completo")
                AppTextField(value = state.email, onValueChange = viewModel::updateEmail, label = "E-mail")
                AppTextField(value = state.password, onValueChange = viewModel::updatePassword, label = "Senha", isPassword = true)
                AppTextField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::updateConfirmPassword,
                    label = "Confirmar senha",
                    isPassword = true,
                )
                PrimaryActionButton(
                    text = "Criar conta",
                    onClick = viewModel::submit,
                    loading = state.isSubmitting,
                )
            }
        }
    }
}
