import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 8) {
            Text("Mismeet")
                .font(.largeTitle)
            Text("Protocol version \(ProtocolConstants.payloadVersion)")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}
