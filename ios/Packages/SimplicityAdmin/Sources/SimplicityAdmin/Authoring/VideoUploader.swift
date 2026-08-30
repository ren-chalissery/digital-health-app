import Foundation

/// Sends a file to a presigned URL.
///
/// A protocol so the view model can be tested without a network, and so the background session —
/// which needs a delegate and outlives the app — stays in one place.
public protocol VideoUploader: Sendable {
    func upload(
        fileURL: URL,
        to target: URL,
        contentType: String,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws
}

public enum VideoUploadError: Error {
    case rejected(statusCode: Int)
    case noResponse
}

/// Uploads on a background `URLSession`, so an upload survives the screen locking or the app being
/// switched away from.
///
/// That is not a nicety here. The server issues a single presigned PUT for the whole file — there
/// are no parts and no resume — so an upload interrupted at 400 MB starts again from nothing.
/// Surviving a backgrounded app is the difference between finishing on hospital wifi and not.
///
/// `uploadTask(with:fromFile:)` is required rather than a data task: a background session refuses
/// in-memory bodies, which is also why the picked video is copied to a temporary file first.
public final class BackgroundVideoUploader: NSObject, VideoUploader {

    // MARK: Types

    private final class State: @unchecked Sendable {
        let lock = NSLock()
        var continuation: CheckedContinuation<Void, Error>?
        var progress: (@Sendable (Double) -> Void)?
    }

    // MARK: Properties

    private let state = State()
    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.background(
            withIdentifier: "io.simplicity.training.upload"
        )
        configuration.isDiscretionary = false
        configuration.sessionSendsLaunchEvents = true
        return URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
    }()

    // MARK: Init

    override public init() {
        super.init()
    }

    // MARK: Functions

    public func upload(
        fileURL: URL,
        to target: URL,
        contentType: String,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws {
        var request = URLRequest(url: target)
        request.httpMethod = "PUT"
        // Must match the content type the URL was signed for, or S3 rejects the PUT.
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")

        try await withCheckedThrowingContinuation { continuation in
            state.lock.withLock {
                state.continuation = continuation
                state.progress = progress
            }
            session.uploadTask(with: request, fromFile: fileURL).resume()
        }
    }
}

// MARK: - URLSessionTaskDelegate

extension BackgroundVideoUploader: URLSessionTaskDelegate, URLSessionDataDelegate {

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didSendBodyData bytesSent: Int64,
        totalBytesSent: Int64,
        totalBytesExpectedToSend: Int64
    ) {
        guard totalBytesExpectedToSend > 0 else { return }
        let fraction = Double(totalBytesSent) / Double(totalBytesExpectedToSend)
        let report = state.lock.withLock { state.progress }
        report?(fraction)
    }

    public func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        let continuation = state.lock.withLock {
            let held = state.continuation
            state.continuation = nil
            state.progress = nil
            return held
        }
        guard let continuation else { return }

        if let error {
            continuation.resume(throwing: error)
            return
        }
        guard let response = task.response as? HTTPURLResponse else {
            continuation.resume(throwing: VideoUploadError.noResponse)
            return
        }
        guard (200..<300).contains(response.statusCode) else {
            // S3 answers a rejected presigned PUT with a 4xx and an XML body. Surfacing the code
            // is what lets the caller tell "expired URL" from "wrong content type".
            continuation.resume(throwing: VideoUploadError.rejected(statusCode: response.statusCode))
            return
        }
        continuation.resume()
    }
}
