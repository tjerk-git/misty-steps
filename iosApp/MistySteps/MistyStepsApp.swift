import SwiftUI
import Shared

@main
struct MistyStepsApp: App {
    init() { IosSetupKt.setUpSharedIos() }

    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
