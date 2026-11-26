import Foundation
import Shared
import UserNotifications

/// "Still recording your walk": a local notification 10 minutes after the last movement. Every accepted fix pushes it back,
/// so it only shows when you have stood still (fixes come every few metres); ending the walk removes it.
enum StillReminder {
    private static let id = "still-recording"
    private static let after: TimeInterval = 10 * 60
    private static var lastScheduled = Date.distantPast

    /// Asks for permission to show notifications (once; iOS remembers the answer), then starts the count.
    static func begin() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        schedule(force: true)
    }

    /// Called for every fix that counted as a step: restarts the count (at most every 30 s, to keep it cheap).
    static func moved() { schedule(force: false) }

    static func end() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [id])
        lastScheduled = .distantPast
    }

    private static func schedule(force: Bool) {
        guard force || Date().timeIntervalSince(lastScheduled) > 30 else { return }
        lastScheduled = Date()
        let content = UNMutableNotificationContent()
        content.title = IosTextsKt.stillTitleText()
        content.body = IosTextsKt.stillBodyText()
        content.sound = .default
        let request = UNNotificationRequest(identifier: id, content: content,
                                            trigger: UNTimeIntervalNotificationTrigger(timeInterval: after, repeats: false))
        UNUserNotificationCenter.current().add(request)
    }
}
