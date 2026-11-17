import SwiftUI
import Shared

/// Hosts a Compose Multiplatform screen from the shared module inside SwiftUI.
struct SharedHelloView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.sharedHelloViewController()
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
