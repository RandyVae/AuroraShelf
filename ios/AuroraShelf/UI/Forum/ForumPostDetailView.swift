import SafariServices
import SwiftUI
import UIKit

public struct ForumPostDetailView: View {
    public let post: ForumPostItem
    public let onDismiss: (() -> Void)?
    @ObservedObject private var imageSaver = ForumImageSaver.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    @State private var loadedPost: ForumPostItem?
    @State private var loadError: String?
    @State private var isLoading = false
    @State private var saveFeedbackMessage: String?
    @State private var feedbackDismissTask: Task<Void, Never>?
    @State private var showPhotoPermissionAlert = false
    @State private var showOriginalPostSafari = false
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

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
                    .accessibilityAddTraits(.isHeader)

                metadata

                Divider()
                    .overlay(AuroraColor.hairline)

                if let batch = activeBatchProgress {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Label("forum.downloading_images".localized(batch.completed, batch.total), systemImage: "arrow.down.circle.fill")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(AuroraColor.brandLabel)
                            Spacer()
                            Text("\(Int(Double(batch.completed) / Double(max(1, batch.total)) * 100))%")
                                .font(.caption.monospacedDigit().bold())
                                .foregroundStyle(AuroraColor.textSecondary)
                        }
                        ProgressView(value: Double(batch.completed), total: Double(max(1, batch.total)))
                            .tint(AuroraColor.brandSolid)
                    }
                    .padding(AuroraMetrics.spacingM)
                    .background(AuroraColor.surfaceCard, in: RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius))
                    .overlay(
                        RoundedRectangle(cornerRadius: AuroraMetrics.cardRadius)
                            .stroke(AuroraColor.hairline, lineWidth: 1)
                    )
                }

                if isLoading {
                    ProgressView("forum.loading_body".localized)
                        .frame(maxWidth: .infinity)
                } else if let loadError {
                    ContentUnavailableView {
                        Label("forum.body_failed".localized, systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(loadError)
                    } actions: {
                        Button("common.retry".localized, systemImage: "arrow.clockwise") { Task { await loadDetail() } }
                            .buttonStyle(.borderedProminent)
                    }
                } else if displayPost.contentText.isEmpty && displayPost.contentImageUrls.isEmpty {
                    ContentUnavailableView(
                        "forum.body_unavailable".localized,
                        systemImage: "doc.text",
                        description: Text("forum.body_unavailable_desc".localized)
                    )
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
            .safeAreaPadding(.bottom, AuroraMetrics.spacingXXL)
        }
        .background(AuroraColor.canvas)
        .overlay(alignment: .bottom) {
            if let feedback = saveFeedbackMessage {
                Text(feedback)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .glassEffect(.regular, in: .capsule)
                    .environment(\.colorScheme, .dark)
                    .padding(.bottom, 24)
                    .transition(.opacity)
            }
        }
        .animation(AuroraMotion.smooth(reduceMotion: reduceMotion), value: saveFeedbackMessage)
        .navigationTitle(post.boardName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .task(id: post.id) { await loadDetail() }
        .toolbar {
            detailToolbarContent
        }
        .sheet(isPresented: $showOriginalPostSafari) {
            if let url = originalThreadURL {
                ForumSafariView(url: url)
                    .ignoresSafeArea()
            }
        }
        .alert("forum.photo_perm_title".localized, isPresented: $showPhotoPermissionAlert) {
            Button("common.open_settings".localized) {
                if let settingsURL = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(settingsURL)
                }
            }
            Button("common.cancel".localized, role: .cancel) {}
        } message: {
            Text("forum.photo_perm_desc".localized)
        }
        .enableSwipeBack {
            closeDetail()
        }
        .onDisappear {
            feedbackDismissTask?.cancel()
        }
    }

    @ToolbarContentBuilder
    private var detailToolbarContent: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button(action: { closeDetail() }) {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.bold))
                    .foregroundStyle(.primary)
                    .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .hoverEffect(.highlight)
            .accessibilityLabel("common.back".localized)
        }

        if #available(iOS 27.0, *) {
            if !detailImageURLs.isEmpty {
                ToolbarItem(placement: .topBarPinnedTrailing) {
                    saveAllImagesButton
                }
            }
            if originalThreadURL != nil {
                ToolbarOverflowMenu {
                    Button {
                        showOriginalPostSafari = true
                    } label: {
                        Label("forum.original_post".localized, systemImage: "safari")
                    }
                }
            }
        } else {
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 8) {
                    if !detailImageURLs.isEmpty {
                        saveAllImagesButton
                    }
                    if originalThreadURL != nil {
                        Button {
                            showOriginalPostSafari = true
                        } label: {
                            Image(systemName: "safari")
                                .font(.subheadline.weight(.semibold))
                                .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                                .contentShape(Rectangle())
                        }
                        .accessibilityLabel("forum.original_post".localized)
                    }
                }
            }
        }
    }

    private var saveAllImagesButton: some View {
        Button {
            Task {
                let urls = detailImageURLs
                let result = await imageSaver.saveAllImages(
                    urls,
                    referer: post.threadUrl,
                    postKey: post.compositeID
                )
                if result.succeeded == 0 {
                    showFeedback("forum.img_download_failed".localized)
                } else if result.savedToPhotoLibraryCount > 0 {
                    showFeedback("forum.img_saved_photos".localized(result.succeeded, result.total))
                } else {
                    showFeedback("forum.img_saved_docs".localized(result.succeeded, result.total))
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
                .frame(minHeight: AuroraMetrics.minTouchTarget)
            } else {
                Label("forum.download_all_btn".localized(detailImageURLs.count), systemImage: "arrow.down.circle")
                    .font(.caption.weight(.semibold))
                    .frame(minHeight: AuroraMetrics.minTouchTarget)
            }
        }
        .disabled(activeBatchProgress != nil)
        .accessibilityLabel("forum.download_all_a11y".localized(detailImageURLs.count))
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
                        .fill(AuroraColor.brand.opacity(0.22))
                        .frame(width: 36, height: 36)
                        .overlay {
                            Text(String(author.prefix(1)))
                                .font(.headline)
                                .foregroundStyle(AuroraColor.textPrimary)
                        }
                        .accessibilityHidden(true)
                }

                VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                    HStack(spacing: AuroraMetrics.spacingS) {
                        if let author = post.author {
                            Text(author)
                                .font(.subheadline.bold())
                                .foregroundStyle(AuroraColor.textPrimary)
                        }
                        let isT66y = post.boardName.contains("草榴") || post.boardName.contains("达盖尔") || post.fid == 100 || post.fid == 16
                        Text(post.boardName)
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(
                                isT66y ? AuroraColor.forestGreen : AuroraColor.accentRed,
                                in: .capsule
                            )
                    }

                    if post.postTime != nil || post.views != nil || post.replies != nil {
                        ViewThatFits(in: .horizontal) {
                            metadataLine
                            VStack(alignment: .leading, spacing: AuroraMetrics.spacingXS) {
                                if let postTime = post.postTime { Text(postTime) }
                                if let views = post.views { Text("forum.views_count".localized(views)) }
                                if let replies = post.replies { Text("forum.replies_detail".localized(replies)) }
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
                Text("forum.views_count".localized(views))
            }
            if let replies = post.replies {
                if post.postTime != nil || post.views != nil { Text("·") }
                Text("forum.replies_detail".localized(replies))
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
                                        .font(.body.weight(.bold))
                                        .foregroundStyle(.green)
                                } else {
                                    Image(systemName: "arrow.down.to.line")
                                        .font(.subheadline.weight(.bold))
                                        .foregroundStyle(.white)
                                }
                            }
                            .frame(width: 40, height: 40)
                            .glassEffect(.clear.interactive(), in: .circle)
                            .environment(\.colorScheme, .dark)
                            .shadow(color: .black.opacity(0.25), radius: 6, y: 2)
                            .frame(width: AuroraMetrics.minTouchTarget, height: AuroraMetrics.minTouchTarget)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .hoverEffect(.highlight)
                        .disabled(isSaving)
                        .padding(6)
                        .accessibilityLabel(isSaved ? "forum.img_saved_a11y".localized : "forum.img_download_a11y".localized)
                    }
                    .contextMenu {
                        Button {
                            triggerSave()
                        } label: {
                            Label(
                                isSaved ? "forum.img_resave".localized : "forum.img_save_photos".localized,
                                systemImage: "square.and.arrow.down"
                            )
                        }
                        if let localURL = saver.savedLocalURLs[url.absoluteString] {
                            ShareLink(item: localURL) {
                                Label("forum.img_share".localized, systemImage: "square.and.arrow.up")
                            }
                        }
                        Button {
                            UIPasteboard.general.string = url.absoluteString
                            onSaveCompleted("forum.img_link_copied".localized, false)
                        } label: {
                            Label("forum.img_copy_link".localized, systemImage: "doc.on.doc")
                        }
                    }
            } else if didFail {
                VStack(spacing: AuroraMetrics.spacingS) {
                    Image(systemName: "photo.badge.exclamationmark")
                        .font(.title2)
                        .foregroundStyle(AuroraColor.textSecondary)
                    Text("forum.img_load_failed".localized)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AuroraColor.textPrimary)
                    Button("common.retry".localized, systemImage: "arrow.clockwise") {
                        didFail = false
                        reloadToken = UUID()
                    }
                    .buttonStyle(.bordered)
                    .tint(AuroraColor.brandLabel)
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
                    onSaveCompleted("forum.img_saved_toast".localized, false)
                } else {
                    onSaveCompleted("forum.img_saved_local_toast".localized, outcome.photoPermissionDenied)
                }
            } catch {
                onSaveCompleted(error.localizedDescription, false)
            }
        }
    }
}
