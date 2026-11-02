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
        case .home: return "精选"
        case .comics: return "漫画"
        case .live: return "直播"
        case .forum: return "论坛"
        case .profile: return "我的"
        }
    }
    
    public var iconName: String {
        switch self {
        case .home: return "sparkles.tv"
        case .comics: return "books.vertical"
        case .live: return "antenna.radiowaves.left.and.right"
        case .forum: return "bubble.left.and.bubble.right"
        case .profile: return "person.crop.circle"
        }
    }
    
    public var selectedIconName: String {
        switch self {
        case .home: return "sparkles.tv.fill"
        case .comics: return "books.vertical.fill"
        case .live: return "antenna.radiowaves.left.and.right.circle.fill"
        case .forum: return "bubble.left.and.bubble.right.fill"
        case .profile: return "person.crop.circle.fill"
        }
    }
}
