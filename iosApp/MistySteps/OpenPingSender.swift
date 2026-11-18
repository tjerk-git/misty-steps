import Foundation
import Shared

/// The anonymous open count on iOS, with the shared OpenPing rules and the same behaviour as Android's Analytics: only when the
/// person opted in, only on a release build, at most one ping every 30 minutes, remembered before sending so a slow request
/// cannot cause a second one, 5 s timeout, no retry and no queue. Call once at app start. `url` is for tests only.
enum OpenPingSender {
    static func pingIfAllowed(release: Bool, url: String = OpenPing.shared.URL) {
        let defaults = UserDefaults.standard
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let last = Int64(defaults.double(forKey: "analytics_last_ping"))
        guard OpenPing.shared.shouldPing(optedIn: defaults.bool(forKey: "analytics_opt_in"), release: release, lastPingAt: last, now: now),
              let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
              let target = URL(string: url) else { return }
        // A version name the server would refuse: send nothing.
        guard let body = try? OpenPing.shared.payload(versionName: version, os: "ios") else { return }
        defaults.set(Double(now), forKey: "analytics_last_ping")
        var request = URLRequest(url: target, timeoutInterval: 5)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("com.potloodgum.mistysteps.ios/\(version)", forHTTPHeaderField: "User-Agent")
        request.httpBody = Data(body.utf8)
        URLSession.shared.dataTask(with: request) { _, _, _ in }.resume() // the answer is not used
    }
}
