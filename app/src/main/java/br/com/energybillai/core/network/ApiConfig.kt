package br.com.energybillai.core.network

data class ApiConfig(
    val baseUrl: String,
    val environment: ApiEnvironment,
) {
    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "API base URL must start with http:// or https://"
        }
        require(baseUrl.endsWith("/")) {
            "API base URL must end with /"
        }
    }

    val isLocal: Boolean = environment == ApiEnvironment.LOCAL
    val isProduction: Boolean = environment == ApiEnvironment.PRODUCTION

    companion object {
        fun create(baseUrl: String, environment: String): ApiConfig {
            return ApiConfig(
                baseUrl = baseUrl.trim().ensureTrailingSlash(),
                environment = ApiEnvironment.from(environment),
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
