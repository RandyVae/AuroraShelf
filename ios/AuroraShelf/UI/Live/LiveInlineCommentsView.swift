import SwiftUI
import WebKit

public struct LiveComment: Identifiable, Equatable {
    public let id: String
    public let author: String
    public let text: String

    public init(id: String, author: String, text: String) {
        self.id = id
        self.author = author
        self.text = text
    }
}

public struct LiveInlineCommentsView: View {
    public let stream: LiveStreamItem
    public var externalComments: [LiveComment] = []
    public var isLivePlaying: Bool = false
    @Binding public var isExpanded: Bool
    public var maxBodyHeight: CGFloat? = nil

    public init(
        stream: LiveStreamItem,
        externalComments: [LiveComment] = [],
        isLivePlaying: Bool = false,
        isExpanded: Binding<Bool> = .constant(true),
        maxBodyHeight: CGFloat? = nil
    ) {
        self.stream = stream
        self.externalComments = externalComments
        self.isLivePlaying = isLivePlaying
        self._isExpanded = isExpanded
        self.maxBodyHeight = maxBodyHeight
    }

    private var comments: [LiveComment] {
        externalComments
    }

    private var status: String {
        isLivePlaying || !externalComments.isEmpty ? "已连接" : "等待新评论…"
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: isExpanded ? AuroraMetrics.spacingS : 0) {
            Button {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: 8) {
                    Label("实时评论", systemImage: "bubble.left.and.bubble.right.fill")
                        .font(.headline)
                        .foregroundStyle(AuroraColor.textPrimary)
                    if !comments.isEmpty {
                        Text("\(comments.count)")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(AuroraColor.textSecondary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(AuroraColor.surfaceCard, in: Capsule())
                    }
                    Spacer()
                    Circle().fill(status == "已连接" ? .green : .secondary).frame(width: 7, height: 7)
                    Text(status).font(.caption).foregroundStyle(AuroraColor.textSecondary)
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AuroraColor.textSecondary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isExpanded ? "收起实时评论" : "展开实时评论")

            if isExpanded {
                if comments.isEmpty {
                    Text("暂无新评论…")
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: 72)
                } else if let maxBodyHeight {
                    ScrollView(.vertical, showsIndicators: true) {
                        commentsList
                    }
                    .frame(maxHeight: maxBodyHeight)
                } else {
                    commentsList
                }
            }
        }
        .padding(AuroraMetrics.spacingL)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
    }

    private var commentsList: some View {
        LazyVStack(alignment: .leading, spacing: 8) {
            ForEach(comments) { comment in
                HStack(alignment: .top, spacing: 8) {
                    Text(comment.author)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AuroraColor.brandLabel)
                        .lineLimit(1)
                    Text(comment.text)
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}
