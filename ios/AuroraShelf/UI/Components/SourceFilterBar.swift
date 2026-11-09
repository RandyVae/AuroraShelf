import SwiftUI

/// 原生分类筛选胶囊条：遵循 Apple HIG 系统 Chip 规范
public struct SourceFilterBar: View {
    public let categories: [String]
    @Binding public var selectedCategory: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    
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
                            withAnimation(AuroraMotion.selection(reduceMotion: reduceMotion)) {
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
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast
    
    var body: some View {
        Button(action: {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            onSelect()
        }) {
            Text(title)
                .font(.subheadline)
                .fontWeight(isSelected ? .bold : .medium)
                .foregroundStyle(isSelected ? AuroraColor.textOnBrand : AuroraColor.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .frame(minHeight: AuroraMetrics.minTouchTarget)
                .contentShape(Capsule(style: .continuous))
                .background(
                    Capsule(style: .continuous)
                        .fill(isSelected ? AuroraColor.brand : unselectedChipFill)
                )
                .overlay(
                    Capsule(style: .continuous)
                        .stroke(isSelected ? Color.clear : AuroraColor.hairline, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var unselectedChipFill: Color {
        if colorScheme == .dark && colorSchemeContrast == .standard {
            return Color.white.opacity(0.08)
        }
        return AuroraColor.surfaceHigh
    }
}
