import MapKit
import SwiftUI

struct ContactsMapView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Map {
            UserAnnotation()
            ForEach(model.state.contacts) { contact in
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
    }
}
