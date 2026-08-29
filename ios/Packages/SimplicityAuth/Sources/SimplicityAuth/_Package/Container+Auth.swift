import Factory
import Foundation

public extension Container {

    var authService: Factory<AuthService> {
        self { AmplifyAuthService() }.scope(.singleton)
    }
}
