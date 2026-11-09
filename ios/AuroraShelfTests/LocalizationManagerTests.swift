import XCTest
@testable import AuroraShelf

@MainActor
final class LocalizationManagerTests: XCTestCase {
    
    override func setUp() async throws {
        // Reset to system language before tests
        LocalizationManager.shared.setLanguage(.system)
    }
    
    func testAllRequestedLanguagesAreCovered() {
        // User requirements: 中文、英文、日語、韓語、泰語、德語、法語、西語、馬來語、印尼語、越南語
        let allLangs = AppLanguage.allCases
        XCTAssertTrue(allLangs.contains(.zhHant), "Must cover 繁體中文")
        XCTAssertTrue(allLangs.contains(.zhHans), "Must cover 简体中文")
        XCTAssertTrue(allLangs.contains(.en), "Must cover 英文")
        XCTAssertTrue(allLangs.contains(.ja), "Must cover 日語")
        XCTAssertTrue(allLangs.contains(.ko), "Must cover 韓語")
        XCTAssertTrue(allLangs.contains(.th), "Must cover 泰語")
        XCTAssertTrue(allLangs.contains(.de), "Must cover 德語")
        XCTAssertTrue(allLangs.contains(.fr), "Must cover 法語")
        XCTAssertTrue(allLangs.contains(.es), "Must cover 西語")
        XCTAssertTrue(allLangs.contains(.ms), "Must cover 馬來語")
        XCTAssertTrue(allLangs.contains(.id), "Must cover 印尼語")
        XCTAssertTrue(allLangs.contains(.vi), "Must cover 越南語")
        XCTAssertTrue(allLangs.contains(.system), "Must support 跟随系统")
        XCTAssertEqual(allLangs.count, 13, "Total 13 cases (system + 12 locales)")
    }
    
    func testLanguageDisplayNamesAreValid() {
        for lang in AppLanguage.allCases {
            XCTAssertFalse(lang.displayName.isEmpty, "Language \(lang.rawValue) must have non-empty display name")
            XCTAssertFalse(lang.localeIdentifier.isEmpty, "Language \(lang.rawValue) must have non-empty localeIdentifier")
        }
    }
    
    func testLocalizationResolvesCorrectlyAcrossAll12Locales() {
        let manager = LocalizationManager.shared
        
        let expectations: [(AppLanguage, String, String, String)] = [
            (.zhHant, "主頁", "播放", "語言"),
            (.zhHans, "主页", "播放", "语言"),
            (.en, "Home", "Play", "Language"),
            (.ja, "ホーム", "再生", "言語"),
            (.ko, "홈", "재생", "언어"),
            (.th, "หน้าแรก", "เล่น", "ภาษา"),
            (.de, "Startseite", "Abspielen", "Sprache"),
            (.fr, "Accueil", "Lecture", "Langue"),
            (.es, "Inicio", "Reproducir", "Idioma"),
            (.ms, "Utama", "Main", "Bahasa"),
            (.id, "Beranda", "Putar", "Bahasa"),
            (.vi, "Trang chủ", "Phát", "Ngôn ngữ")
        ]
        
        for (lang, expectedHome, expectedPlay, expectedLang) in expectations {
            manager.setLanguage(lang)
            
            let homeText = "tab.home".localized
            let playText = "home.play".localized
            let langText = "settings.language".localized
            
            XCTAssertEqual(homeText, expectedHome, "tab.home for \(lang.rawValue) mismatch")
            XCTAssertEqual(playText, expectedPlay, "home.play for \(lang.rawValue) mismatch")
            XCTAssertEqual(langText, expectedLang, "settings.language for \(lang.rawValue) mismatch")
        }
    }
    
    func testFormattedStringSubstitution() {
        let manager = LocalizationManager.shared
        manager.setLanguage(.en)
        let formattedEn = "home.connecting".localized("Huavod")
        XCTAssertEqual(formattedEn, "Connecting to Huavod...")
        
        manager.setLanguage(.ja)
        let formattedJa = "home.connecting".localized("Huavod")
        XCTAssertEqual(formattedJa, "Huavodに接続中...")
        
        manager.setLanguage(.zhHant)
        let formattedZhHant = "home.connecting".localized("Huavod")
        XCTAssertEqual(formattedZhHant, "正在連接 Huavod...")
    }
    
    func testFallbackToKeyWhenKeyMissing() {
        let manager = LocalizationManager.shared
        manager.setLanguage(.en)
        let nonExistent = "unknown.sample.key".localized
        XCTAssertEqual(nonExistent, "unknown.sample.key")
    }

    func testWatchlistCountLocalizationDoesNotDoublePrefix() {
        let manager = LocalizationManager.shared
        manager.setLanguage(.zhHans)
        XCTAssertEqual("watchlist.count".localized(3), "3 部")
        
        manager.setLanguage(.en)
        XCTAssertEqual("watchlist.count".localized(3), "3 items")
    }
}
