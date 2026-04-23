package br.com.energybillai.core.network

import br.com.energybillai.core.common.AppError
import br.com.energybillai.core.common.AppResult
import br.com.energybillai.data.local.SessionStore
import br.com.energybillai.data.remote.AuthRefreshRequestDto
import br.com.energybillai.data.remote.AuthService
import br.com.energybillai.data.remote.ErrorResponseDto
import br.com.energybillai.data.remote.toDomainSession
import com.google.gson.Gson
import java.io.IOException
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
    } catch (_: IOException) {
        AppResult.Error(
            AppError(
                code = "network_error",
                message = "Nao foi possivel conectar ao servidor.",
            ),
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

private fun parseHttpError(
    gson: Gson,
    statusCode: Int,
    rawBody: String?,
    fallbackMessage: String?,
): AppError {
    val parsed = runCatching { gson.fromJson(rawBody, ErrorResponseDto::class.java) }.getOrNull()
    return AppError(
        code = parsed?.error?.code ?: "http_error",
        message = parsed?.error?.message ?: fallbackMessage ?: "Erro HTTP inesperado.",
        statusCode = statusCode,
    )
}
