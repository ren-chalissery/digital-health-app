import Foundation
import SimplicityApi

public final class ReflectionServiceImpl: ReflectionService {

    // MARK: Types

    public typealias List = @Sendable (String?) async throws -> [ReflectionResponse]
    public typealias Write = @Sendable (WriteReflectionRequest) async throws -> ReflectionResponse
    public typealias Edit = @Sendable (UUID, WriteReflectionRequest) async throws
        -> ReflectionResponse
    public typealias Delete = @Sendable (UUID) async throws -> Void

    // MARK: Properties

    private let listCall: List
    private let writeCall: Write
    private let editCall: Edit
    private let deleteCall: Delete

    // MARK: Init

    public init(
        list: @escaping List = { query in
            try await ReflectionsAPI.listReflections(q: query)
        },
        write: @escaping Write = { request in
            try await ReflectionsAPI.writeReflection(writeReflectionRequest: request)
        },
        edit: @escaping Edit = { id, request in
            try await ReflectionsAPI.editReflection(
                reflectionId: id,
                writeReflectionRequest: request
            )
        },
        delete: @escaping Delete = { id in
            try await ReflectionsAPI.deleteReflection(reflectionId: id)
        }
    ) {
        self.listCall = list
        self.writeCall = write
        self.editCall = edit
        self.deleteCall = delete
    }

    // MARK: Functions

    /// An empty query is sent as nil rather than as "", because the server treats a present but
    /// empty search term differently from no search term at all.
    public func list(query: String?) async throws -> [ReflectionResponse] {
        let trimmed = query?.trimmingCharacters(in: .whitespaces)
        return try await listCall(trimmed?.isEmpty == false ? trimmed : nil)
    }

    public func write(title: String?, body: String) async throws -> ReflectionResponse {
        try await writeCall(WriteReflectionRequest(body: body, title: title))
    }

    public func edit(id: UUID, title: String?, body: String) async throws -> ReflectionResponse {
        try await editCall(id, WriteReflectionRequest(body: body, title: title))
    }

    public func delete(id: UUID) async throws {
        try await deleteCall(id)
    }
}
