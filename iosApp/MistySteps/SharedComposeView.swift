import CoreLocation
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

/// The shared Compose "You" page (your character and the settings), full screen.
struct YouHostView: UIViewControllerRepresentable {
    let backup: BackupHost
    let packs: PackManager
    let provinces: [PackEntry]?
    var onOpenPicker: () -> Void
    var onOpenAbout: () -> Void
    var onClose: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        StatsIosKt.youViewController(
            versionName: appVersion, backup: backup, packs: packs, provinces: provinces,
            onOpenPicker: { onOpenPicker() }, onOpenAbout: { onOpenAbout() }, onClose: { onClose() })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose stats page: the numbers counted from the trails on this device, full screen.
struct StatsHostView: UIViewControllerRepresentable {
    let store: SQLiteTrailStore
    let here: CLLocationCoordinate2D?
    let pins: PinBook
    var onOpenWalk: (Int64) -> Void
    var onClose: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        StatsIosKt.statsViewController(
            points: store, sessions: store,
            hereLat: here.map { KotlinDouble(value: $0.latitude) }, hereLon: here.map { KotlinDouble(value: $0.longitude) },
            pins: pins.pins, onOpenWalk: { onOpenWalk($0.int64Value) }, onClose: { onClose() })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose province picker, full screen.
struct PickerHostView: UIViewControllerRepresentable {
    let provinces: [PackEntry]?
    let packs: PackManager
    let preselected: Set<String>
    let existingUser: Bool
    var onDone: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.provincePickerViewController(
            provinces: provinces, packs: packs, preselected: preselected, existingUser: existingUser, onDone: { onDone() })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose pin sheets (leave a pin, look at one), on a transparent view over the map.
struct PinSheetHostView: UIViewControllerRepresentable {
    let book: PinBook
    let target: PinSheetTarget
    var onDone: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = PinSheetIosKt.pinSheetViewController(book: book, state: target.state, onDone: { onDone() })
        controller.view.backgroundColor = .clear
        return controller
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose postcard page, full screen; the picture is painted by [painter].
struct PostcardHostView: UIViewControllerRepresentable {
    let walkId: Int64
    let painter: PostcardPainterImpl
    var onClose: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        PostcardIosKt.postcardViewController(walkId: walkId, painter: painter, onClose: { onClose() })
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// The shared Compose Santa Rat card, on a transparent view over the map.
struct RatCardHostView: UIViewControllerRepresentable {
    var onTry: () -> Void
    var onLater: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = RatCardIosKt.ratCardViewController(onTry: { onTry() }, onLater: { onLater() })
        controller.view.backgroundColor = .clear
        return controller
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
