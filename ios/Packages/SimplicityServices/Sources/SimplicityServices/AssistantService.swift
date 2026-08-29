import Foundation
import Mockable
import SimplicityApi

/// Questions answered from an organisation's published training.
///
/// Single turn: no history, no follow-ups that depend on what was asked before. And no path from
/// here to a reflection — Phase 4 was explicit that the feature somebody will eventually ask for,
/// *help me reflect on this*, is precisely the one that breaks Phase 3's promise.
@Mockable
public protocol AssistantService: AnyObject, Sendable {
    func ask(orgId: UUID, question: String) async throws -> AnswerResponse
}
