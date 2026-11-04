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
        if isSelected {
            Button(action: onSelect) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .buttonStyle(.borderedProminent)
            .tint(AuroraColor.brand)
            .clipShape(Capsule())
            .frame(minHeight: AuroraMetrics.minTouchTarget)
        } else {
            Button(action: onSelect) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.regular)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .buttonStyle(.bordered)
            .tint(.secondary)
            .clipShape(Capsule())
            .frame(minHeight: AuroraMetrics.minTouchTarget)
        }
    }
}
