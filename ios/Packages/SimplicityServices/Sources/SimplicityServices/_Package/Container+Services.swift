import Factory
import Foundation

public extension Container {

    var sessionService: Factory<SessionService> {
        self { SessionServiceImpl() }.scope(.singleton)
    }

    var learningService: Factory<LearningService> {
        self { LearningServiceImpl() }.scope(.singleton)
    }

    var reflectionService: Factory<ReflectionService> {
        self { ReflectionServiceImpl() }.scope(.singleton)
    }

    var assistantService: Factory<AssistantService> {
        self { AssistantServiceImpl() }.scope(.singleton)
    }

    var organisationService: Factory<OrganisationService> {
        self { OrganisationServiceImpl() }.scope(.singleton)
    }
}
