import SwiftUI

public enum MainTab: String, CaseIterable, Identifiable {
    case home = "home"
    case comics = "comics"
    case live = "live"
    case forum = "forum"
    case profile = "profile"
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .home: return "tab.home".localized
        case .comics: return "tab.comics".localized
        case .live: return "tab.live".localized
        case .forum: return "tab.forum".localized
        case .profile: return "tab.library".localized
        }
    }
    
    public var iconName: String {
        switch self {
        case .home: return "house"
        case .comics: return "book.pages"
        case .live: return "dot.radiowaves.left.and.right"
        case .forum: return "person.2"
        case .profile: return "books.vertical"
        }
    }
    
    public var selectedIconName: String {
        switch self {
        case .home: return "house.fill"
        case .comics: return "book.pages.fill"
        case .live: return "dot.radiowaves.left.and.right"
        case .forum: return "person.2.fill"
        case .profile: return "books.vertical.fill"
        }
    }
}
