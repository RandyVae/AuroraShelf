import SwiftUI

/// 原生分类筛选胶囊条：遵循 Apple HIG 系统 Chip 规范
public struct SourceFilterBar: View {
    public let categories: [String]
    @Binding public var selectedCategory: String
    
    public init(categories: [String], selectedCategory: Binding<String>) {
        self.categories = categories
        self._selectedCategory = selectedCategory
    }
    
    public var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AuroraMetrics.spacingS) {
                ForEach(categories, id: \.self) { cat in
                    SourceFilterChip(
                        title: cat,
                        isSelected: selectedCategory == cat,
                        onSelect: {
                            withAnimation(AuroraMotion.quick) {
                                selectedCategory = cat
                            }
                        }
                    )
                }
            }
            .padding(.horizontal, AuroraMetrics.contentInset)
            .padding(.vertical, AuroraMetrics.spacingXS)
        }
    }
}

private struct SourceFilterChip: View {
    let title: String
    let isSelected: Bool
    let onSelect: () -> Void
    
    var body: some View {
        Button(action: {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            onSelect()
        }) {
            Text(title)
                .font(.subheadline)
                .fontWeight(isSelected ? .bold : .medium)
                .foregroundStyle(isSelected ? .white : AuroraColor.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(
                    Capsule(style: .continuous)
                        .fill(isSelected ? AuroraColor.brand : Color.white.opacity(0.08))
                )
                .overlay(
                    Capsule(style: .continuous)
                        .stroke(isSelected ? Color.clear : Color.white.opacity(0.12), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}
