import UIKit

extension UIApplication {
    /// The view controller on top, to present system sheets and pickers from.
    static func topViewController() -> UIViewController? {
        var top = shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
