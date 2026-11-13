import SwiftUI

public struct AccountView: View {
    @ObservedObject private var service = AccountService.shared
    @Environment(\.dismiss) private var dismiss
    @State private var isRegister = false
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""

    public init() {}

    public var body: some View {
        NavigationStack {
            Form {
                if let profile = service.profile {
                    Section("当前账号") {
                        LabeledContent("昵称", value: profile.displayName)
                        LabeledContent("邮箱", value: profile.email)
                        LabeledContent("状态", value: profile.isVerified ? "邮箱已验证" : "邮箱待验证")
                        LabeledContent("计划", value: "免费")
                        if !profile.isVerified {
                            Button("重新发送验证邮件") {
                                Task { await service.requestVerification() }
                            }
                        }
                        Button("退出登录", role: .destructive) { service.signOut() }
                    }
                } else {
                    Section(isRegister ? "注册免费账号" : "登录账号") {
                        if isRegister {
                            TextField("昵称", text: $displayName)
                        }
                        TextField("邮箱", text: $email)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.emailAddress)
                        SecureField("密码（至少 8 位）", text: $password)
                        Button(isRegister ? "注册" : "登录") {
                            Task {
                                if isRegister {
                                    await service.register(email: email, password: password, displayName: displayName)
                                } else {
                                    await service.signIn(email: email, password: password)
                                }
                            }
                        }
                        .disabled(service.isLoading)
                        Button(isRegister ? "已有账号？去登录" : "没有账号？免费注册") {
                            isRegister.toggle()
                            service.message = nil
                        }
                        .buttonStyle(.plain)
                        Button("忘记密码") {
                            Task { await service.requestPasswordReset(email: email) }
                        }
                        .buttonStyle(.plain)
                    }
                }
                if let message = service.message, !message.isEmpty {
                    Section { Text(message).foregroundStyle(.secondary) }
                }
            }
            .navigationTitle("账号")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}
