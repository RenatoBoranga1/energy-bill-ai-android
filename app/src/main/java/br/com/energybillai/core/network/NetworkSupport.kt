package br.com.energybillai.core.network

import br.com.energybillai.BuildConfig
import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.data.local.SessionStore
import br.com.energybillai.data.remote.AuthRefreshRequestDto
import br.com.energybillai.data.remote.AuthService
import br.com.energybillai.data.remote.ErrorResponseDto
import br.com.energybillai.data.remote.toDomainSession
import com.google.gson.Gson
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException

@Singleton
class AuthHeaderInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val accessToken = sessionStore.currentSession()?.tokens?.accessToken
        val authenticatedRequest = if (!accessToken.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            original
        }
        return chain.proceed(authenticatedRequest)
    }
}

@Singleton
class RefreshTokenAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    private val refreshService: dagger.Lazy<AuthService>,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/api/v1/auth/")) {
            return null
        }

        if (responseCount(response) >= 2) {
            return null
        }

        val currentSession = sessionStore.currentSession() ?: return null
        if (!currentSession.tokens.canRefresh()) {
            runBlocking { sessionStore.clear() }
            return null
        }

        synchronized(lock) {
            val latestSession = sessionStore.currentSession() ?: return null
            val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (requestToken != null && requestToken != latestSession.tokens.accessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${latestSession.tokens.accessToken}")
                    .build()
            }

            return try {
                val tokenResponse = runBlocking {
                    refreshService.get().refresh(AuthRefreshRequestDto(refreshToken = latestSession.tokens.refreshToken))
                }
                val refreshedSession = tokenResponse.toDomainSession(issuedAtEpochSeconds = Instant.now().epochSecond)
                runBlocking { sessionStore.saveSession(refreshedSession) }
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshedSession.tokens.accessToken}")
                    .build()
            } catch (_: Exception) {
                runBlocking { sessionStore.clear() }
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var current = response.priorResponse
        while (current != null) {
            result++
            current = current.priorResponse
        }
        return result
    }
}

suspend fun <T> safeApiCall(
    gson: Gson,
    block: suspend () -> T,
): AppResult<T> {
    return try {
        AppResult.Success(block())
    } catch (exception: HttpException) {
        AppResult.Error(
            parseHttpError(
                gson = gson,
                statusCode = exception.code(),
                rawBody = exception.response()?.errorBody()?.string(),
                fallbackMessage = exception.message(),
            ),
        )
    } catch (exception: IOException) {
        AppResult.Error(
            mapNetworkError(exception),
        )
    } catch (exception: Exception) {
        AppResult.Error(
            AppError(
                code = "unexpected_error",
                message = exception.message ?: "Ocorreu um erro inesperado.",
            ),
        )
    }
}

private fun mapNetworkError(exception: IOException): AppError {
    val rawMessage = exception.message.orEmpty()
    val normalizedMessage = rawMessage.lowercase()
    val isProduction = BuildConfig.API_ENVIRONMENT.equals("production", ignoreCase = true)
    return when {
        exception is SocketTimeoutException -> AppError(
            code = "network_timeout",
            message = if (isProduction) {
                "Conectando ao servidor. A primeira requisicao pode levar alguns segundos. Tente novamente em instantes."
            } else {
                "Tempo de resposta excedido. Verifique se o backend esta ativo e tente novamente."
            },
        )

        exception is UnknownHostException -> AppError(
            code = "network_unknown_host",
            message = if (isProduction) {
                "Nao foi possivel localizar o servidor. Verifique sua conexao com a internet e tente novamente."
            } else {
                "Servidor nao encontrado. Verifique o IP configurado e se o tablet esta na mesma rede Wi-Fi."
            },
        )

        exception is NoRouteToHostException -> AppError(
            code = "network_no_route",
            message = if (isProduction) {
                "Nao foi possivel alcancar o servidor. Confira sua conexao com a internet e tente novamente."
            } else {
                "Nao ha rota ate o backend. Confirme se o tablet e o computador estao na mesma rede."
            },
        )

        exception is ConnectException -> AppError(
            code = "network_connection_refused",
            message = if (isProduction) {
                "Nao foi possivel conectar ao servidor. Tente novamente em instantes."
            } else {
                "Nao foi possivel conectar ao backend. Verifique se o FastAPI esta rodando com --host 0.0.0.0 --port 8000 e se o firewall liberou a porta."
            },
        )

        exception is UnknownServiceException && normalizedMessage.contains("cleartext") -> AppError(
            code = "network_cleartext_blocked",
            message = if (isProduction) {
                "A conexao com o servidor falhou por configuracao de rede. Tente novamente."
            } else {
                "O Android bloqueou HTTP sem criptografia. Verifique a configuracao de network security para o IP local."
            },
        )

        normalizedMessage.contains("failed to connect") -> AppError(
            code = "network_connection_failed",
            message = if (isProduction) {
                "Nao foi possivel conectar ao servidor. Tente novamente."
            } else {
                "Falha ao conectar no backend. Confirme IP, porta 8000, firewall e se o servidor esta ligado."
            },
        )

        else -> AppError(
            code = "network_error",
            message = if (isProduction) {
                "Nao foi possivel conectar ao servidor. Tente novamente."
            } else {
                "Nao foi possivel conectar ao servidor. Verifique se o backend esta ativo e se o tablet esta na mesma rede."
            },
        )
    }
}

private fun parseHttpError(
    gson: Gson,
    statusCode: Int,
    rawBody: String?,
    fallbackMessage: String?,
): AppError {
    val parsed = runCatching { gson.fromJson(rawBody, ErrorResponseDto::class.java) }.getOrNull()
    if (statusCode == 401) {
        return AppError(
            code = parsed?.error?.code ?: "unauthorized",
            message = "Sessao expirada. Faca login novamente.",
            statusCode = statusCode,
        )
    }
    return AppError(
        code = parsed?.error?.code ?: "http_error",
        message = parsed?.error?.message ?: fallbackMessage ?: "Erro HTTP inesperado.",
        statusCode = statusCode,
    )
}
