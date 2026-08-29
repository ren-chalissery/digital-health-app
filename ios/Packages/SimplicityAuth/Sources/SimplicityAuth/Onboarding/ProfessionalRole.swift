import Foundation

/// Free text on the server; a fixed list in the client so the same role is not recorded five ways.
/// "Other" exists because a list of clinical roles is never complete.
///
/// Kept identical to `web/src/app/features/onboarding/professional-roles.ts`. The two lists
/// diverging would split the same person's role across clients.
public enum ProfessionalRole {

    public static let all = [
        "Psychiatrist",
        "Clinical psychologist",
        "Mental health nurse",
        "Occupational therapist",
        "Social worker",
        "Peer support worker",
        "Care coordinator",
        "Support worker",
        "Researcher",
        "Service manager",
        "Other"
    ]
}
