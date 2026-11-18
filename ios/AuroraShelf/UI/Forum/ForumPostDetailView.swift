import SafariServices
import SwiftUI
import UIKit

public struct ForumPostDetailView: View {
    public let post: ForumPostItem
    public let onDismiss: (() -> Void)?
    @ObservedObject private var imageSaver = ForumImageSaver.shared
    @State private var loadedPost: ForumPostItem?
    @State private var loadError: String?
    @State private var isLoading = false
    @State private var saveFeedbackMessage: String?
    @State private var feedbackDismissTask: Task<Void, Never>?
    @State private var showPhotoPermissionAlert = false
    @State private var showOriginalPostSafari = false
    @Environment(\.dismiss) private var dismiss

    public init(post: ForumPostItem, onDismiss: (() -> Void)? = nil) {
        self.post = post
        self.onDismiss = onDismiss
        if let cached = ForumRepository.shared.cachedDetail(for: post) {
            _loadedPost = State(initialValue: cached)
        }
    }

    private var activeBatchProgress: (completed: Int, total: Int)? {
        imageSaver.batchProgress(for: post.compositeID)
    }

    private var originalThreadURL: URL? {
        guard let url = URL(string: displayPost.threadUrl),
              let scheme = url.scheme?.lowercased(),
              scheme == "https" || scheme == "http" else {
            return nil
        }
        return url
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

                if let batch = activeBatchProgress {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Label("正在下载论坛图片 \(batch.completed) / \(batch.total)", systemImage: "arrow.down.circle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(AuroraColor.brand)
                            Spacer()
                            Text("\(Int(Double(batch.completed) / Double(max(1, batch.total)) * 100))%")
                                .font(.caption.monospacedDigit().bold())
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        ProgressView(value: Double(batch.completed), total: Double(max(1, batch.total)))
                            .tint(AuroraColor.brand)
                    }
                    .padding(AuroraMetrics.spacingM)
                    .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius))
                    .overlay(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius)
                            .stroke(AuroraColor.hairline, lineWidth: 1)
                    )
                }

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

                if !detailImageURLs.isEmpty {
                    LazyVStack(spacing: AuroraMetrics.spacingM) {
                        ForEach(detailImageURLs, id: \.self) { imageURL in
                            if let url = URL(string: imageURL) {
                                ForumPostImage(
                                    url: url,
                                    referer: post.threadUrl,
                                    onSaveCompleted: { message, permissionDenied in
                                        showFeedback(message)
                                        if permissionDenied {
                                            showPhotoPermissionAlert = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            .padding(AuroraMetrics.contentInset)
            .frame(maxWidth: 720, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(AuroraColor.canvas)
        .overlay(alignment: .bottom) {
            if let feedback = saveFeedbackMessage {
                Text(feedback)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.black.opacity(0.84), in: Capsule())
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.85), value: saveFeedbackMessage)
        .navigationTitle(post.boardName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .task(id: post.id) { await loadDetail() }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button(action: { closeDetail() }) {
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
                HStack(spacing: 8) {
                    if !detailImageURLs.isEmpty {
                        Button {
                            Task {
                                let urls = detailImageURLs
                                let result = await imageSaver.saveAllImages(
                                    urls,
                                    referer: post.threadUrl,
                                    postKey: post.compositeID
                                )
                                if result.succeeded == 0 {
                                    showFeedback("图片下载失败，请检查网络连接")
                                } else if result.savedToPhotoLibraryCount > 0 {
                                    showFeedback("已保存 \(result.succeeded)/\(result.total) 张原图到相册与文档")
                                } else {
                                    showFeedback("已缓存 \(result.succeeded)/\(result.total) 张原图到本地文档")
                                }
                                if result.photoPermissionDenied {
                                    showPhotoPermissionAlert = true
                                }
                            }
                        } label: {
                            if let batch = activeBatchProgress {
                                HStack(spacing: 4) {
                                    ProgressView()
                                        .controlSize(.mini)
                                    Text("\(batch.completed)/\(batch.total)")
                                        .font(.caption.monospacedDigit().bold())
                                }
                            } else {
                                Label("下载图片(\(detailImageURLs.count))", systemImage: "arrow.down.circle")
                                    .font(.caption.weight(.semibold))
                            }
                        }
                        .disabled(activeBatchProgress != nil)
                        .accessibilityLabel("下载本帖全部 \(detailImageURLs.count) 张图片")
                    }

                    if originalThreadURL != nil {
                        Button {
                            showOriginalPostSafari = true
                        } label: {
                            Image(systemName: "safari")
                                .font(.subheadline.weight(.semibold))
                                .frame(width: 36, height: 36)
                        }
                        .accessibilityLabel("forum.original_post".localized)
                    }

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
        }
        .sheet(isPresented: $showOriginalPostSafari) {
            if let url = originalThreadURL {
                ForumSafariView(url: url)
                    .ignoresSafeArea()
            }
        }
        .alert("无法写入系统相册", isPresented: $showPhotoPermissionAlert) {
            Button("前往设置") {
                if let settingsURL = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(settingsURL)
                }
            }
            Button("common.cancel".localized, role: .cancel) {}
        } message: {
            Text("原图已缓存至应用本地文档。若需同步存入系统相册，请在设置中开启「照片（仅添加）」权限。")
        }
        .enableSwipeBack {
            closeDetail()
        }
        .onDisappear {
            feedbackDismissTask?.cancel()
        }
    }

    private func showFeedback(_ message: String) {
        feedbackDismissTask?.cancel()
        saveFeedbackMessage = message
        feedbackDismissTask = Task {
            try? await Task.sleep(for: .seconds(2.6))
            guard !Task.isCancelled else { return }
            saveFeedbackMessage = nil
        }
    }

    private func closeDetail() {
        if let onDismiss {
            onDismiss()
        } else {
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

private struct ForumSafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let vc = SFSafariViewController(url: url)
        vc.dismissButtonStyle = .done
        return vc
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

private struct ForumPostImage: View {
    let url: URL
    let referer: String
    let onSaveCompleted: (_ message: String, _ photoPermissionDenied: Bool) -> Void
    @ObservedObject private var saver = ForumImageSaver.shared
    @State private var image: UIImage?
    @State private var didFail = false
    @State private var reloadToken = UUID()

    private var isSaving: Bool {
        saver.activeSavingURLs.contains(url.absoluteString)
    }

    private var isSaved: Bool {
        saver.isSaved(urlString: url.absoluteString)
    }

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
                    .overlay(alignment: .bottomTrailing) {
                        Button {
                            triggerSave()
                        } label: {
                            Group {
                                if isSaving {
                                    ProgressView()
                                        .controlSize(.small)
                                        .tint(.white)
                                } else if isSaved {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 18, weight: .bold))
                                        .foregroundStyle(.green)
                                } else {
                                    Image(systemName: "arrow.down.to.line")
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundStyle(.white)
                                }
                            }
                            .frame(width: 40, height: 40)
                            .background(.ultraThinMaterial, in: Circle())
                            .background(Color.black.opacity(0.35), in: Circle())
                            .overlay(Circle().stroke(Color.white.opacity(0.22), lineWidth: 0.75))
                            .shadow(color: .black.opacity(0.25), radius: 6, y: 2)
                            .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(isSaving)
                        .padding(6)
                        .accessibilityLabel(isSaved ? "原图已保存" : "下载原图")
                    }
                    .contextMenu {
                        Button {
                            triggerSave()
                        } label: {
                            Label(isSaved ? "重新保存原图" : "保存原图到相册", systemImage: "square.and.arrow.down")
                        }
                        if let localURL = saver.savedLocalURLs[url.absoluteString] {
                            ShareLink(item: localURL) {
                                Label("分享原图", systemImage: "square.and.arrow.up")
                            }
                        }
                        Button {
                            UIPasteboard.general.string = url.absoluteString
                            onSaveCompleted("已复制图片链接", false)
                        } label: {
                            Label("复制图片链接", systemImage: "doc.on.doc")
                        }
                    }
            } else if didFail {
                VStack(spacing: AuroraMetrics.spacingS) {
                    Image(systemName: "photo.badge.exclamationmark")
                        .font(.title2)
                        .foregroundStyle(AuroraColor.textSecondary)
                    Text("图片无法加载")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AuroraColor.textPrimary)
                    Button("重试", systemImage: "arrow.clockwise") {
                        didFail = false
                        reloadToken = UUID()
                    }
                    .buttonStyle(.bordered)
                    .tint(AuroraColor.brand)
                }
                .frame(maxWidth: .infinity, minHeight: 160)
                .padding(AuroraMetrics.spacingM)
                .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius, style: .continuous))
            } else {
                ProgressView("reader.loading_image".localized)
                    .frame(maxWidth: .infinity, minHeight: 180)
            }
        }
        .task(id: "\(url.absoluteString)#\(reloadToken.uuidString)") {
            didFail = false
            image = await CoverImageLoader.shared.loadImage(
                from: url.absoluteString,
                requestReferer: referer
            )
            didFail = image == nil
        }
    }

    private func triggerSave() {
        Task {
            do {
                let outcome = try await saver.saveImage(urlString: url.absoluteString, referer: referer)
                if outcome.savedToPhotoLibrary {
                    onSaveCompleted("图片已保存到系统相册", false)
                } else {
                    onSaveCompleted("已保存到本地文档", outcome.photoPermissionDenied)
                }
            } catch {
                onSaveCompleted(error.localizedDescription, false)
            }
        }
    }
}
