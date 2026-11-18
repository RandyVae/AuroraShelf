import SwiftUI

/// 原生分类筛选胶囊条：遵循 Apple HIG 系统 Chip 规范与 WCAG AA 对比度标准
public struct SourceFilterBar: View {
    public let categories: [String]
    @Binding public var selectedCategory: String
    @ObservedObject private var locManager = LocalizationManager.shared
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
                        title: cat.localizedCategoryLabel,
                        isSelected: selectedCategory == cat,
                        onSelect: {
                            AuroraMotion.perform(AuroraMotion.quick, reduceMotion: reduceMotion) {
                                selectedCategory = cat
                            }
                        }
                    )
                }
            }
            .padding(.horizontal, AuroraMetrics.contentInset)
            .padding(.vertical, AuroraMetrics.spacingXS)
        }
        .sensoryFeedback(.selection, trigger: selectedCategory)
    }
}

private struct SourceFilterChip: View {
    let title: String
    let isSelected: Bool
    let onSelect: () -> Void
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast

    var body: some View {
        Button(action: onSelect) {
            Text(title)
                .font(.subheadline)
                .fontWeight(isSelected ? .bold : .medium)
                .foregroundStyle(isSelected ? Color.white : AuroraColor.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .frame(minHeight: AuroraMetrics.minTouchTarget)
                .contentShape(Capsule(style: .continuous))
                .background(
                    Capsule(style: .continuous)
                        .fill(isSelected ? AuroraColor.brandSolid : unselectedChipFill)
                )
                .overlay(
                    Capsule(style: .continuous)
                        .stroke(isSelected ? Color.clear : AuroraColor.hairline, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var unselectedChipFill: Color {
        if colorScheme == .dark && colorSchemeContrast == .standard {
            return Color.white.opacity(0.08)
        }
        return AuroraColor.surfaceHigh
    }
}
