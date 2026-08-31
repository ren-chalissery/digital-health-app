package io.simplicity.training.services

import io.simplicity.training.api.apis.ReflectionsApi
import io.simplicity.training.api.models.ReflectionResponse
import io.simplicity.training.api.models.WriteReflectionRequest
import java.util.UUID

/**
 * A clinician's private journal.
 *
 * Nothing here is organisation-scoped, deliberately: a reflection belongs to its author and to
 * nobody else, which is the promise the product makes and the reason the endpoints take no org id.
 */
interface ReflectionService {
    suspend fun list(query: String? = null): List<ReflectionResponse>
    suspend fun write(title: String?, body: String): ReflectionResponse
    suspend fun edit(id: UUID, title: String?, body: String): ReflectionResponse
    suspend fun delete(id: UUID)
}

class ReflectionServiceImpl(private val api: ReflectionsApi) : ReflectionService {

    override suspend fun list(query: String?) = api.listReflections(query?.takeIf { it.isNotBlank() }).unwrap()

    override suspend fun write(title: String?, body: String) =
        api.writeReflection(WriteReflectionRequest(body = body, title = title)).unwrap()

    override suspend fun edit(id: UUID, title: String?, body: String) =
        api.editReflection(id, WriteReflectionRequest(body = body, title = title)).unwrap()

    override suspend fun delete(id: UUID) {
        val response = api.deleteReflection(id)
        if (!response.isSuccessful) throw ApiFailure(response.code(), "Delete failed")
    }
}
