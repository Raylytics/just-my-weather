import Shared
import UserNotifications

/// iOS twin of Android's AlertNotifier, with the same words: a rule's title is
/// "<subject> alert" and its body the decision's reason; a safety alert is the
/// NWS event over its headline. Quiet hours post `.passive` (no sound, no
/// banner); safety alerts post `.timeSensitive` and ignore quiet hours.
final class Notifier: NSObject, AlertNotifications {
    func notify(rule: AlertRule, decision: FireDecision, silent: Bool) {
        let content = UNMutableNotificationContent()
        content.title = "\(rule.subject.label) alert"
        content.body = decision.reason
        if silent {
            if #available(iOS 15.0, *) { content.interruptionLevel = .passive }
        } else {
            content.sound = .default
        }
        // Stable id per rule, so a re-fire replaces rather than stacks.
        post(id: "rule-\(rule.id)", content)
    }

    func notifySafety(alert: ActiveAlert) {
        let content = UNMutableNotificationContent()
        content.title = alert.event
        // NWS sometimes sends no headline; the event always stands in.
        content.body = alert.headline.isEmpty ? alert.event : alert.headline
        content.sound = .default
        if #available(iOS 15.0, *) { content.interruptionLevel = .timeSensitive }
        post(id: "safety-\(alert.id)", content)
    }

    private func post(id: String, _ content: UNNotificationContent) {
        let request = UNNotificationRequest(identifier: id, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
