import SwiftUI

public struct CategoryPillBar: View {
    public let categories: [String]
    @Binding public var selectedCategory: String
    
    public init(categories: [String], selectedCategory: Binding<String>) {
        self.categories = categories
        self._selectedCategory = selectedCategory
    }
    
    public var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(categories, id: \.self) { cat in
                    let isSelected = selectedCategory == cat
                    Button(action: {
                        let impact = UIImpactFeedbackGenerator(style: .rigid)
                        impact.impactOccurred()
                        withAnimation(AuroraTheme.appleSpring) {
                            selectedCategory = cat
                        }
                    }) {
                        Text(cat)
                            .font(.system(size: 13, weight: isSelected ? .semibold : .regular))
                            .foregroundColor(isSelected ? .white : AuroraTheme.textSecondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(
                                ZStack {
                                    if isSelected {
                                        Capsule()
                                            .fill(AuroraTheme.crimson)
                                            .shadow(color: AuroraTheme.crimson.opacity(0.4), radius: 8, x: 0, y: 3)
                                    } else {
                                        Capsule()
                                            .fill(.ultraThinMaterial)
                                            .environment(\.colorScheme, .dark)
                                            .overlay(
                                                Capsule()
                                                    .stroke(AuroraTheme.glassBorder, lineWidth: 0.8)
                                            )
                                    }
                                }
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                    .applePressEffect(scale: 0.94)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
        }
    }
}
