package br.com.energybillai.core.network

import java.net.URI

data class ApiConfig(
    val baseUrl: String,
    val environment: ApiEnvironment,
    val connectTimeoutSeconds: Long,
    val readTimeoutSeconds: Long,
    val writeTimeoutSeconds: Long,
) {
    init {
        val uri = URI(baseUrl)
        require(uri.scheme == "http" || uri.scheme == "https") {
            "API base URL must start with http:// or https://"
        }
        require(baseUrl.endsWith("/")) {
            "API base URL must end with /"
        }
        require(!uri.host.isNullOrBlank()) {
            "API base URL must include a valid host"
        }
        require(!uri.isDisallowedDevelopmentHost()) {
            "API base URL must point to a reachable LAN or production host"
        }
        if (environment == ApiEnvironment.PRODUCTION) {
            require(uri.scheme == "https") {
                "Production API base URL must use HTTPS"
            }
        }
        require(connectTimeoutSeconds > 0 && readTimeoutSeconds > 0 && writeTimeoutSeconds > 0) {
            "API timeouts must be positive"
        }
    }

    val isLocal: Boolean = environment == ApiEnvironment.LOCAL
    val isProduction: Boolean = environment == ApiEnvironment.PRODUCTION

    companion object {
        fun create(
            baseUrl: String,
            environment: String,
            connectTimeoutSeconds: Int,
            readTimeoutSeconds: Int,
            writeTimeoutSeconds: Int,
        ): ApiConfig {
            return ApiConfig(
                baseUrl = baseUrl.trim().ensureTrailingSlash(),
                environment = ApiEnvironment.from(environment),
                connectTimeoutSeconds = connectTimeoutSeconds.toLong(),
                readTimeoutSeconds = readTimeoutSeconds.toLong(),
                writeTimeoutSeconds = writeTimeoutSeconds.toLong(),
            )
        }
    }
}

enum class ApiEnvironment(val rawValue: String, val displayName: String) {
    LOCAL("local", "Local"),
    STAGING("staging", "Homologacao"),
    PRODUCTION("production", "Producao"),
    UNKNOWN("unknown", "Personalizado");

    companion object {
        fun from(rawValue: String): ApiEnvironment {
            return entries.firstOrNull { it.rawValue.equals(rawValue.trim(), ignoreCase = true) }
                ?: UNKNOWN
        }
    }
}

private fun String.ensureTrailingSlash(): String {
    return if (endsWith("/")) this else "$this/"
}

private fun URI.isDisallowedDevelopmentHost(): Boolean {
    val normalizedHost = host?.trim()?.lowercase().orEmpty()
    val loopbackHost = listOf(127, 0, 0, 1).joinToString(".")
    val emulatorHostAlias = listOf(10, 0, 2, 2).joinToString(".")
    val localHostName = listOf("local", "host").joinToString("")
    return normalizedHost == loopbackHost ||
        normalizedHost == emulatorHostAlias ||
        normalizedHost == localHostName
}
