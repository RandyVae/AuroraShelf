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

    public init(
        stream: LiveStreamItem,
        externalComments: [LiveComment] = [],
        isLivePlaying: Bool = false
    ) {
        self.stream = stream
        self.externalComments = externalComments
        self.isLivePlaying = isLivePlaying
    }

    private var comments: [LiveComment] {
        externalComments
    }

    private var status: String {
        isLivePlaying || !externalComments.isEmpty ? "已连接" : "等待新评论…"
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: AuroraMetrics.spacingS) {
            HStack {
                Label("实时评论", systemImage: "bubble.left.and.bubble.right.fill")
                    .font(.headline)
                Spacer()
                Circle().fill(status == "已连接" ? .green : .secondary).frame(width: 7, height: 7)
                Text(status).font(.caption).foregroundStyle(AuroraColor.textSecondary)
            }
            if comments.isEmpty {
                Text("暂无新评论…")
                    .font(.subheadline)
                    .foregroundStyle(AuroraColor.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: 72)
            } else {
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
                                .lineLimit(2)
                        }
                    }
                }
            }
        }
        .padding(AuroraMetrics.spacingL)
        .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
    }
}
