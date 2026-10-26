import SwiftUI

struct MeView: View {
    @Environment(AppModel.self) private var model
    @State private var newRelay = ""

    var body: some View {
        NavigationStack {
            List {
                Section("Invite") {
                    if let image = QRCode.image(for: model.inviteLink) {
                        Image(uiImage: image)
                            .resizable()
                            .interpolation(.none)
                            .scaledToFit()
                            .frame(maxWidth: 240)
                            .frame(maxWidth: .infinity)
                    }
                    Text(model.npub)
                        .font(.footnote.monospaced())
                        .textSelection(.enabled)
                    Button("Copy invite") {
                        UIPasteboard.general.string = model.inviteLink
                    }
                }
                Section("Sharing from this phone") {
                    Toggle("Share my location", isOn: Binding(
                        get: { model.state.sharingEnabled },
                        set: { model.setSharingEnabled($0) }
                    ))
                    if model.state.sharingEnabled {
                        LabeledContent("Permission", value: model.authorizationText)
                        if model.authorization != .authorizedAlways {
                            Button("Allow location") { model.requestLocationAuthorization() }
                        }
                        Button("Publish now") { model.publishNow() }
                            .disabled(model.isPublishing)
                        if let at = model.state.lastPublishedAt {
                            LabeledContent("Last published", value: at.formatted(date: .abbreviated, time: .shortened))
                        }
                    } else {
                        Text("Off: this phone only watches its contacts, publishes nothing and needs no location permission.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                Section("Relays") {
                    ForEach(model.state.relays, id: \.self) { relay in
                        Text(relay).font(.footnote.monospaced())
                    }
                    .onDelete { offsets in model.removeRelays(at: offsets) }
                    HStack {
                        TextField("wss://", text: $newRelay)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Button("Add") {
                            model.addRelay(newRelay)
                            newRelay = ""
                        }
                        .disabled(newRelay.isEmpty)
                    }
                }
                if model.showsLog {
                    Section("Log") {
                        ForEach(model.log.reversed()) { line in
                            Text("\(line.time.formatted(date: .omitted, time: .standard))  \(line.text)")
                                .font(.caption)
                        }
                    }
                }
            }
            .navigationTitle("Me")
        }
    }
}
