import Foundation

enum JSONNumber {
    /// Fixed notation, at most `maxDecimals` decimals, trailing zeros removed, never an exponent.
    static func format(_ value: Double, maxDecimals: Int) -> String {
        var text = String(format: "%.\(maxDecimals)f", value)
        if text.contains(".") {
            while text.hasSuffix("0") { text.removeLast() }
            if text.hasSuffix(".") { text.removeLast() }
        }
        return text == "-0" ? "0" : text
    }
}
