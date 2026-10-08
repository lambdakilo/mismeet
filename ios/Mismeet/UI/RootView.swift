import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            Tab("Contacts", systemImage: "person.2") {
                ContactsView()
            }
            Tab("Map", systemImage: "map") {
                ContactsMapView()
            }
            Tab("Me", systemImage: "person.crop.circle") {
                MeView()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.becameActive() }
        }
    }
}
