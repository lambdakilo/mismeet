import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate {
    // A launch for a significant location change happens in the background, before any view
    // exists. Touching the model starts location monitoring so the pending fix is delivered.
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        _ = AppModel.shared
        return true
    }
}
