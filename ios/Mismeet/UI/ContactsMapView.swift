import MapKit
import SwiftUI

/// Opens framed around the people in this phone's country, the user included, like Find My. The
/// button at the bottom returns there, and a second tap widens to everyone, abroad included.
struct ContactsMapView: View {
    private struct Estimate {
        var path: [CLLocationCoordinate2D]
        var point: CLLocationCoordinate2D
    }

    private enum Framing {
        case nearby, everyone
    }

    @Environment(AppModel.self) private var model
    @Namespace private var mapScope
    @State private var position = MapCameraPosition.automatic
    @State private var framing = Framing.nearby
    @State private var estimates: [String: Estimate] = [:]
    @State private var countries: Countries?

    private var located: [Contact] {
        model.state.contacts.filter { $0.lastPayload != nil }
    }

    private var own: LatLon? {
        model.state.lastFix.map { LatLon(latitude: $0.latitude, longitude: $0.longitude) }
    }

    private var home: String? {
        countries?.home(own: own, contacts: located.compactMap(\.lastPayload).map(Self.latLon))
    }

    private var someoneAbroad: Bool {
        points(.nearby).count != points(.everyone).count
    }

    private var frameKey: String {
        located.map(\.id).joined(separator: ",") + ":" + estimates.keys.sorted().joined(separator: ",") + ":\(own != nil):\(home ?? "")"
    }

    var body: some View {
        let widen = someoneAbroad && framing == .nearby && !position.positionedByUser
        Map(position: $position, scope: mapScope) {
            UserAnnotation()
            ForEach(located) { contact in
                if let estimate = estimates[contact.id] {
                    MapPolyline(coordinates: estimate.path)
                        .stroke(Color.purple.opacity(0.8), style: StrokeStyle(lineWidth: 4, lineCap: .round, dash: [8, 6]))
                    Annotation("\(contact.name), estimated", coordinate: estimate.point) {
                        ContactAvatar(contact: contact, size: 36).opacity(0.45).environment(model)
                    }
                }
                if let payload = contact.lastPayload {
                    Annotation(contact.name, coordinate: CLLocationCoordinate2D(latitude: payload.latitude, longitude: payload.longitude)) {
                        ContactAvatar(contact: contact)
                            .saturation(contact.sharing == .notSharing ? 0 : 1)
                            .environment(model)
                    }
                }
            }
        }
        .mapControls {}
        .overlay(alignment: .bottomTrailing) {
            VStack(alignment: .trailing, spacing: 12) {
                MapCompass(scope: mapScope)
                MapUserLocationButton(scope: mapScope)
                Button {
                    show(widen ? .everyone : .nearby)
                } label: {
                    Label(
                        someoneAbroad && !widen ? countryName : "Everyone",
                        systemImage: widen || !someoneAbroad ? "person.2.fill" : "house.fill"
                    )
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
        .mapScope(mapScope)
        .task {
            countries = await Task.detached { Countries.bundled() }.value
        }
        .onChange(of: frameKey, initial: true) { _, _ in
            show(.nearby)
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

    private var countryName: String {
        home.flatMap { Locale.current.localizedString(forRegionCode: $0) } ?? "Home"
    }

    private static func latLon(_ payload: LocationPayload) -> LatLon {
        LatLon(latitude: payload.latitude, longitude: payload.longitude)
    }

    /// The contacts and this phone in the framing, each contact with its estimated position too.
    private func points(_ framing: Framing) -> [CLLocationCoordinate2D] {
        let home = home
        let keep = { (point: LatLon) in framing == .everyone || home == nil || countries?.code(point) == home }
        var points: [CLLocationCoordinate2D] = []
        for contact in located {
            guard let payload = contact.lastPayload, keep(Self.latLon(payload)) else { continue }
            points.append(CLLocationCoordinate2D(latitude: payload.latitude, longitude: payload.longitude))
            if let estimate = estimates[contact.id] { points.append(estimate.point) }
        }
        if let own, keep(own) {
            points.append(CLLocationCoordinate2D(latitude: own.latitude, longitude: own.longitude))
        }
        return points.isEmpty && framing == .nearby ? self.points(.everyone) : points
    }

    private func show(_ target: Framing) {
        framing = target
        let points = points(target)
        guard let first = points.first else {
            position = .automatic
            return
        }
        let latitudes = points.map(\.latitude)
        let longitudes = points.map(\.longitude)
        let (south, north) = (latitudes.min() ?? first.latitude, latitudes.max() ?? first.latitude)
        let (west, east) = (longitudes.min() ?? first.longitude, longitudes.max() ?? first.longitude)
        position = .region(MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: (south + north) / 2, longitude: (west + east) / 2),
            span: MKCoordinateSpan(latitudeDelta: (north - south) * 1.4 + 0.01, longitudeDelta: (east - west) * 1.4 + 0.01)
        ))
    }
}
