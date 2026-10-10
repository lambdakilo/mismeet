import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @State private var screen = Screen.map

    enum Screen: Hashable {
        case map, contacts, me
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
