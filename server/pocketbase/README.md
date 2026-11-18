# Netgrey account service

This directory contains the deployment contract for the isolated account service. PocketBase runs
beside the OTA helper on `127.0.0.1:8090` and is reverse-proxied through an explicit Nginx
allowlist at `https://bananarey.us/auth-api`.

The mobile clients (`AccountRepository.kt` on Android and `AccountService.swift` on iOS) use the
built-in `users` auth collection for email/password registration, login, session refresh, logout,
email verification, and password reset. Session tokens are stored locally using hardware-backed
encryption (Android Keystore AES-GCM and iOS Keychain).

The service is isolated from the `/api/*` OTA helper and anonymous content modules: if the account
service is unavailable, anonymous video, live, comics, forum, built-in game, and OTA flows continue
operating independently.

## Security & Hardening Contract

1. **Collection Access Rules & App Meta (`pb_migrations/`)**:
   - `1731900000_lock_users_rules.js`: Locks `users.listRule = "id = @request.auth.id"` and `users.viewRule = "id = @request.auth.id"` so unauthenticated or cross-user enumeration is impossible; enforces `@request.body.subscriptionStatus` and `@request.body.subscriptionExpiresAt` guards in `createRule` and `updateRule` so clients can never self-escalate subscription tiers (`new` accounts start as `free`).
   - `1731900001_configure_app_meta_and_verification.js`: Configures `meta` (`appName: "Netgrey"`, `appURL: "https://bananarey.us/auth-api"`, `senderName: "Netgrey"`, `senderAddress: "noreply@bananarey.us"`), enables `trustedProxy` (`X-Real-IP`) and `rateLimits`, and marks existing unverified accounts as verified when external SMTP is not enabled.

2. **Auto-Verification Hook Without External SMTP (`pb_hooks/main.pb.js`)**:
   - Loaded via `--hooksDir=/var/lib/netgrey-pocketbase/pb_hooks`.
   - When `settings.smtp.enabled` is `false` (outbound port 25 blocked on VPS), automatically marks newly registered accounts `verified = true` and intercepts `onMailerRecordVerificationSend` so `POST /api/collections/users/request-verification` succeeds (`204`) and verifies the account without invoking local `sendmail`. When external SMTP is enabled in settings, normal verification emails are dispatched.

3. **Nginx Explicit Allowlist (`netgrey-auth-location.conf`)**:
   - Exposes **only** `/auth-api/api/health` and `/auth-api/api/collections/users/(records|auth-with-password|auth-refresh|request-verification|confirm-verification|request-password-reset|confirm-password-reset)`.
   - Blocks `/auth-api/_` (with or without trailing slash), `/api/collections/_superusers`, `/api/admins`, `/api/backups`, `/api/settings`, `/api/logs`, and any non-allowlisted `/auth-api/*` path with `404`.
   - Blocks sensitive static extensions (`\.(json|py|sh|bak|old|tmp)$`) in the web root while explicitly allowing `/source-manifest.json`.

4. **Systemd Sandboxing & Superuser Bootstrap (`netgrey-pocketbase.service` & `scripts/deploy_auth_backend.sh`)**:
   - Runs as dedicated system user `netgrey-auth` with `ProtectSystem=strict`, `ProtectHome=true`, `PrivateTmp=true`, `PrivateDevices=true`, `ProtectKernelTunables=true`, `RestrictSUIDSGID=true`, `NoNewPrivileges=true`, and `ReadWritePaths=/var/lib/netgrey-pocketbase`.
   - `scripts/deploy_auth_backend.sh` automatically provisions a CSPRNG superuser credential in `/etc/netgrey-ota/pocketbase_admin.json` (`0600`) and runs `pocketbase superuser upsert`, permanently disabling the first-time `pbinstall` installer token.

## First-time administration

Create or manage PocketBase superusers only over an SSH local port forward (`ssh -L 8090:127.0.0.1:8090 ...`) to `http://127.0.0.1:8090/_/`. Never expose `/_/` publicly or commit admin credentials to the repository. Configure SMTP in local admin settings before enabling mandatory email verification or password-reset emails.

## Allowed Public Client Endpoints

Base URL: `https://bananarey.us/auth-api`

```text
GET  /api/health
POST /api/collections/users/records
GET  /api/collections/users/records/<id>
PATCH /api/collections/users/records/<id>
DELETE /api/collections/users/records/<id>
POST /api/collections/users/auth-with-password
POST /api/collections/users/auth-refresh
POST /api/collections/users/request-verification
POST /api/collections/users/confirm-verification
POST /api/collections/users/request-password-reset
POST /api/collections/users/confirm-password-reset
```
