import MapKit
import SwiftUI

/// Opens framed around everyone with a known position, the user included, like Find My.
struct ContactsMapView: View {
    @Environment(AppModel.self) private var model
    @State private var position = MapCameraPosition.automatic

    private var located: [Contact] {
        model.state.contacts.filter { $0.lastPayload != nil }
    }

    var body: some View {
        Map(position: $position) {
            UserAnnotation()
            ForEach(located) { contact in
                if let payload = contact.lastPayload {
                    Annotation(contact.name, coordinate: CLLocationCoordinate2D(latitude: payload.latitude, longitude: payload.longitude)) {
                        Image(systemName: "person.circle.fill")
                            .font(.title)
                            .foregroundStyle(contact.sharing == .sharing ? Color.blue : Color.gray)
                    }
                }
            }
        }
        .mapControls {
            MapUserLocationButton()
        }
        .overlay(alignment: .bottomTrailing) {
            Button {
                position = .automatic
            } label: {
                Label("Everyone", systemImage: "person.2.fill")
            }
            .buttonStyle(.borderedProminent)
            .padding()
        }
        .onChange(of: located.map(\.id)) { _, _ in
            position = .automatic
        }
    }
}
