import Foundation
import Mockable
import SimplicityApi

/// The clinician's private journal.
///
/// Note the absence of an organisation anywhere in this protocol. A reflection belongs to a
/// person, not to a workplace — the endpoints are under `/api/v1/me` — and this must not acquire
/// an `orgId` for symmetry with the other services.
@Mockable
public protocol ReflectionService: AnyObject, Sendable {
    func list(query: String?) async throws -> [ReflectionResponse]
    func write(title: String?, body: String) async throws -> ReflectionResponse
    func edit(id: UUID, title: String?, body: String) async throws -> ReflectionResponse
    func delete(id: UUID) async throws
}
