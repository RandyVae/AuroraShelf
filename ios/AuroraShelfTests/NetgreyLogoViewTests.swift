import XCTest
import SwiftUI
@testable import AuroraShelf

@MainActor
final class NetgreyLogoViewTests: XCTestCase {
    func testNetgreyMarkViewInitialization() {
        let defaultMark = NetgreyMarkView()
        XCTAssertEqual(defaultMark.width, 18, "默认宽度应为 18pt")
        XCTAssertEqual(defaultMark.height, 26, "默认高度应为 26pt")

        let customMark = NetgreyMarkView(width: 22, height: 34)
        XCTAssertEqual(customMark.width, 22)
        XCTAssertEqual(customMark.height, 34)
    }

    func testNetgreyLogoViewInitialization() {
        let defaultLogo = NetgreyLogoView()
        XCTAssertEqual(defaultLogo.markWidth, 18, "默认标牌宽度应为 18pt")
        XCTAssertEqual(defaultLogo.markHeight, 26, "默认标牌高度应为 26pt")
        XCTAssertEqual(defaultLogo.fontSize, 21, "默认字号应为 21pt")

        let customLogo = NetgreyLogoView(markWidth: 22, markHeight: 34, fontSize: 24)
        XCTAssertEqual(customLogo.markWidth, 22)
        XCTAssertEqual(customLogo.markHeight, 34)
        XCTAssertEqual(customLogo.fontSize, 24)
    }

    func testNetflixNLogoTypealias() {
        let alias = NetflixNLogo()
        XCTAssertEqual(alias.width, 18)
        XCTAssertEqual(alias.height, 26)
    }

    #if canImport(UIKit)
    func testNetgreyTabIconRenderer() {
        let imageSelected = NetgreyTabIconRenderer.renderImage(width: 20, height: 26, scale: 3.0, isSelected: true)
        XCTAssertEqual(imageSelected.size.width, 20)
        XCTAssertEqual(imageSelected.size.height, 26)
        XCTAssertEqual(imageSelected.scale, 3.0)
        XCTAssertEqual(imageSelected.renderingMode, .alwaysOriginal)

        let imageUnselected = NetgreyTabIconRenderer.renderImage(width: 20, height: 26, scale: 3.0, isSelected: false)
        XCTAssertEqual(imageUnselected.size.width, 20)
        XCTAssertEqual(imageUnselected.size.height, 26)
    }
    #endif
}
