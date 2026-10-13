import BackgroundTasks
import Shared

/// iOS twin of Android's AlertWorker scheduling. Android's WorkManager runs the
/// check every N minutes; iOS only takes a hint — "not before N minutes" — and
/// runs the refresh when it judges best, which is why the Alerts screen says
/// alerts are best effort on iPhone (plan decision 2). The check itself is the
/// shared `AlertCheck`, run through `IosApp.runAlertCheck`.
enum AlertRefresh {
    static let identifier = "io.raylytics.justmyweather.alerts"
    private static var interval: TimeInterval = 60 * 60
    private static var enabled = false

    static func register(app: IosApp) {
        // A queue of its own: the handler runs on it, and so does schedule(),
        // which the shared code calls from a background thread.
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: queue) { task in
            guard let task = task as? BGAppRefreshTask else { return }
            // The next one first, so a slow or killed run never ends the chain.
            submit()
            // Expiry and the check can both finish the task; only the first may.
            let finish = Once { success in task.setTaskCompleted(success: success) }
            task.expirationHandler = { finish(false) }
            app.runAlertCheck { done in finish(done.boolValue) }
        }
    }

    static func schedule(enabled: Bool, everyMinutes: Int) {
        queue.async {
            self.enabled = enabled
            interval = TimeInterval(max(everyMinutes, 15) * 60)
            if enabled {
                submit()
            } else {
                BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
            }
        }
    }

    private static let queue = DispatchQueue(label: "io.raylytics.justmyweather.alert-refresh")

    private static func submit() {
        guard enabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }
}

/// Runs its action the first time it is called, and never again.
private final class Once {
    private let lock = NSLock()
    private var action: ((Bool) -> Void)?

    init(_ action: @escaping (Bool) -> Void) { self.action = action }

    func callAsFunction(_ value: Bool) {
        lock.lock()
        let pending = action
        action = nil
        lock.unlock()
        pending?(value)
    }
}
