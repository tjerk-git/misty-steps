import SwiftUI
import Shared

/// The shared Compose usage-count switch (anonymous open count), kept in UserDefaults.
struct SharedHelloView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.usageCardViewController(versionName: appVersion)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose consent page, full screen.
struct ConsentHostView: UIViewControllerRepresentable {
    var onAnswer: (Bool) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.consentViewController(onAnswer: { allowed in onAnswer(allowed.boolValue) })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose first-run pages, full screen.
struct OnboardingHostView: UIViewControllerRepresentable {
    var onDone: () -> Void
    var onSkip: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.onboardingViewController(onDone: { onDone() }, onSkip: { onSkip() })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"

/// The shared Compose about page, full screen.
struct AboutHostView: UIViewControllerRepresentable {
    var onClose: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let device = UIDevice.current
        let details = "\n\n\n\u{2014}\nMisty Steps \(appVersion)\niOS \(device.systemVersion), \(device.model)\n"
        return MainViewControllerKt.aboutViewController(versionName: appVersion, feedbackDetails: details, onClose: { onClose() })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose character picker, full screen.
struct CharacterHostView: UIViewControllerRepresentable {
    var onClose: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.characterViewController(onClose: { onClose() })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
