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

1. **Collection Access Rules (`pb_migrations/1731900000_lock_users_rules.js`)**:
   - Applied automatically on startup via `--migrationsDir=/var/lib/netgrey-pocketbase/pb_migrations`.
   - Locks `users.listRule = "id = @request.auth.id"` and `users.viewRule = "id = @request.auth.id"` so unauthenticated or cross-user enumeration is impossible.
   - Enforces `@request.body.subscriptionStatus` and `@request.body.subscriptionExpiresAt` guards in `createRule` and `updateRule` so clients can never self-escalate subscription tiers (`new` accounts start as `free`).

2. **Nginx Explicit Allowlist (`netgrey-auth-location.conf`)**:
   - Exposes **only** `/auth-api/api/health` and `/auth-api/api/collections/users/(records|auth-with-password|auth-refresh|request-verification|confirm-verification|request-password-reset|confirm-password-reset)`.
   - Blocks `/auth-api/_` (with or without trailing slash), `/api/collections/_superusers`, `/api/admins`, `/api/backups`, `/api/settings`, `/api/logs`, and any non-allowlisted `/auth-api/*` path with `404`.
   - Blocks sensitive static extensions (`\.(json|py|sh|bak|old|tmp)$`) in the web root while explicitly allowing `/source-manifest.json`.

3. **Systemd Sandboxing (`netgrey-pocketbase.service`)**:
   - Runs as dedicated system user `netgrey-auth` with `ProtectSystem=strict`, `ProtectHome=true`, `PrivateTmp=true`, `PrivateDevices=true`, `ProtectKernelTunables=true`, `RestrictSUIDSGID=true`, `NoNewPrivileges=true`, and `ReadWritePaths=/var/lib/netgrey-pocketbase`.

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
