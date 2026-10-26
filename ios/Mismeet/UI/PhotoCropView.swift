import SwiftUI

/// Drag and pinch a picture under a circle; the square around the circle becomes the photo,
/// which the app always draws as a circle.
struct PhotoCropView: View {
    let image: UIImage
    let onChoose: (UIImage) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var area = CGSize.zero
    @State private var zoom: CGFloat = 1
    @State private var offset = CGSize.zero
    @GestureState private var gestureZoom: CGFloat = 1
    @GestureState private var gestureOffset = CGSize.zero

    private static let maxZoom: CGFloat = 5

    private var side: CGFloat { max(1, min(area.width, area.height) - 48) }

    /// The display scale at zoom 1, where the picture's shorter side just fills the circle.
    private var cover: CGFloat { side / min(image.size.width, image.size.height) }

    var body: some View {
        let scale = cover * min(max(zoom * gestureZoom, 1), Self.maxZoom)
        let shown = clamp(CGSize(width: offset.width + gestureOffset.width, height: offset.height + gestureOffset.height), scale: scale)
        // The picture is an overlay, so its zoomed size never stretches the area the circle is measured from.
        Color.black
            .overlay {
                Image(uiImage: image)
                    .resizable()
                    .frame(width: image.size.width * scale, height: image.size.height * scale)
                    .offset(shown)
            }
            .overlay {
                Rectangle()
                    .fill(.black.opacity(0.6))
                    .overlay { Circle().frame(width: side, height: side).blendMode(.destinationOut) }
                    .compositingGroup()
            }
            .overlay {
                Circle()
                    .stroke(.white, lineWidth: 2)
                    .frame(width: side, height: side)
            }
            .clipped()
        .contentShape(Rectangle())
        .onGeometryChange(for: CGSize.self) { $0.size } action: { area = $0 }
        .gesture(
            MagnifyGesture()
                .updating($gestureZoom) { value, state, _ in state = value.magnification }
                .onEnded { value in
                    zoom = min(max(zoom * value.magnification, 1), Self.maxZoom)
                    offset = clamp(offset, scale: cover * zoom)
                }
                .simultaneously(with: DragGesture()
                    .updating($gestureOffset) { value, state, _ in state = value.translation }
                    .onEnded { value in
                        offset = clamp(
                            CGSize(width: offset.width + value.translation.width, height: offset.height + value.translation.height),
                            scale: cover * zoom
                        )
                    }
                )
        )
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: 12) {
                Button { dismiss() } label: {
                    Text("Cancel").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                Button {
                    if let square = crop() { onChoose(square) }
                    dismiss()
                } label: {
                    Text("Choose").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(area == .zero)
            }
            .controlSize(.large)
            .padding()
        }
        .background(.black)
        .preferredColorScheme(.dark)
    }

    /// Keeps the picture covering the whole circle.
    private func clamp(_ offset: CGSize, scale: CGFloat) -> CGSize {
        let maxX = max(0, (image.size.width * scale - side) / 2)
        let maxY = max(0, (image.size.height * scale - side) / 2)
        return CGSize(width: min(max(offset.width, -maxX), maxX), height: min(max(offset.height, -maxY), maxY))
    }

    private func crop() -> UIImage? {
        let scale = cover * zoom
        let cropSide = min(side / scale, image.size.width, image.size.height).rounded()
        let x = min(max(image.size.width / 2 - offset.width / scale - cropSide / 2, 0), image.size.width - cropSide)
        let y = min(max(image.size.height / 2 - offset.height / scale - cropSide / 2, 0), image.size.height - cropSide)
        guard let cropped = image.cgImage?.cropping(to: CGRect(x: x.rounded(), y: y.rounded(), width: cropSide, height: cropSide)) else {
            return nil
        }
        return UIImage(cgImage: cropped)
    }
}
