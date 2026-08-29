import Factory
import Foundation

public extension Container {

    /// `fatalError` on purpose. The app must register an adapter before anything calls the API, and
    /// a silent default would turn that omission into a confusing 401 much later instead of a crash
    /// on the first line that needed it.
    var apiAdapter: Factory<ApiAdapter> {
        self { fatalError("No ApiAdapter registered. The app target must register one at launch.") }
    }
}
