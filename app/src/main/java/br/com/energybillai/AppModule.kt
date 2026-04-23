package br.com.energybillai

import br.com.energybillai.core.network.ApiConfig
import br.com.energybillai.core.network.AuthHeaderInterceptor
import br.com.energybillai.core.network.RefreshTokenAuthenticator
import br.com.energybillai.data.local.BillHistoryDao
import br.com.energybillai.data.local.DatabaseFactory
import br.com.energybillai.data.local.EnergyBillDatabase
import br.com.energybillai.data.repository.DefaultAuthRepository
import br.com.energybillai.data.repository.DefaultBillRepository
import br.com.energybillai.data.repository.DefaultSessionRepository
import br.com.energybillai.data.remote.AuthService
import br.com.energybillai.data.remote.BillService
import br.com.energybillai.data.remote.DocumentService
import br.com.energybillai.domain.repository.AuthRepository
import br.com.energybillai.domain.repository.BillRepository
import br.com.energybillai.domain.repository.SessionRepository
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApiConfig(): ApiConfig {
        return ApiConfig.create(
            baseUrl = BuildConfig.API_BASE_URL,
            environment = BuildConfig.API_ENVIRONMENT,
        )
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
    }

    @Provides
    @Singleton
    fun provideDatabase(factory: DatabaseFactory): EnergyBillDatabase = factory.create()

    @Provides
    fun provideBillHistoryDao(database: EnergyBillDatabase): BillHistoryDao = database.billHistoryDao()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    }

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainOkHttp(
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthedOkHttp(
        loggingInterceptor: HttpLoggingInterceptor,
        authHeaderInterceptor: AuthHeaderInterceptor,
        refreshTokenAuthenticator: RefreshTokenAuthenticator,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(refreshTokenAuthenticator)
            .build()
    }

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainRetrofit(
        apiConfig: ApiConfig,
        gson: Gson,
        @Named("plain") okHttpClient: OkHttpClient,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(apiConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthedRetrofit(
        apiConfig: ApiConfig,
        gson: Gson,
        okHttpClient: OkHttpClient,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(apiConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthService(@Named("plain") retrofit: Retrofit): AuthService = retrofit.create(AuthService::class.java)

    @Provides
    @Singleton
    fun provideDocumentService(retrofit: Retrofit): DocumentService = retrofit.create(DocumentService::class.java)

    @Provides
    @Singleton
    fun provideBillService(retrofit: Retrofit): BillService = retrofit.create(BillService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(implementation: DefaultSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindBillRepository(implementation: DefaultBillRepository): BillRepository
}
