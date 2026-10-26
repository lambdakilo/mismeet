import PhotosUI
import SwiftUI

struct ContactsView: View {
    private struct AddRequest: Identifiable {
        let id = UUID()
        var invite = ""
    }

    @Environment(AppModel.self) private var model
    @State private var adding: AddRequest?

    var body: some View {
        NavigationStack {
            List {
                if model.state.contacts.isEmpty {
                    ContentUnavailableView(
                        "No contacts",
                        systemImage: "person.2",
                        description: Text("Add a contact from the invite they sent you.")
                    )
                }
                ForEach(model.state.contacts) { contact in
                    ContactRow(contact: contact)
                }
                .onDelete { offsets in
                    model.removeContacts(at: offsets)
                }
            }
            .navigationTitle("Contacts")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Add", systemImage: "plus") { adding = AddRequest() }
                }
            }
            .refreshable { await model.refresh() }
            .sheet(item: $adding) { AddContactView(invite: $0.invite) }
            .onChange(of: model.openedInvite, initial: true) { _, link in
                guard let link else { return }
                adding = AddRequest(invite: link)
                model.openedInvite = nil
            }
        }
    }
}

struct ContactRow: View {
    private struct PickedImage: Identifiable {
        let id = UUID()
        let image: UIImage
    }

    @Environment(AppModel.self) private var model
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var cropping: PickedImage?
    let contact: Contact

    var body: some View {
        HStack {
            PhotosPicker(selection: $pickedPhoto, matching: .images) {
                ContactAvatar(contact: contact)
            }
            .buttonStyle(.borderless)
            .contextMenu {
                if contact.photoSetAt != nil {
                    Button("Remove photo", systemImage: "trash") { model.removePhoto(contact.id) }
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(contact.name).font(.headline)
                Text(status).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            if model.state.sharingEnabled {
                Toggle("Share my location", isOn: Binding(
                    get: { contact.share },
                    set: { model.setShare(contact.id, $0) }
                ))
                .labelsHidden()
            }
        }
        .onChange(of: pickedPhoto) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = model.loadPhoto(data: data) {
                    cropping = PickedImage(image: image)
                }
                pickedPhoto = nil
            }
        }
        .fullScreenCover(item: $cropping) { picked in
            PhotoCropView(image: picked.image) { square in
                model.setPhoto(contact.id, square: square)
            }
        }
    }

    private var status: String {
        if let payload = contact.lastPayload {
            let fixed = Date(timeIntervalSince1970: TimeInterval(payload.fixTime))
            let age = RelativeDateTimeFormatter().localizedString(for: fixed, relativeTo: Date())
            let stale = contact.sharing == .notSharing ? ", no longer sharing" : ""
            let moving = ContactRow.movement(of: payload).map { ", \($0)" } ?? ""
            return "Seen \(age), within \(payload.accuracy) m\(moving)\(stale)"
        }
        switch contact.sharing {
        case .notSharing: return "Not sharing with you"
        case .sharing, .unknown: return "No location yet"
        }
    }

    /// How a contact was moving at the fix, such as "by bike at 18 km/h", or nil when it was not.
    static func movement(of payload: LocationPayload) -> String? {
        guard let speed = payload.speed, speed >= ProtocolConstants.estimateMinSpeed else { return nil }
        let mode = switch TravelMode(speed: speed) {
        case .foot: "on foot"
        case .bike: "by bike"
        case .car: "by car"
        }
        return "\(mode) at \(Int((speed * 3.6).rounded())) km/h"
    }
}

struct AddContactView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var invite: String
    @State private var name = ""
    @State private var error: String?
    @State private var scanning = false

    init(invite: String = "") {
        _invite = State(initialValue: invite)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Invite") {
                    TextField("Invite link", text: $invite, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .lineLimit(3...6)
                    Button("Paste") {
                        invite = UIPasteboard.general.string ?? ""
                    }
                    if QRScannerView.isSupported {
                        Button("Scan QR code") { scanning = true }
                    }
                }
                Section("Name") {
                    TextField("How you call them", text: $name)
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
            }
            .navigationTitle("Add contact")
            .sheet(isPresented: $scanning) {
                NavigationStack {
                    QRScannerView { text in
                        invite = text
                        scanning = false
                    }
                    .ignoresSafeArea()
                    .navigationTitle("Scan the invite")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Cancel") { scanning = false }
                        }
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        do {
                            try model.addContact(invite: invite, name: name)
                            dismiss()
                        } catch {
                            self.error = error.localizedDescription
                        }
                    }
                    .disabled(invite.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
