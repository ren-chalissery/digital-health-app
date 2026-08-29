import Foundation

/// Build-time configuration, read from the bundle.
///
/// A missing value is a build configuration error rather than a runtime condition, so it fails at
/// launch with the reason rather than becoming a confusing network failure later.
enum AppConfiguration {

    private enum Constants {
        static let apiBaseURL = "APIBaseURL"
    }

    static var apiBaseURL: URL {
        guard
            let raw = Bundle.main.object(forInfoDictionaryKey: Constants.apiBaseURL) as? String,
            let url = URL(string: raw)
        else {
            fatalError("APIBaseURL is missing from Info.plist. Check Config-Shared.xcconfig.")
        }
        return url
    }
}
