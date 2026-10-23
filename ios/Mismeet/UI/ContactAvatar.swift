import SwiftUI

/// A round picture of a contact: the photo the user chose, or the first letter of the name on a colour from the key.
struct ContactAvatar: View {
    @Environment(AppModel.self) private var model
    let contact: Contact
    var size: CGFloat = 44

    var body: some View {
        Group {
            if let photo = model.photo(for: contact) {
                Image(uiImage: photo).resizable().scaledToFill()
            } else {
                Circle()
                    .fill(Color(hue: hue, saturation: 0.5, brightness: 0.75))
                    .overlay {
                        Text(initial)
                            .font(.system(size: size * 0.42, weight: .bold))
                            .foregroundStyle(.white)
                    }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay { Circle().stroke(.white, lineWidth: size * 0.07) }
        .shadow(color: .black.opacity(0.25), radius: 1)
        .accessibilityLabel(contact.name)
    }

    private var initial: String {
        contact.name.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "?"
    }

    private var hue: Double {
        Double((Int(contact.id.prefix(6), radix: 16) ?? 0) % 360) / 360
    }
}
