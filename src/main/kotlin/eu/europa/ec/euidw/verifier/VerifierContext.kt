package eu.europa.ec.euidw.verifier

import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import eu.europa.ec.euidw.verifier.EmbedOptionEnum.byReference
import eu.europa.ec.euidw.verifier.EmbedOptionEnum.byValue
import eu.europa.ec.euidw.verifier.adapter.`in`.timer.ScheduleTimeoutPresentations
import eu.europa.ec.euidw.verifier.adapter.`in`.web.StaticContent
import eu.europa.ec.euidw.verifier.adapter.`in`.web.VerifierApi
import eu.europa.ec.euidw.verifier.adapter.`in`.web.WalletApi
import eu.europa.ec.euidw.verifier.adapter.out.cfg.GeneratePresentationIdNimbus
import eu.europa.ec.euidw.verifier.adapter.out.cfg.GenerateRequestIdNimbus
import eu.europa.ec.euidw.verifier.adapter.out.jose.SignRequestObjectNimbus
import eu.europa.ec.euidw.verifier.adapter.out.persistence.PresentationInMemoryRepo
import eu.europa.ec.euidw.verifier.application.port.`in`.*
import eu.europa.ec.euidw.verifier.application.port.out.cfg.GeneratePresentationId
import eu.europa.ec.euidw.verifier.application.port.out.cfg.GenerateRequestId
import eu.europa.ec.euidw.verifier.application.port.out.jose.SignRequestObject
import eu.europa.ec.euidw.verifier.application.port.out.persistence.LoadIncompletePresentationsOlderThan
import eu.europa.ec.euidw.verifier.application.port.out.persistence.LoadPresentationById
import eu.europa.ec.euidw.verifier.application.port.out.persistence.LoadPresentationByRequestId
import eu.europa.ec.euidw.verifier.application.port.out.persistence.StorePresentation
import eu.europa.ec.euidw.verifier.domain.ClientMetaData
import eu.europa.ec.euidw.verifier.domain.EmbedOption
import eu.europa.ec.euidw.verifier.domain.VerifierConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.core.env.Environment
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.function.server.RouterFunction
import java.net.URL
import java.time.Clock
import java.time.Duration
import java.util.*

@Configuration
@EnableWebFlux
class MyConfig : WebFluxConfigurer {
    override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
        configurer.defaultCodecs().enableLoggingRequestDetails(true)
    }

}

@Configuration
@EnableScheduling
class ScheduleSupport {

}

@Configuration
class VerifierContext(environment: Environment) {

    val verifierConfig = environment.verifierConfig()

    //
    // End points
    //

    @Bean
    fun route(webApi: WalletApi, verifierApi: VerifierApi, staticContent: StaticContent): RouterFunction<*> =
        webApi.route.and(verifierApi.route).and(staticContent.route)

    @Bean
    fun webApi(
        getRequestObject: GetRequestObject,
        getPresentationDefinition: GetPresentationDefinition,
        postWalletResponse: PostWalletResponse,
        rsaKey: RSAKey
    ): WalletApi =
        WalletApi(getRequestObject, getPresentationDefinition, postWalletResponse, rsaKey)

    @Bean
    fun verifierApi(
        initTransaction: InitTransaction,
        getWalletResponse: GetWalletResponse
    ): VerifierApi = VerifierApi(initTransaction, getWalletResponse)

    @Bean
    fun staticApi(): StaticContent = StaticContent()

    //
    // Scheduled
    //
    @Bean
    fun scheduleTimeoutPresentations(timeoutPresentations: TimeoutPresentations): ScheduleTimeoutPresentations =
        ScheduleTimeoutPresentations(timeoutPresentations)

    //
    // Use cases
    //

    @Bean
    fun initTransaction(
        generatePresentationId: GeneratePresentationId,
        generateRequestId: GenerateRequestId,
        storePresentation: StorePresentation,
        signRequestObject: SignRequestObject,
        clock: Clock
    ): InitTransaction = InitTransactionLive(
        generatePresentationId,
        generateRequestId,
        storePresentation,
        signRequestObject,
        verifierConfig,
        clock
    )

    @Bean
    fun getRequestObject(
        loadPresentationByRequestId: LoadPresentationByRequestId,
        signRequestObject: SignRequestObject,
        storePresentation: StorePresentation,
        clock: Clock
    ): GetRequestObject = GetRequestObjectLive(
        loadPresentationByRequestId,
        storePresentation,
        signRequestObject,
        verifierConfig,
        clock
    )

    @Bean
    fun getPresentationDefinition(
        loadPresentationByRequestId: LoadPresentationByRequestId
    ): GetPresentationDefinition =
        GetPresentationDefinitionLive(loadPresentationByRequestId)

    @Bean
    fun timeoutPresentations(
        loadIncompletePresentationsOlderThan: LoadIncompletePresentationsOlderThan,
        storePresentation: StorePresentation,
        clock: Clock
    ): TimeoutPresentations = TimeoutPresentationsLive(
        loadIncompletePresentationsOlderThan,
        storePresentation,
        verifierConfig.maxAge,
        clock
    )

    @Bean
    fun postAuthorisationResponse(
        loadPresentationByRequestId: LoadPresentationByRequestId,
        storePresentation: StorePresentation,
        clock: Clock
    ): PostWalletResponse = PostWalletResponseLive(
        loadPresentationByRequestId,
        storePresentation,
        clock
    )

    @Bean
    fun getWalletResponse(
        loadPresentationById: LoadPresentationById
    ): GetWalletResponse =
        GetWalletResponseLive(loadPresentationById)


    //
    // JOSE
    //


    @Bean
    fun rsaJwk(clock: Clock): RSAKey =
        RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key (optional)
            .keyID(UUID.randomUUID().toString()) // give the key a unique ID (optional)
            .issueTime(Date.from(clock.instant())) // issued-at timestamp (optional)
            .generate()

    @Lazy
    @Bean
    fun signRequestObject(rsaKey: RSAKey): SignRequestObject =
        SignRequestObjectNimbus(rsaKey)

    //
    // Persistence
    //

    @Bean
    fun generatePresentationId(): GeneratePresentationId = GeneratePresentationIdNimbus(64)

    @Bean
    fun generateRequestId(): GenerateRequestId = GenerateRequestIdNimbus(64)

    @Bean
    fun loadPresentationById(presentationInMemoryRepo: PresentationInMemoryRepo): LoadPresentationById =
        presentationInMemoryRepo.loadPresentationById

    @Bean
    fun loadPresentationByRequestId(presentationInMemoryRepo: PresentationInMemoryRepo): LoadPresentationByRequestId =
        presentationInMemoryRepo.loadPresentationByRequestId

    @Bean
    fun storePresentation(presentationInMemoryRepo: PresentationInMemoryRepo): StorePresentation =
        presentationInMemoryRepo.storePresentation

    @Bean
    fun loadIncompletePresentationsOlderThan(presentationInMemoryRepo: PresentationInMemoryRepo): LoadIncompletePresentationsOlderThan =
        presentationInMemoryRepo.loadIncompletePresentationsOlderThan

    @Bean
    fun presentationInMemoryRepo(): PresentationInMemoryRepo =
        PresentationInMemoryRepo()

    @Bean
    fun clock(): Clock {
        return Clock.systemDefaultZone()
    }

}

private enum class EmbedOptionEnum {
    byValue,
    byReference
}

private fun Environment.verifierConfig(): VerifierConfig {


    val clientId = getProperty("verifier.clientId", "verifier")
    val clientIdScheme = getProperty("verifier.clientIdScheme", "pre-regis tered")
    val publicUrl = getProperty("verifier.publicUrl", "http://localhost:8080")
    val requestJarOption = getProperty("verifier.requestJwt.embed", EmbedOptionEnum::class.java).let {
        when (it) {
            byValue -> EmbedOption.ByValue
            byReference, null -> WalletApi.requestJwtByReference(publicUrl)
        }
    }
    val presentationDefinitionEmbedOption =
        getProperty("verifier.presentationDefinition.embed", EmbedOptionEnum::class.java).let {
            when (it) {
                byReference -> WalletApi.presentationDefinitionByReference(publicUrl)
                byValue, null -> EmbedOption.ByValue
            }
        }
    val maxAge = getProperty("verifier.maxAge", Duration::class.java) ?: Duration.ofSeconds(60)

    return VerifierConfig(
        clientId = clientId,
        clientIdScheme = clientIdScheme,
        requestJarOption = requestJarOption,
        presentationDefinitionEmbedOption = presentationDefinitionEmbedOption,
        responseUriBuilder = { _ -> URL("https://foo") },
        maxAge = maxAge,
        clientMetaData = clientMetaData(publicUrl)
    )

}

private fun Environment.clientMetaData(publicUrl: String): ClientMetaData {
    val jwkOption = getProperty("verifier.jwk.embed", EmbedOptionEnum::class.java).let {
        when (it) {
            byReference -> WalletApi.publicJwkSet(publicUrl)
            byValue, null -> EmbedOption.ByValue
        }
    }
    return ClientMetaData(
        jwkOption = jwkOption,
        idTokenSignedResponseAlg = "RS256",
        idTokenEncryptedResponseAlg = "RS256",
        idTokenEncryptedResponseEnc = "A128CBC-HS256",
        subjectSyntaxTypesSupported = listOf(
            "urn:ietf:params:oauth:jwk-thumbprint",
            "did:example",
            "did:key"
        )
    )
}