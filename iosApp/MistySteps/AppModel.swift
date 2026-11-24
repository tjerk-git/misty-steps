import Foundation
import Shared

/// A full-screen page shown on top of the map.
enum Route: String {
    case onboarding, picker, stats, about, you
}

/// Which page is open, and the first-run flow (onboarding, then the question about counting opens, then the province
/// picker). The stage rules are the shared `FirstRun` ones; the saved flags use the same names as the Android app.
final class AppModel: ObservableObject {
    @Published var route: Route?

    private let defaults = UserDefaults.standard

    /// The Santa Rat card (see the shared `RatAnnouncement`): due once the first-run flow has been decided and no page is open.
    @Published private var started = false
    @Published private var ratSeen: Bool

    init() {
        ratSeen = UserDefaults.standard.bool(forKey: RatAnnouncement.shared.SEEN_KEY)
    }

    var ratCardDue: Bool {
        RatAnnouncement.shared.due(stage: started && route == nil ? .done : .onboarding, seen: ratSeen)
    }

    /// The card was answered: "Try the rat" ([tryIt]) also makes it your character. Answers once, however fast the taps come.
    func finishRatCard(tryIt: Bool) {
        if ratSeen { return }
        if tryIt, let rat = Characters.shared.all.first(where: { $0.id == RatAnnouncement.shared.CHARACTER_ID }) {
            Characters.shared.select(c: rat)
        }
        defaults.set(true, forKey: RatAnnouncement.shared.SEEN_KEY)
        ratSeen = true
    }
    private let onboardingDone = "onboarding_done"
    private let pickerSeen = "picker_seen"

    /// True once the picker exists on this platform; until then its stage counts as seen.
    var pickerAvailable = false

    /// The first step still due, or nil when the first-run flow is finished. [hasTrailPoints]: an existing user skips the onboarding.
    private func dueStage(hasTrailPoints: Bool) -> Route? {
        let stage = FirstRun.shared.stage(
            onboardingDone: defaults.bool(forKey: onboardingDone),
            pickerSeen: defaults.bool(forKey: pickerSeen),
            hasTrailPoints: hasTrailPoints)
        switch stage {
        case .onboarding: return .onboarding
        case .picker: return pickerAvailable ? .picker : nil // no picker on this platform yet: nothing more to ask
        default: return nil
        }
    }

    /// At launch: show the first step that is due. A launch argument `-route <page>` opens that page instead (for testing).
    func start(hasTrailPoints: Bool) {
        started = true
        // Test hook: -ratcard 1 shows the Santa Rat card whatever the saved flag says.
        if defaults.integer(forKey: "ratcard") > 0 { route = nil; ratSeen = false; return }
        if let forced = defaults.string(forKey: "route"), let r = Route(rawValue: forced) { route = r; return }
        route = dueStage(hasTrailPoints: hasTrailPoints)
    }

    /// A first-run page was finished: remember it and show the next step, if any.
    func finishOnboarding(hasTrailPoints: Bool) {
        defaults.set(true, forKey: onboardingDone)
        route = dueStage(hasTrailPoints: hasTrailPoints)
    }

    func finishPicker(hasTrailPoints: Bool) {
        defaults.set(true, forKey: pickerSeen)
        defaults.set(true, forKey: onboardingDone)
        route = dueStage(hasTrailPoints: hasTrailPoints)
    }

    func close() { route = nil }
}
