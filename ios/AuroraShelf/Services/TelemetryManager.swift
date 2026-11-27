import Combine
import Foundation
import Security
import UIKit

public struct TelemetryEvent: Codable, Equatable, Sendable {
    public let eventId: String
    public let type: String
    public let sessionId: String
    public let section: String
    public let subSection: String
    public let durationSec: Int
    public let ts: Double

    enum CodingKeys: String, CodingKey {
        case eventId = "event_id"
        case type
        case sessionId = "session_id"
        case section
        case subSection = "sub_section"
        case durationSec = "duration_sec"
        case ts
    }

    public init(
        eventId: String,
        type: String,
        sessionId: String,
        section: String,
        subSection: String,
        durationSec: Int,
        ts: Double
    ) {
        self.eventId = eventId
        self.type = type
        self.sessionId = sessionId
        self.section = section
        self.subSection = subSection
        self.durationSec = durationSec
        self.ts = ts
    }
}

public final class TelemetrySessionTracker: @unchecked Sendable {
    public static let maxQueueSize = 200
    public static let maxBatchSize = 32
    public static let maxEventDwellSec = 3600
    public static let allowedSections: Set<String> = [
        "video", "comics", "live", "forum", "game", "search", "library"
    ]

    private let lock = NSLock()
    private let monoClockSec: () -> Double
    private let wallClockSec: () -> Double
    private let idFactory: () -> String
    private let maxQueueSize: Int

    private var pendingQueue: [TelemetryEvent] = []
    private var activeSessionId: String?
    private var currentSection: String = "video"
    private var currentSubSection: String = "video_browse"
    private var lastTickMono: Double = 0

    public init(
        monoClockSec: @escaping () -> Double = { ProcessInfo.processInfo.systemUptime },
        wallClockSec: @escaping () -> Double = { Date().timeIntervalSince1970 },
        idFactory: @escaping () -> String = { UUID().uuidString.lowercased() },
        maxQueueSize: Int = TelemetrySessionTracker.maxQueueSize
    ) {
        self.monoClockSec = monoClockSec
        self.wallClockSec = wallClockSec
        self.idFactory = idFactory
        self.maxQueueSize = max(1, maxQueueSize)
    }

    public func loadPersistedEvents(_ events: [TelemetryEvent]) {
        lock.lock()
        defer { lock.unlock() }
        for ev in events {
            enqueueLocked(ev)
        }
    }

    @discardableResult
    public func onForeground() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if activeSessionId != nil {
            return false
        }
        let nowMono = monoClockSec()
        let nowWall = wallClockSec()
        let sid = "sess-\(idFactory())"
        activeSessionId = sid
        lastTickMono = nowMono
        enqueueLocked(
            TelemetryEvent(
                eventId: "ev-\(idFactory())",
                type: "session_start",
                sessionId: sid,
                section: currentSection,
                subSection: currentSubSection,
                durationSec: 0,
                ts: nowWall
            )
        )
        return true
    }

    public func setSection(_ section: String, subSection: String? = nil) {
        let cleanSection = section.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let rawSub = (subSection ?? cleanSection).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanSub = rawSub.isEmpty ? cleanSection : rawSub
        guard Self.allowedSections.contains(cleanSection) else { return }

        lock.lock()
        defer { lock.unlock() }
        if cleanSection == currentSection && cleanSub == currentSubSection {
            return
        }
        if let sid = activeSessionId {
            let nowMono = monoClockSec()
            let nowWall = wallClockSec()
            let elapsed = min(Self.maxEventDwellSec, max(0, Int(nowMono - lastTickMono)))
            lastTickMono = nowMono
            enqueueLocked(
                TelemetryEvent(
                    eventId: "ev-\(idFactory())",
                    type: "section_dwell",
                    sessionId: sid,
                    section: currentSection,
                    subSection: currentSubSection,
                    durationSec: elapsed,
                    ts: nowWall
                )
            )
        }
        currentSection = cleanSection
        currentSubSection = cleanSub
    }

    @discardableResult
    public func heartbeat() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard let sid = activeSessionId else { return false }
        let nowMono = monoClockSec()
        let nowWall = wallClockSec()
        let delta = min(Self.maxEventDwellSec, max(0, Int(nowMono - lastTickMono)))
        guard delta >= 1 else { return false }
        lastTickMono += Double(delta)
        enqueueLocked(
            TelemetryEvent(
                eventId: "ev-\(idFactory())",
                type: "heartbeat",
                sessionId: sid,
                section: currentSection,
                subSection: currentSubSection,
                durationSec: delta,
                ts: nowWall
            )
        )
        return true
    }

    public func onBackground() {
        lock.lock()
        defer { lock.unlock() }
        guard let sid = activeSessionId else { return }
        let nowMono = monoClockSec()
        let nowWall = wallClockSec()
        let remaining = min(Self.maxEventDwellSec, max(0, Int(nowMono - lastTickMono)))
        enqueueLocked(
            TelemetryEvent(
                eventId: "ev-\(idFactory())",
                type: "section_dwell",
                sessionId: sid,
                section: currentSection,
                subSection: currentSubSection,
                durationSec: remaining,
                ts: nowWall
            )
        )
        enqueueLocked(
            TelemetryEvent(
                eventId: "ev-\(idFactory())",
                type: "session_end",
                sessionId: sid,
                section: currentSection,
                subSection: currentSubSection,
                durationSec: 0,
                ts: nowWall
            )
        )
        activeSessionId = nil
        lastTickMono = nowMono
    }

    public func peekBatch(limit: Int = TelemetrySessionTracker.maxBatchSize) -> [TelemetryEvent] {
        lock.lock()
        defer { lock.unlock() }
        return Array(pendingQueue.prefix(max(1, limit)))
    }

    public func markSent(_ count: Int) {
        lock.lock()
        defer { lock.unlock() }
        let toRemove = min(max(0, count), pendingQueue.count)
        if toRemove > 0 {
            pendingQueue.removeFirst(toRemove)
        }
    }

    public func snapshotQueue() -> [TelemetryEvent] {
        lock.lock()
        defer { lock.unlock() }
        return pendingQueue
    }

    private func enqueueLocked(_ event: TelemetryEvent) {
        while pendingQueue.count >= maxQueueSize {
            pendingQueue.removeFirst()
        }
        pendingQueue.append(event)
    }
}

@MainActor
public final class TelemetryManager {
    public static let shared = TelemetryManager()

    private static let endpointURL = URL(string: "https://bananarey.us/api/telemetry/batch")!
    private static let installIDKey = "netgrey.telemetry.install_id.v1"
    private static let keychainService = "com.aurorashelf.app.telemetry"
    private static let keychainAccount = "install-id"

    private let tracker = TelemetrySessionTracker()
    private let session: URLSession
    private var installID: String = ""
    private var started = false
    private var isFlushing = false
    private var heartbeatTask: Task<Void, Never>?
    private var cancellables: Set<AnyCancellable> = []
    private var currentAccount: AccountProfile?

    private init() {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.timeoutIntervalForRequest = 5
        cfg.timeoutIntervalForResource = 8
        self.session = URLSession(configuration: cfg)
    }

    public func start() {
        guard !started else { return }
        started = true
        installID = Self.loadOrCreateInstallID()
        tracker.loadPersistedEvents(Self.loadPersistedQueue())

        AccountService.shared.$profile
            .receive(on: RunLoop.main)
            .sink { [weak self] profile in
                self?.currentAccount = profile
            }
            .store(in: &cancellables)

        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handleForeground()
            }
        }

        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.handleBackground()
            }
        }

        if UIApplication.shared.applicationState != .background {
            handleForeground()
        }
    }

    public func setSection(_ section: String, subSection: String? = nil) {
        tracker.setSection(section, subSection: subSection)
    }

    private func handleForeground() {
        if tracker.onForeground() {
            startHeartbeat()
            Task { await flush() }
        }
    }

    private func handleBackground() {
        heartbeatTask?.cancel()
        heartbeatTask = nil
        tracker.onBackground()
        Self.savePersistedQueue(tracker.snapshotQueue())

        var bgTaskID: UIBackgroundTaskIdentifier = .invalid
        bgTaskID = UIApplication.shared.beginBackgroundTask(withName: "NetgreyTelemetryFlush") {
            if bgTaskID != .invalid {
                UIApplication.shared.endBackgroundTask(bgTaskID)
                bgTaskID = .invalid
            }
        }
        Task {
            await flush()
            if bgTaskID != .invalid {
                UIApplication.shared.endBackgroundTask(bgTaskID)
                bgTaskID = .invalid
            }
        }
    }

    private func startHeartbeat() {
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                guard !Task.isCancelled, let self else { return }
                if self.tracker.heartbeat() {
                    Self.savePersistedQueue(self.tracker.snapshotQueue())
                    await self.flush()
                }
            }
        }
    }

    private func flush() async {
        guard !isFlushing, !installID.isEmpty else { return }
        isFlushing = true
        defer { isFlushing = false }

        let meta = Self.collectDeviceMetadata(
            installID: installID,
            account: currentAccount
        )

        while true {
            let batch = tracker.peekBatch(limit: TelemetrySessionTracker.maxBatchSize)
            if batch.isEmpty { break }
            guard let bodyData = Self.encodePayload(meta: meta, events: batch) else {
                tracker.markSent(batch.count)
                Self.savePersistedQueue(tracker.snapshotQueue())
                continue
            }

            var request = URLRequest(url: Self.endpointURL)
            request.httpMethod = "POST"
            request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            request.httpBody = bodyData

            do {
                let (_, response) = try await session.data(for: request)
                let status = (response as? HTTPURLResponse)?.statusCode ?? 0
                if (200..<300).contains(status) || status == 400 {
                    tracker.markSent(batch.count)
                    Self.savePersistedQueue(tracker.snapshotQueue())
                } else {
                    Self.savePersistedQueue(tracker.snapshotQueue())
                    break
                }
            } catch {
                Self.savePersistedQueue(tracker.snapshotQueue())
                break
            }
        }
    }

    public struct DeviceMetadata: Sendable {
        public let installID: String
        public let platform: String
        public let deviceForm: String
        public let deviceModel: String
        public let osVersion: String
        public let appVersion: String
        public let appBuild: Int
        public let installChannel: String
        public let locale: String
        public let timezone: String
        public let accountID: String
        public let accountEmail: String
        public let accountName: String
    }

    private struct BatchPayload: Encodable, Sendable {
        let installId: String
        let platform: String
        let deviceForm: String
        let deviceModel: String
        let osVersion: String
        let appVersion: String
        let appBuild: Int
        let installChannel: String
        let locale: String
        let timezone: String
        let accountId: String
        let accountEmail: String
        let accountName: String
        let events: [TelemetryEvent]

        enum CodingKeys: String, CodingKey {
            case installId = "install_id"
            case platform
            case deviceForm = "device_form"
            case deviceModel = "device_model"
            case osVersion = "os_version"
            case appVersion = "app_version"
            case appBuild = "app_build"
            case installChannel = "install_channel"
            case locale
            case timezone
            case accountId = "account_id"
            case accountEmail = "account_email"
            case accountName = "account_name"
            case events
        }
    }

    nonisolated public static func encodePayload(meta: DeviceMetadata, events: [TelemetryEvent]) -> Data? {
        let payload = BatchPayload(
            installId: meta.installID,
            platform: meta.platform,
            deviceForm: meta.deviceForm,
            deviceModel: meta.deviceModel,
            osVersion: meta.osVersion,
            appVersion: meta.appVersion,
            appBuild: meta.appBuild,
            installChannel: meta.installChannel,
            locale: meta.locale,
            timezone: meta.timezone,
            accountId: meta.accountID,
            accountEmail: meta.accountEmail,
            accountName: meta.accountName,
            events: events
        )
        return try? JSONEncoder().encode(payload)
    }

    private static func collectDeviceMetadata(installID: String, account: AccountProfile?) -> DeviceMetadata {
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let platform = isPad ? "ipados" : "ios"
        let deviceForm = isPad ? "tablet" : "phone"
        let info = Bundle.main.infoDictionary
        let appVersion = (info?["CFBundleShortVersionString"] as? String) ?? "0.0.0"
        let appBuild = Int((info?["CFBundleVersion"] as? String) ?? "0") ?? 0
        let osPrefix = isPad ? "iPadOS" : "iOS"

        return DeviceMetadata(
            installID: installID,
            platform: platform,
            deviceForm: deviceForm,
            deviceModel: hardwareMachineModel(),
            osVersion: "\(osPrefix) \(UIDevice.current.systemVersion)",
            appVersion: appVersion,
            appBuild: appBuild,
            installChannel: detectInstallChannel(),
            locale: Locale.current.identifier,
            timezone: TimeZone.current.identifier,
            accountID: account?.id ?? "",
            accountEmail: account?.email ?? "",
            accountName: account?.displayName ?? ""
        )
    }

    private static func hardwareMachineModel() -> String {
        var systemInfo = utsname()
        uname(&systemInfo)
        return withUnsafeBytes(of: &systemInfo.machine) { rawPtr in
            let bytes = rawPtr.prefix { $0 != 0 }
            let model = String(decoding: bytes, as: UTF8.self)
            return model.isEmpty ? UIDevice.current.model : model
        }
    }

    private static func detectInstallChannel() -> String {
        #if targetEnvironment(simulator)
        return "simulator"
        #elseif DEBUG
        return "debug"
        #else
        if Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt" {
            return "testflight"
        }
        return "ota"
        #endif
    }

    private static func queueFileURL() -> URL? {
        guard let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return nil
        }
        try? FileManager.default.createDirectory(at: appSupport, withIntermediateDirectories: true)
        return appSupport.appendingPathComponent("telemetry_queue.json")
    }

    private static func loadPersistedQueue() -> [TelemetryEvent] {
        guard let url = queueFileURL(),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([TelemetryEvent].self, from: data) else {
            return []
        }
        return decoded
    }

    private static func savePersistedQueue(_ events: [TelemetryEvent]) {
        guard let url = queueFileURL() else { return }
        if events.isEmpty {
            try? FileManager.default.removeItem(at: url)
            return
        }
        if let data = try? JSONEncoder().encode(events) {
            try? data.write(to: url, options: .atomic)
        }
    }

    private static func loadOrCreateInstallID() -> String {
        if let cached = UserDefaults.standard.string(forKey: installIDKey), !cached.isEmpty {
            return cached
        }
        if let fromKC = loadKeychainInstallID(), !fromKC.isEmpty {
            UserDefaults.standard.set(fromKC, forKey: installIDKey)
            return fromKC
        }
        let prefix = UIDevice.current.userInterfaceIdiom == .pad ? "ipad" : "ios"
        let generated = "\(prefix)-\(UUID().uuidString.lowercased())"
        UserDefaults.standard.set(generated, forKey: installIDKey)
        saveKeychainInstallID(generated)
        return generated
    }

    private static func loadKeychainInstallID() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func saveKeychainInstallID(_ id: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
        ]
        SecItemDelete(query as CFDictionary)
        let attrs = query.merging([
            kSecValueData as String: Data(id.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]) { _, new in new }
        SecItemAdd(attrs as CFDictionary, nil)
    }
}
