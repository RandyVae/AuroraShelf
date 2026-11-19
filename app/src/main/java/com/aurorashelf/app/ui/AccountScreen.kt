@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.aurorashelf.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.data.AccountRepository
import com.aurorashelf.app.data.AccountResult
import com.aurorashelf.app.data.AccountState
import com.aurorashelf.app.ui.theme.AuroraHaptics

@Composable
internal fun AccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val repository = remember(context) { AccountRepository.get(context) }
    val accountState by repository.state.collectAsStateWithLifecycle()
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isResetting by rememberSaveable { mutableStateOf(false) }
    var isVerifying by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("账号") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            AuroraHaptics.tap(haptic)
                            onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "收藏、观看历史与离线缓存均保存在本机私有空间，匿名浏览无需登录账号；账号仅用于后续跨端身份权益。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            when (val state = accountState) {
                is AccountState.SignedIn -> item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(18.dp),
                        ) {
                            Text(state.profile.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(state.profile.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AuroraBadge(
                                text = if (state.profile.isVerified) "邮箱已验证 · 免费计划" else "邮箱待验证 · 免费计划",
                            )
                            Button(
                                onClick = {
                                    AuroraHaptics.confirm(haptic)
                                    repository.signOut()
                                    isErrorMessage = false
                                    message = "已退出登录"
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                Text("退出登录", modifier = Modifier.padding(start = 8.dp))
                            }
                            if (!state.profile.isVerified) {
                                OutlinedButton(
                                    enabled = !isSubmitting,
                                    onClick = {
                                        AuroraHaptics.tap(haptic)
                                        isVerifying = true
                                        isSubmitting = true
                                        message = null
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text("重新发送验证邮件")
                                }
                            }
                        }
                    }
                }
                AccountState.SignedOut -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(18.dp),
                            ) {
                                Text(
                                    if (isRegister) "注册免费账号" else "登录账号",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (isRegister) {
                                    OutlinedTextField(
                                        value = displayName,
                                        onValueChange = { displayName = it },
                                        label = { Text("昵称") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                        singleLine = true,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("邮箱") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("密码（至少 8 位）") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                            )
                                        }
                                    },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    enabled = !isSubmitting,
                                    onClick = {
                                        AuroraHaptics.tap(haptic)
                                        isSubmitting = true
                                        message = null
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                ) {
                                    if (isSubmitting) {
                                        AuroraLoadingIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    } else {
                                        Text(if (isRegister) "注册" else "登录")
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    TextButton(
                                        onClick = {
                                            AuroraHaptics.segmentTick(haptic)
                                            isRegister = !isRegister
                                            message = null
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Text(if (isRegister) "已有账号？去登录" else "没有账号？免费注册")
                                    }
                                    TextButton(
                                        enabled = !isSubmitting,
                                        onClick = {
                                            if (email.isBlank()) {
                                                AuroraHaptics.reject(haptic)
                                                isErrorMessage = true
                                                message = "请先在邮箱栏填写注册邮箱，再点击忘记密码"
                                            } else {
                                                AuroraHaptics.tap(haptic)
                                                isResetting = true
                                                isSubmitting = true
                                                message = null
                                            }
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Text("忘记密码")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            message?.let { text ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isErrorMessage) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (isErrorMessage) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isSubmitting) {
        if (!isSubmitting) return@LaunchedEffect
        val result = when {
            isVerifying -> repository.requestVerification()
            isResetting -> repository.requestPasswordReset(email)
            email.isBlank() -> AccountResult.Failure("请输入邮箱")
            isRegister -> repository.register(email, password, displayName)
            else -> repository.signIn(email, password)
        }
        when (result) {
            is AccountResult.Success -> {
                AuroraHaptics.confirm(haptic)
                isErrorMessage = false
                message = result.message ?: "操作成功"
            }
            is AccountResult.Failure -> {
                AuroraHaptics.reject(haptic)
                isErrorMessage = true
                message = result.message
            }
        }
        isVerifying = false
        isResetting = false
        isSubmitting = false
    }

    LaunchedEffect(Unit) {
        if (repository.state.value is AccountState.SignedIn) {
            repository.refresh()
        }
    }
}
