import SwiftUI

public struct ForumPostDetailView: View {
    public let post: ForumPostItem
    @State private var loadedPost: ForumPostItem?
    @State private var loadError: String?
    @State private var isLoading = false
    @Environment(\.dismiss) private var dismiss

    public init(post: ForumPostItem) {
        self.post = post
        if let cached = ForumRepository.shared.cachedDetail(for: post) {
            _loadedPost = State(initialValue: cached)
        }
    }

    public var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: AuroraMetrics.spacingL) {
                Text(displayPost.title)
                    .font(.title2.bold())
                    .foregroundStyle(AuroraColor.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                metadata

                Divider()
                    .overlay(AuroraColor.hairline)

                if isLoading {
                    ProgressView("正在加载帖子正文…")
                        .frame(maxWidth: .infinity)
                } else if let loadError {
                    ContentUnavailableView {
                        Label("正文加载失败", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(loadError)
                    } actions: {
                        Button("重试", systemImage: "arrow.clockwise") { Task { await loadDetail() } }
                            .buttonStyle(.borderedProminent)
                    }
                } else if displayPost.contentText.isEmpty && displayPost.contentImageUrls.isEmpty {
                    ContentUnavailableView("正文暂不可用", systemImage: "doc.text", description: Text("论坛源没有返回帖子正文。"))
                } else if !displayPost.contentText.isEmpty {
                    Text(displayPost.contentText)
                        .font(.body)
                        .foregroundStyle(AuroraColor.textPrimary)
                        .lineSpacing(6)
                        .textSelection(.enabled)
                }

                ForEach(detailImageURLs, id: \.self) { imageURL in
                    if let url = URL(string: imageURL) {
                        ForumPostImage(url: url, referer: post.threadUrl)
                    }
                }
            }
            .padding(AuroraMetrics.contentInset)
            .frame(maxWidth: 720, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(AuroraColor.canvas)
        .navigationTitle(post.boardName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .task(id: post.id) { await loadDetail() }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.primary)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityLabel("返回")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Text(post.boardName)
                    .font(.caption.bold())
                    .foregroundStyle(post.boardName.contains("草榴") ? AuroraColor.forestGreen : AuroraColor.brand)
                    .padding(.horizontal, AuroraMetrics.spacingS)
                    .padding(.vertical, AuroraMetrics.spacingXS)
                    .background(
                        (post.boardName.contains("草榴") ? AuroraColor.forestGreen : AuroraColor.brand).opacity(0.18),
                        in: .capsule
                    )
                    .accessibilityHidden(true)
            }
        }
        .enableSwipeBack {
            dismiss()
        }
    }

    private var displayPost: ForumPostItem { loadedPost ?? post }

    private var detailImageURLs: [String] {
        if !displayPost.contentImageUrls.isEmpty { return displayPost.contentImageUrls }
        return displayPost.previewImageUrl.map { [$0] } ?? []
    }

    @MainActor
    private func loadDetail() async {
        if let loaded = loadedPost, !loaded.contentText.isEmpty { return }
        if let cached = ForumRepository.shared.cachedDetail(for: post) {
            loadedPost = cached
            return
        }
        guard post.contentText.isEmpty else { loadedPost = post; return }
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            loadedPost = try await ForumRepository.shared.loadDetail(for: post)
        } catch {
            loadError = error.localizedDescription
        }
    }

    @ViewBuilder
    private var metadata: some View {
        if post.author != nil || post.postTime != nil || post.views != nil || post.replies != nil {
            HStack(alignment: .top, spacing: AuroraMetrics.spacingM) {
                if let author = post.author {
                    Circle()
                        .fill(AuroraColor.brand.opacity(0.25))
                        .frame(width: 36, height: 36)
                        .overlay {
                            Text(String(author.prefix(1)))
                                .font(.headline)
                                .foregroundStyle(AuroraColor.textPrimary)
                        }
                        .accessibilityHidden(true)
                    }

                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                    if let author = post.author {
                        Text(author)
                            .font(.subheadline.bold())
                            .foregroundStyle(AuroraColor.textPrimary)
                    }

                    if post.postTime != nil || post.views != nil || post.replies != nil {
                        ViewThatFits(in: .horizontal) {
                            metadataLine
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                                if let postTime = post.postTime { Text(postTime) }
                                if let views = post.views { Text("\(views) 次浏览") }
                                if let replies = post.replies { Text("\(replies) 条回复") }
                            }
                        }
                        .font(.caption)
                        .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
            }
        }
    }

    private var metadataLine: some View {
        HStack(spacing: AuroraMetrics.spacingXS) {
            if let postTime = post.postTime {
                Text(postTime)
            }
            if let views = post.views {
                if post.postTime != nil { Text("·") }
                Text("\(views) 次浏览")
            }
            if let replies = post.replies {
                if post.postTime != nil || post.views != nil { Text("·") }
                Text("\(replies) 条回复")
            }
        }
    }
}

private struct ForumPostImage: View {
    let url: URL
    let referer: String
    @State private var image: UIImage?
    @State private var didFail = false

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
            } else if didFail {
                ContentUnavailableView(
                    "图片无法加载",
                    systemImage: "photo",
                    description: Text("请检查网络连接后重试。")
                )
            } else {
                ProgressView("正在加载图片…")
                    .frame(maxWidth: .infinity)
            }
        }
        .task(id: url.absoluteString) {
            image = await CoverImageLoader.shared.loadImage(
                from: url.absoluteString,
                requestReferer: referer
            )
            didFail = image == nil
        }
    }
}
