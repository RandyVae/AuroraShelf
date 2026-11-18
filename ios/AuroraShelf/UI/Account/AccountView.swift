import SwiftUI

public struct AccountView: View {
    @ObservedObject private var service = AccountService.shared
    @ObservedObject private var locManager = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss
    @State private var isRegister = false
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var localValidationMessage: String?

    public init() {}

    private var trimmedEmail: String {
        email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private var isEmailValid: Bool {
        trimmedEmail.contains("@") && trimmedEmail.contains(".")
    }

    private var isFormValid: Bool {
        isEmailValid && password.count >= 8
    }

    private var hasUnsavedDraft: Bool {
        service.profile == nil && (
            !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            !password.isEmpty ||
            !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        )
    }

    private func planDisplayLabel(for status: String) -> String {
        switch status.lowercased() {
        case "pro", "active", "vip":
            return "account.pro_member".localized
        default:
            return "account.free_plan".localized
        }
    }

    public var body: some View {
        NavigationStack {
            Form {
                if let profile = service.profile {
                    Section("account.section_current".localized) {
                        LabeledContent("account.nickname".localized, value: profile.displayName)
                        LabeledContent("account.email".localized, value: profile.email)
                        LabeledContent(
                            "account.status".localized,
                            value: profile.isVerified ? "account.verified".localized : "account.unverified".localized
                        )
                        LabeledContent("account.plan".localized, value: planDisplayLabel(for: profile.subscriptionStatus))
                        if !profile.isVerified {
                            Button {
                                localValidationMessage = nil
                                Task { await service.requestVerification() }
                            } label: {
                                HStack {
                                    Text("account.resend_email".localized)
                                    if service.isLoading {
                                        Spacer()
                                        ProgressView()
                                    }
                                }
                            }
                            .disabled(service.isLoading)
                        }
                        Button("account.refresh_status".localized) {
                            localValidationMessage = nil
                            Task { await service.refresh() }
                        }
                        .disabled(service.isLoading)
                        Button("account.logout".localized, role: .destructive) {
                            localValidationMessage = nil
                            service.signOut()
                        }
                    }
                } else {
                    Section {
                        if isRegister {
                            TextField("account.nickname_optional".localized, text: $displayName)
                                .textContentType(.nickname)
                                .autocorrectionDisabled()
                        }
                        TextField("account.email".localized, text: $email)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.emailAddress)
                            .textContentType(.emailAddress)
                        SecureField("account.password_hint".localized, text: $password)
                            .textContentType(isRegister ? .newPassword : .password)
                        Button {
                            guard isEmailValid else {
                                localValidationMessage = "account.invalid_email".localized
                                return
                            }
                            guard password.count >= 8 else {
                                localValidationMessage = "account.short_password".localized
                                return
                            }
                            localValidationMessage = nil
                            Task {
                                if isRegister {
                                    await service.register(email: trimmedEmail, password: password, displayName: displayName)
                                } else {
                                    await service.signIn(email: trimmedEmail, password: password)
                                }
                            }
                        } label: {
                            HStack {
                                Text(isRegister ? "account.register".localized : "account.login".localized)
                                    .fontWeight(.semibold)
                                if service.isLoading {
                                    Spacer()
                                    ProgressView()
                                }
                            }
                        }
                        .disabled(service.isLoading || !isFormValid)

                        Button(isRegister ? "account.switch_to_login".localized : "account.switch_to_register".localized) {
                            isRegister.toggle()
                            localValidationMessage = nil
                            service.message = nil
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(AuroraColor.brandLabel)

                        Button("account.forgot_password".localized) {
                            guard isEmailValid else {
                                localValidationMessage = "account.enter_email_first".localized
                                return
                            }
                            localValidationMessage = nil
                            Task { await service.requestPasswordReset(email: trimmedEmail) }
                        }
                        .buttonStyle(.plain)
                        .disabled(service.isLoading)
                    } header: {
                        Text(isRegister ? "account.register_free_title".localized : "account.login_title".localized)
                    } footer: {
                        Text("account.local_storage_note".localized)
                    }
                }

                if let validation = localValidationMessage, !validation.isEmpty {
                    Section {
                        Text(validation)
                            .foregroundStyle(AuroraColor.warning)
                    }
                } else if let message = service.message, !message.isEmpty {
                    Section {
                        Text(message)
                            .foregroundStyle(AuroraColor.textSecondary)
                    }
                }
            }
            .navigationTitle("account.title".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("common.done".localized) { dismiss() }
                        .frame(minWidth: AuroraMetrics.minTouchTarget, minHeight: AuroraMetrics.minTouchTarget)
                }
            }
            .modifier(AccountDraftDismissalGuard(hasUnsavedDraft: hasUnsavedDraft, onDiscard: { dismiss() }))
        }
    }
}

private struct AccountDraftDismissalGuard: ViewModifier {
    let hasUnsavedDraft: Bool
    let onDiscard: () -> Void

    func body(content: Content) -> some View {
        if #available(iOS 27.0, *) {
            content
                .interactiveDismissDisabled(hasUnsavedDraft)
                .dismissalConfirmationDialog(
                    Text("dialog.discard_title".localized),
                    shouldPresent: hasUnsavedDraft
                ) {
                    Button("dialog.discard_confirm".localized, role: .destructive) {
                        onDiscard()
                    }
                    Button("dialog.keep_editing".localized, role: .cancel) {}
                }
        } else {
            content
                .interactiveDismissDisabled(hasUnsavedDraft)
        }
    }
}
