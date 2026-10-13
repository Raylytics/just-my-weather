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
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else { return }
            // The next one first, so a slow or killed run never ends the chain.
            submit()
            task.expirationHandler = { task.setTaskCompleted(success: false) }
            app.runAlertCheck { done in task.setTaskCompleted(success: done.boolValue) }
        }
    }

    static func schedule(enabled: Bool, everyMinutes: Int) {
        self.enabled = enabled
        interval = TimeInterval(max(everyMinutes, 15) * 60)
        if enabled {
            submit()
        } else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
        }
    }

    private static func submit() {
        guard enabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }
}
