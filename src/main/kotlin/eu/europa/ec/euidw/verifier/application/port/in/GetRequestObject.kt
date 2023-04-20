package eu.europa.ec.euidw.verifier.application.port.`in`

import eu.europa.ec.euidw.prex.PresentationExchange
import eu.europa.ec.euidw.verifier.application.port.out.jose.SignRequestObject
import eu.europa.ec.euidw.verifier.application.port.out.persistence.LoadPresentationById
import eu.europa.ec.euidw.verifier.application.port.out.persistence.StorePresentation
import eu.europa.ec.euidw.verifier.domain.*
import java.net.URL
import java.time.Instant

data class RequestObject(
    val clientId: String,
    val clientIdScheme: String,
    val responseType: List<String>,
    val presentationDefinitionUri: URL?,
    val presentationDefinition: String? = null,
    val scope: List<String>,
    val idTokenType: List<String>,
    val nonce: String,
    val responseMode: String,
    val responseUri: URL?,
    val aud: List<String>,
    val state: String?
)


interface GetRequestObject {
    suspend operator fun invoke(presentationId: PresentationId): QueryResponse<Jwt>

    companion object {
        fun live(
            loadPresentationById: LoadPresentationById,
            storePresentation: StorePresentation,
            signRequestObject: SignRequestObject,
            verifierConfig: VerifierConfig
        ): GetRequestObject =
            GetRequestObjectLive(loadPresentationById, storePresentation,signRequestObject, verifierConfig)
    }
}

internal class GetRequestObjectLive(
    private val loadPresentationById: LoadPresentationById,
    private val storePresentation: StorePresentation,
    private val signRequestObject: SignRequestObject,
    private val verifierConfig: VerifierConfig
) : GetRequestObject {

    override suspend operator fun invoke(presentationId: PresentationId): QueryResponse<Jwt> =
        when (val presentation = loadPresentationById(presentationId)) {
            null -> QueryResponse.NotFound
            is Presentation.Requested -> {
                val jwt = signedRequestObjectOf(presentation).getOrThrow()
                markAsRequestObjectRetrieved(presentation, Instant.now())
                QueryResponse.Found(jwt)
            }

            else -> QueryResponse.InvalidState
        }

    private suspend fun markAsRequestObjectRetrieved(presentation: Presentation.Requested, at: Instant) {
        val updatedPresentation = presentation.requestObjectRetrieved(at).getOrThrow()
        storePresentation(updatedPresentation)
    }

    private fun signedRequestObjectOf(presentation: Presentation.Requested): Result<Jwt> {
        val requestObject = requestObjectOf(presentation)
        return signRequestObject(requestObject)
    }

    private fun requestObjectOf(presentation: Presentation.Requested): RequestObject =
        RequestObject(
            clientId = verifierConfig.clientId,
            clientIdScheme = verifierConfig.clientIdScheme,
            scope = when (presentation.type) {
                is PresentationType.IdTokenRequest -> listOf("openid")
                is PresentationType.VpTokenRequest -> emptyList()
                is PresentationType.IdAndVpToken -> listOf("openid")
            },
            idTokenType = when (presentation.type) {
                is PresentationType.IdTokenRequest -> presentation.type.idTokenType
                is PresentationType.VpTokenRequest -> emptyList()
                is PresentationType.IdAndVpToken -> presentation.type.idTokenType
            }.map { it.asString() },
            presentationDefinitionUri = when (presentation.type) {
                is PresentationType.IdTokenRequest -> null
                else -> verifierConfig.presentationDefinitionUriBuilder?.build(presentation.id)
            },
            presentationDefinition = when (val type = presentation.type) {
                is PresentationType.IdTokenRequest -> null
                is PresentationType.VpTokenRequest ->
                    if (verifierConfig.presentationDefinitionUriBuilder == null) with(PresentationExchange.jsonParser){type.presentationDefinition.encode()}
                    else null
                is PresentationType.IdAndVpToken ->
                    if (verifierConfig.presentationDefinitionUriBuilder == null) with(PresentationExchange.jsonParser){type.presentationDefinition.encode()}
                    else null
            },
            responseType = when (presentation.type) {
                is PresentationType.IdTokenRequest -> listOf("id_token")
                is PresentationType.VpTokenRequest -> listOf("vp_token")
                is PresentationType.IdAndVpToken -> listOf("vp_token", "id_token")
            },
            aud = when (presentation.type) {
                is PresentationType.IdTokenRequest -> emptyList()
                else -> listOf("https://self-issued.me/v2")
            },
            nonce = presentation.id.value.toString(),
            state = null,
            responseMode = "direct_post.jwt",
            responseUri = verifierConfig.responseUriBuilder.build(presentation.id)
        )

    private fun IdTokenType.asString(): String = when (this) {
        IdTokenType.AttesterSigned -> "attester_signed_id_token"
        IdTokenType.SubjectSigned -> "subject_signed_id_token"
    }
}



