import AppKit
import CoreGraphics
import Foundation

// Starts only its own child, observes native activation events and WindowServer metadata.
// It never raises windows, changes focus, injects input or inspects other apps' content.
let args = CommandLine.arguments
guard args.count >= 5 else { fatalError("Usage: WatchFocus report.json timeout-seconds working-directory executable [args...]") }
let reportPath = args[1]
let timeout = Double(args[2])!
let child = Process()
child.executableURL = URL(fileURLWithPath: args[4])
child.arguments = Array(args.dropFirst(5))
child.currentDirectoryURL = URL(fileURLWithPath: args[3])
let logPath = reportPath + ".client.log"
FileManager.default.createFile(atPath: logPath, contents: nil)
let log = FileHandle(forWritingAtPath: logPath)!
child.standardOutput = log
child.standardError = log
child.standardInput = FileHandle.nullDevice
var activationEvents = 0
var foregroundSamples = 0
var onScreenSamples = 0
var ownedWindowsSeen = 0
var samples = 0
var windowQueriesAvailable = true
var timedOut = false
var ownedPid: pid_t = -1
let observer = NSWorkspace.shared.notificationCenter.addObserver(
    forName: NSWorkspace.didActivateApplicationNotification, object: nil, queue: .main
) { notification in
    if let app = notification.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication,
       app.processIdentifier == ownedPid {
        activationEvents += 1
        if child.isRunning { child.terminate() }
    }
}
try child.run()
ownedPid = child.processIdentifier
let started = Date()
while child.isRunning {
    RunLoop.current.run(until: Date().addingTimeInterval(0.01))
    samples += 1
    if NSWorkspace.shared.frontmostApplication?.processIdentifier == ownedPid {
        foregroundSamples += 1
        child.terminate()
    }
    if let windows = CGWindowListCopyWindowInfo(.optionAll, kCGNullWindowID) as? [[String: Any]] {
        let owned = windows.filter { ($0[kCGWindowOwnerPID as String] as? Int32) == ownedPid }
        ownedWindowsSeen = max(ownedWindowsSeen, owned.count)
        if owned.contains(where: { ($0[kCGWindowIsOnscreen as String] as? Bool) == true }) {
            onScreenSamples += 1
            child.terminate()
        }
    } else { windowQueriesAvailable = false }
    if Date().timeIntervalSince(started) > timeout {
        timedOut = true
        child.terminate()
        break
    }
}
if child.isRunning {
    let deadline = Date().addingTimeInterval(10)
    while child.isRunning && Date() < deadline { RunLoop.current.run(until: Date().addingTimeInterval(0.05)) }
    if child.isRunning { kill(ownedPid, SIGKILL) }
}
child.waitUntilExit()
NSWorkspace.shared.notificationCenter.removeObserver(observer)
try log.close()
let passed = child.terminationStatus == 0 && !timedOut && activationEvents == 0 && foregroundSamples == 0 && onScreenSamples == 0
let report: [String: Any] = ["event": "native_focus_observation", "status": passed ? "pass" : "fail",
    "pid": ownedPid, "exit": child.terminationStatus, "timedOut": timedOut, "samples": samples,
    "activationEvents": activationEvents, "foregroundSamples": foregroundSamples,
    "onScreenSamples": onScreenSamples, "ownedWindowsSeen": ownedWindowsSeen,
    "windowQueriesAvailable": windowQueriesAvailable, "log": logPath]
let data = try JSONSerialization.data(withJSONObject: report, options: [.prettyPrinted, .sortedKeys])
try data.write(to: URL(fileURLWithPath: reportPath))
let compact = try JSONSerialization.data(withJSONObject: report, options: [.sortedKeys])
print("QA_EVT " + String(data: compact, encoding: .utf8)!)
exit(passed ? 0 : 1)
