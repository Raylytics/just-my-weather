import MessageUI
import OSLog
import Shared
import UIKit

/// iOS twin of Android's SupportIntents: facts about this install, the app's
/// own recent log, and handing a finished mail to Mail (or a mailto: link
/// when Mail isn't set up). Nothing leaves without the user pressing Send.
final class SupportService: NSObject, SupportHost, MFMailComposeViewControllerDelegate {
    private static let address = SupportMailKt.SUPPORT_ADDRESS

    func appInfo() -> AppInfo {
        let info = Bundle.main.infoDictionary
        return IosBridgesKt.iosAppInfo(
            version: info?["CFBundleShortVersionString"] as? String ?? "?",
            build: Int32(info?["CFBundleVersion"] as? String ?? "") ?? 0,
            osRelease: UIDevice.current.systemVersion,
            model: Self.model
        )
    }

    /// This process's last 50 log lines — Android reads its own logcat; iOS
    /// reads its own OSLogStore. Best effort: empty if the system won't say.
    func recentLogLines() -> [String] {
        guard let store = try? OSLogStore(scope: .currentProcessIdentifier),
              let entries = try? store.getEntries(at: store.position(date: Date().addingTimeInterval(-3600)))
        else { return [] }
        return entries.compactMap { $0 as? OSLogEntryLog }.suffix(50).map { "\($0.date) \($0.composedMessage)" }
    }

    func send(mail: SupportMail) -> Bool {
        guard let host = AppDelegate.platform.presenter() else { return false }
        if MFMailComposeViewController.canSendMail() {
            let composer = MFMailComposeViewController()
            composer.mailComposeDelegate = self
            composer.setToRecipients([Self.address])
            composer.setSubject(mail.subject)
            composer.setMessageBody(mail.body, isHTML: false)
            host.present(composer, animated: true)
            return true
        }
        var parts = URLComponents()
        parts.scheme = "mailto"
        parts.path = Self.address
        parts.queryItems = [URLQueryItem(name: "subject", value: mail.subject), URLQueryItem(name: "body", value: mail.body)]
        guard let url = parts.url, UIApplication.shared.canOpenURL(url) else { return false }
        UIApplication.shared.open(url)
        return true
    }

    func mailComposeController(
        _ controller: MFMailComposeViewController,
        didFinishWith result: MFMailComposeResult,
        error: Error?
    ) {
        controller.dismiss(animated: true)
    }

    /// "iPhone16,1" — the hardware model, as Android's Build.MODEL is.
    private static let model: String = {
        var system = utsname()
        uname(&system)
        return withUnsafeBytes(of: &system.machine) { bytes in
            String(decoding: bytes.prefix { $0 != 0 }, as: UTF8.self)
        }
    }()
}
