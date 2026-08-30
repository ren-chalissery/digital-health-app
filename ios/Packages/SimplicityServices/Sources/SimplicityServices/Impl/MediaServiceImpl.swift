import Foundation
import SimplicityApi

public final class MediaServiceImpl: MediaService {

    // MARK: Types

    public typealias List = @Sendable (UUID) async throws -> [MediaAssetResponse]
    public typealias Register = @Sendable (UUID, RegisterUploadRequest) async throws
        -> UploadTargetResponse
    public typealias MarkUploaded = @Sendable (UUID, UUID) async throws -> Void
    public typealias SetCaptions = @Sendable (UUID, UUID, String) async throws -> Void
    public typealias RemoveCaptions = @Sendable (UUID, UUID) async throws -> Void
    public typealias Delete = @Sendable (UUID, UUID) async throws -> Void

    // MARK: Properties

    private let listCall: List
    private let registerCall: Register
    private let markUploadedCall: MarkUploaded
    private let setCaptionsCall: SetCaptions
    private let removeCaptionsCall: RemoveCaptions
    private let deleteCall: Delete

    // MARK: Init

    public init(
        list: @escaping List = { orgId in try await MediaAPI.listMedia(orgId: orgId) },
        register: @escaping Register = { orgId, request in
            try await MediaAPI.registerUpload(orgId: orgId, registerUploadRequest: request)
        },
        markUploaded: @escaping MarkUploaded = { orgId, assetId in
            try await MediaAPI.completeUpload(orgId: orgId, assetId: assetId)
        },
        setCaptions: @escaping SetCaptions = { orgId, assetId, vtt in
            try await MediaAPI.setCaptions(orgId: orgId, assetId: assetId, body: vtt)
        },
        removeCaptions: @escaping RemoveCaptions = { orgId, assetId in
            try await MediaAPI.removeCaptions(orgId: orgId, assetId: assetId)
        },
        delete: @escaping Delete = { orgId, assetId in
            try await MediaAPI.deleteMedia(orgId: orgId, assetId: assetId)
        }
    ) {
        self.listCall = list
        self.registerCall = register
        self.markUploadedCall = markUploaded
        self.setCaptionsCall = setCaptions
        self.removeCaptionsCall = removeCaptions
        self.deleteCall = delete
    }

    // MARK: Functions

    public func list(orgId: UUID) async throws -> [MediaAssetResponse] {
        try await listCall(orgId)
    }

    public func register(
        orgId: UUID,
        filename: String,
        contentType: String,
        sizeBytes: Int64
    ) async throws -> UploadTargetResponse {
        try await registerCall(
            orgId,
            RegisterUploadRequest(
                contentType: contentType,
                filename: filename,
                sizeBytes: sizeBytes
            )
        )
    }

    public func markUploaded(orgId: UUID, assetId: UUID) async throws {
        try await markUploadedCall(orgId, assetId)
    }

    public func asset(orgId: UUID, assetId: UUID) async throws -> MediaAssetResponse? {
        try await listCall(orgId).first { $0.assetId == assetId }
    }

    public func setCaptions(orgId: UUID, assetId: UUID, vtt: String) async throws {
        try await setCaptionsCall(orgId, assetId, vtt)
    }

    public func removeCaptions(orgId: UUID, assetId: UUID) async throws {
        try await removeCaptionsCall(orgId, assetId)
    }

    public func delete(orgId: UUID, assetId: UUID) async throws {
        try await deleteCall(orgId, assetId)
    }
}
