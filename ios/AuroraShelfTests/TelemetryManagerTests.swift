import XCTest
@testable import AuroraShelf

final class TelemetryManagerTests: XCTestCase {
    func testSessionTrackerMeasuresDwellWithoutHeartbeatDuplication() {
        var mono: Double = 10.0
        var wall: Double = 1795790000.0
        var seq = 0
        let tracker = TelemetrySessionTracker(
            monoClockSec: { mono },
            wallClockSec: { wall },
            idFactory: {
                seq += 1
                return "ios-\(seq)"
            }
        )

        XCTAssertTrue(tracker.onForeground())
        XCTAssertFalse(tracker.onForeground())

        mono += 30.0
        wall += 30.0
        XCTAssertTrue(tracker.heartbeat())

        mono += 15.0
        wall += 15.0
        tracker.setSection("live", subSection: "live_watch")

        mono += 20.0
        wall += 20.0
        tracker.onBackground()

        let batch = tracker.peekBatch(limit: 32)
        XCTAssertEqual(batch.count, 5)
        XCTAssertEqual(batch[0].type, "session_start")
        XCTAssertEqual(batch[1].type, "heartbeat")
        XCTAssertEqual(batch[1].durationSec, 30)
        XCTAssertEqual(batch[2].type, "section_dwell")
        XCTAssertEqual(batch[2].section, "video")
        XCTAssertEqual(batch[2].durationSec, 15)
        XCTAssertEqual(batch[3].type, "section_dwell")
        XCTAssertEqual(batch[3].section, "live")
        XCTAssertEqual(batch[3].subSection, "live_watch")
        XCTAssertEqual(batch[3].durationSec, 20)
        XCTAssertEqual(batch[4].type, "session_end")

        let videoTotal = batch.filter { $0.section == "video" }.reduce(0) { $0 + $1.durationSec }
        let liveTotal = batch.filter { $0.section == "live" }.reduce(0) { $0 + $1.durationSec }
        XCTAssertEqual(videoTotal, 45)
        XCTAssertEqual(liveTotal, 20)
    }

    func testQueueCapAndPayloadEncoding() throws {
        var mono: Double = 0
        var wall: Double = 1795790000.0
        var seq = 0
        let tracker = TelemetrySessionTracker(
            monoClockSec: { mono },
            wallClockSec: { wall },
            idFactory: {
                seq += 1
                return "cap-\(seq)"
            },
            maxQueueSize: 4
        )
        tracker.onForeground()
        tracker.setSection("invalid_section", subSection: "bad")
        for _ in 0..<5 {
            mono += 10
            wall += 10
            tracker.heartbeat()
        }
        let queued = tracker.snapshotQueue()
        XCTAssertEqual(queued.count, 4)
        XCTAssertTrue(queued.allSatisfy { $0.section == "video" })

        let meta = TelemetryManager.DeviceMetadata(
            installID: "ipad-001",
            platform: "ipados",
            deviceForm: "tablet",
            deviceModel: "iPad16,6",
            osVersion: "iPadOS 27.2",
            appVersion: "2.19.2",
            appBuild: 277,
            installChannel: "testflight",
            locale: "zh_HK",
            timezone: "Asia/Hong_Kong",
            accountID: "u1",
            accountEmail: "randy@example.com",
            accountName: "Randy"
        )
        let data = try XCTUnwrap(TelemetryManager.encodePayload(meta: meta, events: queued))
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(json["install_id"] as? String, "ipad-001")
        XCTAssertEqual(json["platform"] as? String, "ipados")
        XCTAssertEqual(json["app_build"] as? Int, 277)
        XCTAssertEqual((json["events"] as? [[String: Any]])?.count, 4)
    }
}
