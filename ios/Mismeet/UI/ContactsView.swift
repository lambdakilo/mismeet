import SwiftUI

struct ContactsView: View {
    @Environment(AppModel.self) private var model
    @State private var showingAdd = false

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
                    Button("Add", systemImage: "plus") { showingAdd = true }
                }
            }
            .refreshable { await model.refresh() }
            .sheet(isPresented: $showingAdd) { AddContactView() }
            .task {
                while !Task.isCancelled {
                    await model.refresh()
                    try? await Task.sleep(for: .seconds(ProtocolConstants.foregroundFetchIntervalSeconds))
                }
            }
        }
    }
}

struct ContactRow: View {
    @Environment(AppModel.self) private var model
    let contact: Contact

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(contact.name).font(.headline)
                Text(status).font(.subheadline).foregroundStyle(.secondary)
                Text(contact.npubPrefix).font(.caption.monospaced()).foregroundStyle(.tertiary)
            }
            Spacer()
            Toggle("Share my location", isOn: Binding(
                get: { contact.share },
                set: { model.setShare(contact.id, $0) }
            ))
            .labelsHidden()
        }
    }

    private var status: String {
        if let payload = contact.lastPayload {
            let fixed = Date(timeIntervalSince1970: TimeInterval(payload.fixTime))
            let age = RelativeDateTimeFormatter().localizedString(for: fixed, relativeTo: Date())
            let stale = contact.sharing == .notSharing ? ", no longer sharing" : ""
            return "Seen \(age), within \(payload.accuracy) m\(stale)"
        }
        switch contact.sharing {
        case .notSharing: return "Not sharing with you"
        case .sharing, .unknown: return "No location yet"
        }
    }
}

struct AddContactView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var invite = ""
    @State private var name = ""
    @State private var error: String?
    @State private var scanning = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Invite") {
                    TextField("nostr:nprofile1…", text: $invite, axis: .vertical)
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
