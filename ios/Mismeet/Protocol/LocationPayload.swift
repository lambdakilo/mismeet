import Foundation

/// The plaintext of one entry, spec section 5.2.
struct LocationPayload: Equatable, Sendable {
    var fixTime: UInt64
    var latitude: Double
    var longitude: Double
    var accuracy: Int
    var altitude: Int?
    var speed: Double?
    var course: Int?
    var battery: Double?

    enum DecodeError: Error, Equatable {
        case notAnObject
        case unsupportedVersion
        case invalid(String)
    }

    func encode() -> String {
        var fields = [
            "\"v\":\(ProtocolConstants.payloadVersion)",
            "\"ts\":\(fixTime)",
            "\"lat\":\(JSONNumber.format(latitude, maxDecimals: ProtocolConstants.coordinateDecimals))",
            "\"lon\":\(JSONNumber.format(longitude, maxDecimals: ProtocolConstants.coordinateDecimals))",
            "\"acc\":\(accuracy)",
        ]
        if let altitude { fields.append("\"alt\":\(altitude)") }
        if let speed { fields.append("\"spd\":\(JSONNumber.format(speed, maxDecimals: 1))") }
        if let course { fields.append("\"hdg\":\(course)") }
        if let battery { fields.append("\"bat\":\(JSONNumber.format(battery, maxDecimals: 2))") }
        return "{" + fields.joined(separator: ",") + "}"
    }

    static func decode(_ json: String) throws -> LocationPayload {
        guard let data = json.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              let fields = object as? [String: Any] else {
            throw DecodeError.notAnObject
        }
        guard let version = integer(fields["v"]), version == ProtocolConstants.payloadVersion else {
            throw DecodeError.unsupportedVersion
        }
        guard let fixTime = integer(fields["ts"]), fixTime >= 0 else { throw DecodeError.invalid("ts") }
        guard let latitude = double(fields["lat"]), (-90...90).contains(latitude) else { throw DecodeError.invalid("lat") }
        guard let longitude = double(fields["lon"]), (-180...180).contains(longitude) else { throw DecodeError.invalid("lon") }
        guard let accuracy = integer(fields["acc"]), accuracy >= 0 else { throw DecodeError.invalid("acc") }
        let course = integer(fields["hdg"]).flatMap { (0...359).contains($0) ? $0 : nil }
        let battery = double(fields["bat"]).flatMap { (0...1).contains($0) ? $0 : nil }
        let speed = double(fields["spd"]).flatMap { $0 >= 0 ? $0 : nil }
        return LocationPayload(
            fixTime: UInt64(fixTime),
            latitude: latitude,
            longitude: longitude,
            accuracy: accuracy,
            altitude: integer(fields["alt"]),
            speed: speed,
            course: course,
            battery: battery
        )
    }

    // JSONSerialization hands back NSNumber for both numbers and booleans; only the type id tells them apart.
    private static func number(_ value: Any?) -> NSNumber? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        return number
    }

    private static func integer(_ value: Any?) -> Int? {
        guard let number = number(value) else { return nil }
        let double = number.doubleValue
        guard double == double.rounded(), abs(double) < 9_007_199_254_740_992 else { return nil }
        return number.intValue
    }

    private static func double(_ value: Any?) -> Double? {
        guard let number = number(value), number.doubleValue.isFinite else { return nil }
        return number.doubleValue
    }
}
