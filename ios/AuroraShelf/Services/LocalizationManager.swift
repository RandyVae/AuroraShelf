import Foundation
import SwiftUI

public enum AppLanguage: String, CaseIterable, Identifiable, Sendable {
    case system = "system"
    case zhHant = "zh-Hant"
    case zhHans = "zh-Hans"
    case en = "en"
    case ja = "ja"
    case ko = "ko"
    case th = "th"
    case de = "de"
    case fr = "fr"
    case es = "es"
    case ms = "ms"
    case id = "id"
    case vi = "vi"
    
    public var id: String { rawValue }
    
    public var displayName: String {
        switch self {
        case .system: return "settings.follow_system".localized
        case .zhHant: return "繁體中文（香港）"
        case .zhHans: return "简体中文"
        case .en: return "English"
        case .ja: return "日本語"
        case .ko: return "한국어"
        case .th: return "ไทย"
        case .de: return "Deutsch"
        case .fr: return "Français"
        case .es: return "Español"
        case .ms: return "Bahasa Melayu"
        case .id: return "Bahasa Indonesia"
        case .vi: return "Tiếng Việt"
        }
    }
    
    public var localeIdentifier: String {
        switch self {
        case .system:
            return AppLanguage.resolveSystemLanguageCode()
        case .zhHant: return "zh-Hant"
        case .zhHans: return "zh-Hans"
        case .en: return "en"
        case .ja: return "ja"
        case .ko: return "ko"
        case .th: return "th"
        case .de: return "de"
        case .fr: return "fr"
        case .es: return "es"
        case .ms: return "ms"
        case .id: return "id"
        case .vi: return "vi"
        }
    }
    
    public static func resolveSystemLanguageCode() -> String {
        let preferred = Locale.preferredLanguages
        for pref in preferred {
            let lower = pref.lowercased()
            if lower.hasPrefix("zh-hant") || lower.hasPrefix("zh-hk") || lower.hasPrefix("zh-tw") {
                return "zh-Hant"
            } else if lower.hasPrefix("zh") {
                return "zh-Hans"
            } else if lower.hasPrefix("ja") {
                return "ja"
            } else if lower.hasPrefix("ko") {
                return "ko"
            } else if lower.hasPrefix("th") {
                return "th"
            } else if lower.hasPrefix("de") {
                return "de"
            } else if lower.hasPrefix("fr") {
                return "fr"
            } else if lower.hasPrefix("es") {
                return "es"
            } else if lower.hasPrefix("ms") {
                return "ms"
            } else if lower.hasPrefix("id") {
                return "id"
            } else if lower.hasPrefix("vi") {
                return "vi"
            } else if lower.hasPrefix("en") {
                return "en"
            }
        }
        return "zh-Hans"
    }
}

public final class LocalizationManager: ObservableObject, @unchecked Sendable {
    public static let shared = LocalizationManager()
    
    private static let storageKey = "app_selected_language"
    
    private let lock = NSLock()
    private var lockedSelectedLanguage: AppLanguage
    private var lockedActiveLocale: Locale
    private var lockedActiveBundle: Bundle
    
    @Published public private(set) var selectedLanguage: AppLanguage
    @Published public private(set) var activeLocale: Locale
    
    public init() {
        let savedRaw = UserDefaults.standard.string(forKey: Self.storageKey) ?? AppLanguage.system.rawValue
        let initialLang = AppLanguage(rawValue: savedRaw) ?? .system
        let resolvedCode = initialLang.localeIdentifier
        let locale = Locale(identifier: resolvedCode)
        let bundle: Bundle
        if let path = Bundle.main.path(forResource: resolvedCode, ofType: "lproj"),
           let b = Bundle(path: path) {
            bundle = b
        } else {
            bundle = Bundle.main
        }
        
        self.lockedSelectedLanguage = initialLang
        self.lockedActiveLocale = locale
        self.lockedActiveBundle = bundle
        self.selectedLanguage = initialLang
        self.activeLocale = locale
    }
    
    public func setLanguage(_ language: AppLanguage) {
        let resolvedCode = language.localeIdentifier
        let locale = Locale(identifier: resolvedCode)
        let bundle: Bundle
        if let path = Bundle.main.path(forResource: resolvedCode, ofType: "lproj"),
           let b = Bundle(path: path) {
            bundle = b
        } else {
            bundle = Bundle.main
        }
        
        lock.withLock {
            lockedSelectedLanguage = language
            lockedActiveLocale = locale
            lockedActiveBundle = bundle
        }
        
        UserDefaults.standard.set(language.rawValue, forKey: Self.storageKey)
        
        if Thread.isMainThread {
            if self.selectedLanguage != language {
                self.selectedLanguage = language
            }
            if self.activeLocale != locale {
                self.activeLocale = locale
            }
        } else {
            DispatchQueue.main.async {
                if self.selectedLanguage != language {
                    self.selectedLanguage = language
                }
                if self.activeLocale != locale {
                    self.activeLocale = locale
                }
            }
        }
    }
    
    public nonisolated func localized(_ key: String, _ arguments: [CVarArg] = []) -> String {
        let (bundle, locale) = lock.withLock {
            (lockedActiveBundle, lockedActiveLocale)
        }
        let format = bundle.localizedString(forKey: key, value: nil, table: "Localizable")
        if arguments.isEmpty {
            return format
        }
        return String(format: format, locale: locale, arguments: arguments)
    }
}

public func L10n(_ key: String, _ arguments: CVarArg...) -> String {
    LocalizationManager.shared.localized(key, arguments)
}

extension String {
    public var localized: String {
        LocalizationManager.shared.localized(self)
    }
    
    public func localized(_ args: CVarArg...) -> String {
        LocalizationManager.shared.localized(self, args)
    }

    public var localizedCategoryLabel: String {
        switch self {
        case "全部":
            return "home.all".localized
        case "女性主播":
            return "live.female".localized
        case "男性主播":
            return "live.male".localized
        case "情侣双人", "情侶雙人", "情侣互动", "情侶互動":
            return "live.couples".localized
        case "跨性别", "跨性別":
            return "live.trans".localized
        default:
            return self
        }
    }
}
