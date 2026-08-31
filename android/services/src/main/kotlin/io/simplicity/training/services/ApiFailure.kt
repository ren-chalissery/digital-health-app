package io.simplicity.training.services

/**
 * A non-2xx, turned into something a view model can act on.
 *
 * The generated client hands back a Retrofit `Response`, which forces every caller to remember to
 * check `isSuccessful`. Forgetting produces a null body and a confusing crash far from the cause,
 * so the services check once and throw.
 */
class ApiFailure(val status: Int, message: String) : RuntimeException(message)

/** Unwraps a response, or throws [ApiFailure]. */
internal fun <T> retrofit2.Response<T>.unwrap(): T {
    if (!isSuccessful) {
        throw ApiFailure(code(), "Request failed with ${code()}")
    }
    return body() ?: throw ApiFailure(code(), "Request succeeded with no body")
}
