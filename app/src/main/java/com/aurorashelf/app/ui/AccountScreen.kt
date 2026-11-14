package com.aurorashelf.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.data.AccountRepository
import com.aurorashelf.app.data.AccountResult
import com.aurorashelf.app.data.AccountState

@Composable
internal fun AccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { AccountRepository.get(context) }
    val accountState by repository.state.collectAsStateWithLifecycle()
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isResetting by rememberSaveable { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            item { Text("账号", style = MaterialTheme.typography.headlineMedium) }
            when (val state = accountState) {
                is AccountState.SignedIn -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.profile.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(state.profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (state.profile.isVerified) "邮箱已验证" else "邮箱待验证", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("当前计划：免费", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { repository.signOut(); message = "已退出登录" }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Text("退出登录", modifier = Modifier.padding(start = 8.dp))
                        }
                        if (!state.profile.isVerified) {
                            TextButton(onClick = { isSubmitting = true; message = null }) {
                                Text("重新发送验证邮件")
                            }
                        }
                    }
                }
                AccountState.SignedOut -> {
                    item { Text(if (isRegister) "注册免费账号" else "登录账号", style = MaterialTheme.typography.titleLarge) }
                    if (isRegister) item {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("昵称") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("邮箱") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码（至少 8 位）") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Button(
                            enabled = !isSubmitting,
                            onClick = { isSubmitting = true; message = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (isRegister) "注册" else "登录") }
                    }
                    item {
                        TextButton(onClick = { isRegister = !isRegister; message = null }) {
                            Text(if (isRegister) "已有账号？去登录" else "没有账号？免费注册")
                        }
                    }
                    item {
                        TextButton(onClick = { isResetting = true; isSubmitting = true; message = null }) { Text("忘记密码") }
                    }
                }
            }
            message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error) } }
        }
    }

    LaunchedEffect(isSubmitting) {
        if (!isSubmitting) return@LaunchedEffect
        val result = when {
            isResetting -> repository.requestPasswordReset(email)
            email.isBlank() -> AccountResult.Failure("请输入邮箱")
            isRegister -> repository.register(email, password, displayName)
            else -> repository.signIn(email, password)
        }
        message = when (result) {
            is AccountResult.Success -> result.message ?: "操作成功"
            is AccountResult.Failure -> result.message
        }
        isResetting = false
        isSubmitting = false
    }

    LaunchedEffect(Unit) { repository.refresh() }
}
