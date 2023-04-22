package eu.europa.ec.euidw.verifier.adapter.out.jose

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.euidw.verifier.TestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URL
import java.net.URLEncoder
import java.util.*


class SignRequestObjectNimbusTest {

    private val signRequestObject = TestContext.singRequestObject
    private val verifier = TestContext.singRequestObjectVerifier

    @Test
    fun `given a request object, it should be signed and decoded`() {


        val requestObject = RequestObject(
            clientId = "client-id",
            clientIdScheme = "pre-registered",
            responseType = listOf("vp_token", "id_token"),
            presentationDefinitionUri = null,
            scope = listOf("openid"),
            idTokenType = listOf("subject_signed_id_token"),
            nonce = UUID.randomUUID().toString(),
            responseMode = "direct_post.jwt",
            responseUri = URL("https://foo"),
            state = TestContext.testRequestId.value,
            aud = emptyList()
        )

        val jwt = signRequestObject.sign(requestObject).getOrThrow().also { println(it) }
        val claimSet = decode(jwt).getOrThrow().also { println(it) }

        assertEqualsRequestObjectJWTClaimSet(requestObject, claimSet)
    }

    private fun decode(jwt: String): Result<JWTClaimsSet> {

        return runCatching {
            val signedJWT = SignedJWT.parse(jwt)
            signedJWT.verify(verifier)
            signedJWT.jwtClaimsSet
        }
    }

    private fun assertEqualsRequestObjectJWTClaimSet(r: RequestObject, c: JWTClaimsSet) {

        assertEquals(r.clientId, c.getStringClaim("client_id"))
        assertEquals(r.clientIdScheme, c.getStringClaim("client_id_scheme"))
        assertEquals(r.responseType.joinToString(separator = " "), c.getStringClaim("response_type"))
        assertEquals(r.presentationDefinitionUri?.urlEncoded(), c.getStringClaim("presentation_definition_uri"))
        assertEquals(r.scope.joinToString(separator = " "), c.getStringClaim("scope"))
        assertEquals(r.idTokenType.joinToString(separator = " "), c.getStringClaim("id_token_type"))
        assertEquals(r.nonce, c.getStringClaim("nonce"))
        assertEquals(r.responseMode, c.getStringClaim("response_mode"))
        assertEquals(r.responseUri?.urlEncoded(), c.getStringClaim("response_uri"))
        assertEquals(r.state, c.getStringClaim("state"))

    }

    private fun URL.urlEncoded() = URLEncoder.encode(toExternalForm(), "UTF-8")
}