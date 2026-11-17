import SwiftUI
import Shared

/// Hosts a Compose Multiplatform screen from the shared module inside SwiftUI.
struct SharedHelloView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.sharedHelloViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
