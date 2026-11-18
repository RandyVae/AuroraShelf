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
    public var isCommentBridgeActive: Bool = false
    @Binding public var isExpanded: Bool
    public var maxBodyHeight: CGFloat?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        stream: LiveStreamItem,
        externalComments: [LiveComment] = [],
        isLivePlaying: Bool = false,
        isCommentBridgeActive: Bool = false,
        isExpanded: Binding<Bool> = .constant(true),
        maxBodyHeight: CGFloat? = nil
    ) {
        self.stream = stream
        self.externalComments = externalComments
        self.isLivePlaying = isLivePlaying
        self.isCommentBridgeActive = isCommentBridgeActive
        self._isExpanded = isExpanded
        self.maxBodyHeight = maxBodyHeight
    }

    private var comments: [LiveComment] {
        externalComments
    }

    private var isBridgeConnected: Bool {
        isCommentBridgeActive || !externalComments.isEmpty
    }

    private var status: String {
        if isBridgeConnected {
            return "live.connected".localized
        }
        return isLivePlaying ? "live.tap_chat_hint".localized : "live.waiting_connect".localized
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: isExpanded ? AuroraMetrics.spacingS : 0) {
            Button {
                AuroraHaptics.selection()
                AuroraMotion.perform(AuroraMotion.snappy, reduceMotion: reduceMotion) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: 8) {
                    Label("live.inline_comments".localized, systemImage: "bubble.left.and.bubble.right.fill")
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
                    Circle()
                        .fill(isBridgeConnected ? .green : .secondary)
                        .frame(width: 7, height: 7)
                    Text(status)
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .lineLimit(1)
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AuroraColor.textSecondary)
                }
                .frame(minHeight: AuroraMetrics.minTouchTarget)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isExpanded ? "live.collapse_comments".localized : "live.expand_comments".localized)

            if isExpanded {
                if comments.isEmpty {
                    Text(isBridgeConnected ? "live.no_new_comments".localized : "live.native_low_latency_hint".localized)
                        .font(.subheadline)
                        .foregroundStyle(AuroraColor.textSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, minHeight: 72)
                        .padding(.horizontal, 8)
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
        .padding(.horizontal, AuroraMetrics.spacingL)
        .padding(.vertical, AuroraMetrics.spacingS)
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
        .padding(.bottom, AuroraMetrics.spacingS)
    }
}
