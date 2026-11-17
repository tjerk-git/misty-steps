import Foundation
import Shared

/// A full-screen page shown on top of the map.
enum Route: String {
    case onboarding, consent, picker, stats, about, characters
}

/// Which page is open, and the first-run flow (onboarding, then the question about counting opens, then the province
/// picker). The stage rules are the shared `FirstRun` ones; the saved flags use the same names as the Android app.
final class AppModel: ObservableObject {
    @Published var route: Route?

    private let defaults = UserDefaults.standard
    private let onboardingDone = "onboarding_done"
    private let pickerSeen = "picker_seen"
    private let consentAsked = "analytics_asked"
    private let optIn = "analytics_opt_in"

    /// True once the picker exists on this platform; until then its stage counts as seen.
    var pickerAvailable = false

    /// The first step still due, or nil when the first-run flow is finished. [hasTrailPoints]: an existing user skips the onboarding.
    private func dueStage(hasTrailPoints: Bool) -> Route? {
        let stage = FirstRun.shared.stage(
            onboardingDone: defaults.bool(forKey: onboardingDone),
            consentAsked: defaults.bool(forKey: consentAsked),
            pickerSeen: defaults.bool(forKey: pickerSeen),
            hasTrailPoints: hasTrailPoints)
        switch stage {
        case .onboarding: return .onboarding
        case .consent: return .consent
        case .picker: return pickerAvailable ? .picker : nil // no picker on this platform yet: nothing more to ask
        default: return nil
        }
    }

    /// At launch: show the first step that is due. A launch argument `-route <page>` opens that page instead (for testing).
    func start(hasTrailPoints: Bool) {
        if let forced = defaults.string(forKey: "route"), let r = Route(rawValue: forced) { route = r; return }
        route = dueStage(hasTrailPoints: hasTrailPoints)
    }

    /// A first-run page was finished: remember it and show the next step, if any.
    func finishOnboarding(hasTrailPoints: Bool) {
        defaults.set(true, forKey: onboardingDone)
        route = dueStage(hasTrailPoints: hasTrailPoints)
    }

    func answerConsent(_ allowed: Bool, hasTrailPoints: Bool) {
        defaults.set(allowed, forKey: optIn)
        defaults.set(true, forKey: consentAsked)
        route = dueStage(hasTrailPoints: hasTrailPoints)
    }

    func finishPicker(hasTrailPoints: Bool) {
        defaults.set(true, forKey: pickerSeen)
        defaults.set(true, forKey: onboardingDone)
        route = dueStage(hasTrailPoints: hasTrailPoints)
    }

    func close() { route = nil }
}
