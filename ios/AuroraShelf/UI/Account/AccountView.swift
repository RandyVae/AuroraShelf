import SwiftUI

public struct AccountView: View {
    @ObservedObject private var service = AccountService.shared
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

    private func planDisplayLabel(for status: String) -> String {
        switch status.lowercased() {
        case "pro", "active", "vip":
            return "PRO 会员"
        default:
            return "免费版"
        }
    }

    public var body: some View {
        NavigationStack {
            Form {
                if let profile = service.profile {
                    Section("当前账号") {
                        LabeledContent("昵称", value: profile.displayName)
                        LabeledContent("邮箱", value: profile.email)
                        LabeledContent("状态", value: profile.isVerified ? "邮箱已验证" : "邮箱待验证")
                        LabeledContent("计划", value: planDisplayLabel(for: profile.subscriptionStatus))
                        if !profile.isVerified {
                            Button {
                                localValidationMessage = nil
                                Task { await service.requestVerification() }
                            } label: {
                                HStack {
                                    Text("重新发送验证邮件")
                                    if service.isLoading {
                                        Spacer()
                                        ProgressView()
                                    }
                                }
                            }
                            .disabled(service.isLoading)
                        }
                        Button("刷新账号状态") {
                            localValidationMessage = nil
                            Task { await service.refresh() }
                        }
                        .disabled(service.isLoading)
                        Button("退出登录", role: .destructive) {
                            localValidationMessage = nil
                            service.signOut()
                        }
                    }
                } else {
                    Section {
                        if isRegister {
                            TextField("昵称（选填）", text: $displayName)
                                .textContentType(.nickname)
                                .autocorrectionDisabled()
                        }
                        TextField("邮箱", text: $email)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.emailAddress)
                            .textContentType(.emailAddress)
                        SecureField("密码（至少 8 位）", text: $password)
                            .textContentType(isRegister ? .newPassword : .password)
                        Button {
                            guard isEmailValid else {
                                localValidationMessage = "请输入有效的邮箱地址"
                                return
                            }
                            guard password.count >= 8 else {
                                localValidationMessage = "密码长度至少需要 8 位"
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
                                Text(isRegister ? "注册" : "登录")
                                    .fontWeight(.semibold)
                                if service.isLoading {
                                    Spacer()
                                    ProgressView()
                                }
                            }
                        }
                        .disabled(service.isLoading || !isFormValid)

                        Button(isRegister ? "已有账号？去登录" : "没有账号？免费注册") {
                            isRegister.toggle()
                            localValidationMessage = nil
                            service.message = nil
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(AuroraColor.brand)

                        Button("忘记密码") {
                            guard isEmailValid else {
                                localValidationMessage = "请先在上方输入有效的注册邮箱"
                                return
                            }
                            localValidationMessage = nil
                            Task { await service.requestPasswordReset(email: trimmedEmail) }
                        }
                        .buttonStyle(.plain)
                        .disabled(service.isLoading)
                    } header: {
                        Text(isRegister ? "注册免费账号" : "登录账号")
                    } footer: {
                        Text("片单、播放进度与离线下载保存在本机存储中，不依赖云端登录。")
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
            .navigationTitle("账号")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                        .frame(minWidth: 44, minHeight: 44)
                }
            }
        }
    }
}
