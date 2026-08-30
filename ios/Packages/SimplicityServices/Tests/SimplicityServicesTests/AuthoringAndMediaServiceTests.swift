import Foundation
import SimplicityApi
import SimplicityTesting
import Testing

@testable import SimplicityServices

@Suite("MediaService", .serialized)
final class MediaServiceTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let assetId = UUID()
    }

    nonisolated private static func asset(status: String?) -> MediaAssetResponse {
        MediaAssetResponse(assetId: Constants.assetId, filename: "clip.mp4", status: status)
    }

    // MARK: Status

    @Test("each known state maps")
    func mapsKnownStates() {
        #expect(Self.asset(status: "PENDING").mediaStatus == .pending)
        #expect(Self.asset(status: "PROCESSING").mediaStatus == .processing)
        #expect(Self.asset(status: "READY").mediaStatus == .ready)
        #expect(Self.asset(status: "FAILED").mediaStatus == .failed)
    }

    @Test("a state this app does not know is nil rather than treated as ready")
    func unknownStateIsNil() {
        #expect(Self.asset(status: "QUARANTINED").mediaStatus == nil)
        #expect(Self.asset(status: "QUARANTINED").isReady == false)
        #expect(Self.asset(status: nil).mediaStatus == nil)
    }

    @Test("ready and failed both end polling; pending and processing do not")
    func finishedCoversBothTerminalStates() {
        #expect(Self.asset(status: "READY").isFinished)
        #expect(Self.asset(status: "FAILED").isFinished)
        #expect(Self.asset(status: "PENDING").isFinished == false)
        #expect(Self.asset(status: "PROCESSING").isFinished == false)
    }

    // MARK: Registering

    @Test("registering sends the filename, type and size the server needs")
    func registerSendsDetails() async throws {
        let captured = CapturedRegister()
        let service = MediaServiceImpl(register: { _, request in
            await captured.set(request)
            return UploadTargetResponse(assetId: Constants.assetId, uploadUrl: "https://s3/put")
        })

        _ = try await service.register(
            orgId: Constants.orgId,
            filename: "clip.mp4",
            contentType: "video/mp4",
            sizeBytes: 1_234
        )

        #expect(await captured.value?.filename == "clip.mp4")
        #expect(await captured.value?.contentType == "video/mp4")
        #expect(await captured.value?.sizeBytes == 1_234)
    }

    // MARK: Polling one asset

    @Test("one asset is found by listing, since the API has no single-asset endpoint")
    func assetFoundByListing() async throws {
        let service = MediaServiceImpl(list: { _ in
            [
                MediaAssetResponse(assetId: UUID(), filename: "other.mp4", status: "READY"),
                Self.asset(status: "PROCESSING")
            ]
        })

        let found = try await service.asset(orgId: Constants.orgId, assetId: Constants.assetId)

        #expect(found?.mediaStatus == .processing)
    }

    @Test("an asset that is not there is nil rather than an error")
    func missingAssetIsNil() async throws {
        let service = MediaServiceImpl(list: { _ in [] })

        #expect(try await service.asset(orgId: Constants.orgId, assetId: Constants.assetId) == nil)
    }

    private actor CapturedRegister {
        private(set) var value: RegisterUploadRequest?
        func set(_ request: RegisterUploadRequest) { value = request }
    }
}

@Suite("AuthoringService", .serialized)
final class AuthoringServiceTests: SimplicityTestCase {

    private enum Constants {
        static let orgId = UUID()
        static let moduleId = UUID()
    }

    nonisolated private static func module(
        draft: VersionResponse? = nil,
        published: VersionResponse? = nil
    ) -> AuthoredModuleResponse {
        AuthoredModuleResponse(
            draft: draft, moduleId: Constants.moduleId, published: published, title: "A module"
        )
    }

    nonisolated private static func version(_ status: String) -> VersionResponse {
        VersionResponse(status: status, versionId: UUID(), versionNumber: 1)
    }

    // MARK: Shape

    @Test("a module with only a draft is not published")
    func draftOnly() {
        let subject = Self.module(draft: Self.version("DRAFT"))
        #expect(subject.hasDraft)
        #expect(subject.isPublished == false)
        #expect(subject.hasUnpublishedChanges == false)
    }

    @Test("a published module edited since is flagged, because learners see the older version")
    func publishedWithEdits() {
        let subject = Self.module(
            draft: Self.version("DRAFT"), published: Self.version("PUBLISHED")
        )
        #expect(subject.hasUnpublishedChanges)
    }

    // MARK: Creating

    @Test("an empty summary is sent as none rather than as an empty string")
    func emptySummaryBecomesNil() async throws {
        let captured = CapturedCreate()
        let service = AuthoringServiceImpl(create: { _, request in
            await captured.set(request)
            return Self.module()
        })

        _ = try await service.create(orgId: Constants.orgId, title: "Pacing", summary: "  ")

        #expect(await captured.value?.summary == nil)
        #expect(await captured.value?.title == "Pacing")
    }

    // MARK: Publishing

    @Test("publishing without superseding leaves completions alone")
    func publishNotSuperseding() async throws {
        let captured = CapturedPublish()
        let service = AuthoringServiceImpl(publish: { _, _, request in
            await captured.set(request)
            return Self.module()
        })

        _ = try await service.publish(
            orgId: Constants.orgId, moduleId: Constants.moduleId, supersedesCompletions: false
        )

        #expect(await captured.value?.supersedesCompletions == false)
    }

    @Test("publishing with superseding sends true, sending everyone back through it")
    func publishSuperseding() async throws {
        // Written as its own test rather than a loop: getting this flag backwards either
        // un-completes an entire organisation's training or fails to, and a failure should name
        // which direction it went.
        let captured = CapturedPublish()
        let service = AuthoringServiceImpl(publish: { _, _, request in
            await captured.set(request)
            return Self.module()
        })

        _ = try await service.publish(
            orgId: Constants.orgId, moduleId: Constants.moduleId, supersedesCompletions: true
        )

        #expect(await captured.value?.supersedesCompletions == true)
    }

    // MARK: Assignment

    @Test("deselecting every team sends an empty list, which is how a module is unassigned")
    func assignEmptyUnassigns() async throws {
        let captured = CapturedAssign()
        let service = AuthoringServiceImpl(assignTeams: { _, _, request in
            await captured.set(request)
            return Self.module()
        })

        _ = try await service.assignTeams(
            orgId: Constants.orgId, moduleId: Constants.moduleId, teamIds: []
        )

        #expect(await captured.value?.teamIds.isEmpty == true)
    }

    private actor CapturedCreate {
        private(set) var value: CreateModuleRequest?
        func set(_ request: CreateModuleRequest) { value = request }
    }

    private actor CapturedPublish {
        private(set) var value: PublishRequest?
        func set(_ request: PublishRequest) { value = request }
    }

    private actor CapturedAssign {
        private(set) var value: AssignTeamsRequest?
        func set(_ request: AssignTeamsRequest) { value = request }
    }
}
