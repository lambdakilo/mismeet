import MapKit
import SwiftUI

/// Opens framed around everyone with a known position, the user included, like Find My.
struct ContactsMapView: View {
    private struct Estimate {
        var path: [CLLocationCoordinate2D]
        var point: CLLocationCoordinate2D
    }

    @Environment(AppModel.self) private var model
    @State private var position = MapCameraPosition.automatic
    @State private var estimates: [String: Estimate] = [:]

    private var located: [Contact] {
        model.state.contacts.filter { $0.lastPayload != nil }
    }

    var body: some View {
        Map(position: $position) {
            UserAnnotation()
            ForEach(located) { contact in
                if let estimate = estimates[contact.id] {
                    MapPolyline(coordinates: estimate.path)
                        .stroke(Color.purple.opacity(0.8), style: StrokeStyle(lineWidth: 4, lineCap: .round, dash: [8, 6]))
                    Annotation("\(contact.name), estimated", coordinate: estimate.point) {
                        ContactAvatar(contact: contact, size: 36).opacity(0.45)
                    }
                }
                if let payload = contact.lastPayload {
                    Annotation(contact.name, coordinate: CLLocationCoordinate2D(latitude: payload.latitude, longitude: payload.longitude)) {
                        ContactAvatar(contact: contact)
                            .saturation(contact.sharing == .notSharing ? 0 : 1)
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
        .task(id: located.map { "\($0.id):\($0.lastEventId ?? "")" }) {
            while !Task.isCancelled {
                estimates = estimates.filter { entry in located.contains { $0.id == entry.key } }
                for contact in located {
                    guard let payload = contact.lastPayload else { continue }
                    estimates[contact.id] = await model.roads.estimate(payload, now: Date()).map { estimate in
                        Estimate(
                            path: estimate.path.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) },
                            point: CLLocationCoordinate2D(latitude: estimate.point.latitude, longitude: estimate.point.longitude)
                        )
                    }
                }
                try? await Task.sleep(for: .seconds(10))
            }
        }
    }
}
