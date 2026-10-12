import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @State private var screen = Screen.initial

    enum Screen: Hashable {
        case map, contacts, me

        // Test hook: MISMEET_TEST_TAB=contacts or me in the launch environment of a debug build.
        static var initial: Screen {
            #if DEBUG
            switch ProcessInfo.processInfo.environment["MISMEET_TEST_TAB"] {
            case "contacts": return .contacts
            case "me": return .me
            default: return .map
            }
            #else
            return .map
            #endif
        }
    }

    var body: some View {
        TabView(selection: $screen) {
            Tab("Map", systemImage: "map", value: .map) {
                ContactsMapView()
            }
            Tab("Contacts", systemImage: "person.2", value: .contacts) {
                ContactsView()
            }
            Tab("Me", systemImage: "person.crop.circle", value: .me) {
                MeView()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.becameActive() }
        }
        .task {
            while !Task.isCancelled {
                await model.refresh()
                try? await Task.sleep(for: .seconds(ProtocolConstants.foregroundFetchIntervalSeconds))
            }
        }
    }
}
