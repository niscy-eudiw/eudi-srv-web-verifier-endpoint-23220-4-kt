package eu.europa.ec.eudi.verifier.endpoint.port.`in`

sealed interface QueryResponse<out T : Any> {
    object NotFound : QueryResponse<Nothing>
    object InvalidState : QueryResponse<Nothing>
    data class Found<T : Any>(val value: T) : QueryResponse<T>
}
