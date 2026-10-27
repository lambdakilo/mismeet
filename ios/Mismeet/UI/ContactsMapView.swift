import MapKit
import SwiftUI

/// Opens framed around the people in this phone's country, the user included, like Find My. The
/// button at the bottom returns there, and a second tap widens to everyone, abroad included. The
/// contact list is a panel at the bottom, and a contact tapped in it is focused, with its card in
/// the panel until it is closed.
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
    @Binding var focused: String?
    @Binding var listUp: Bool

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

    private var focusedContact: Contact? {
        located.first { $0.id == focused }
    }

    private var focusKey: String? {
        focusedContact?.lastPayload.map { "\(focused ?? ""):\($0.latitude),\($0.longitude),\($0.accuracy)" }
    }

    private var frameKey: String {
        located.map(\.id).joined(separator: ",") + ":" + estimates.keys.sorted().joined(separator: ",") + ":\(own != nil):\(home ?? "")"
    }

    var body: some View {
        let widen = focusedContact == nil && someoneAbroad && framing == .nearby && !position.positionedByUser
        Map(position: $position, scope: mapScope) {
            UserAnnotation()
            if let payload = focusedContact?.lastPayload {
                MapCircle(center: CLLocationCoordinate2D(latitude: payload.latitude, longitude: payload.longitude),
                          radius: CLLocationDistance(payload.accuracy))
                    .foregroundStyle(.blue.opacity(0.25))
                    .stroke(.white, lineWidth: 3)
            }
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
                    focused = nil
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
        .safeAreaInset(edge: .bottom, spacing: 0) {
            Group {
                if let contact = focusedContact {
                    HStack(alignment: .top, spacing: 12) {
                        ContactAvatar(contact: contact)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(contact.name).font(.headline)
                            Text(ContactRow.status(of: contact, place: model.placeNames[contact.id]))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if let distance = ContactRow.distance(to: contact, in: model.state) {
                            Text(distance).font(.subheadline).foregroundStyle(.secondary)
                        }
                        Button("Close", systemImage: "xmark") {
                            focused = nil
                            show(.nearby)
                        }
                        .labelStyle(.iconOnly)
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.circle)
                    }
                    .padding()
                } else {
                    ContactList(expanded: $listUp) { focused = $0 }
                }
            }
            .background {
                UnevenRoundedRectangle(topLeadingRadius: 20, topTrailingRadius: 20)
                    .fill(.regularMaterial)
                    .shadow(color: .black.opacity(0.15), radius: 8)
                    .ignoresSafeArea(edges: .bottom)
            }
        }
        .mapScope(mapScope)
        .onAppear {
            if model.state.contacts.isEmpty { listUp = true }
        }
        .task {
            countries = await Task.detached { Countries.bundled() }.value
        }
        .onChange(of: frameKey, initial: true) { _, _ in
            if focusedContact == nil { show(.nearby) }
        }
        .onChange(of: focusKey, initial: true) { _, _ in
            guard let payload = focusedContact?.lastPayload else { return }
            let span = max(Double(payload.accuracy) * 3, 800)
            position = .region(MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: payload.latitude, longitude: payload.longitude),
                latitudinalMeters: span, longitudinalMeters: span
            ))
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
