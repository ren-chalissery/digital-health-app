import Factory
import Foundation

public extension Container {

    var sessionService: Factory<SessionService> {
        self { SessionServiceImpl() }.scope(.singleton)
    }

    var learningService: Factory<LearningService> {
        self { LearningServiceImpl() }.scope(.singleton)
    }
}
