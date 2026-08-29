import Foundation
import SimplicityApi

/// Wording shared by Learn and the Dashboard, kept identical to
/// `web/src/app/features/learn/status-labels.ts`.
///
/// "Updated since you finished" is deliberately plain: the clinician did finish it, and the reason
/// it is back is that the content changed substantively, not that anything they did was wrong.
public extension AssignedModuleResponse.Status {

    var label: String {
        switch self {
        case .notStarted: String(localized: "status_not_started", bundle: .module)
        case .inProgress: String(localized: "status_in_progress", bundle: .module)
        case .completed: String(localized: "status_completed", bundle: .module)
        case .needsRedoing: String(localized: "status_needs_redoing", bundle: .module)
        }
    }
}

public extension LearnerModuleResponse.Status {

    var label: String {
        switch self {
        case .notStarted: String(localized: "status_not_started", bundle: .module)
        case .inProgress: String(localized: "status_in_progress", bundle: .module)
        case .completed: String(localized: "status_completed", bundle: .module)
        case .needsRedoing: String(localized: "status_needs_redoing", bundle: .module)
        }
    }
}
