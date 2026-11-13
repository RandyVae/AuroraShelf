# Netgrey account service

This directory contains the deployment contract for the optional account service. PocketBase
runs beside the existing OTA helper and is reverse-proxied at `https://bananarey.us/auth-api`.

The mobile clients use the built-in `users` auth collection for email/password registration,
login, refresh, logout, verification and password reset. The `subscriptionStatus` and
`subscriptionExpiresAt` fields are reserved for a future billing integration; new accounts start
with `subscriptionStatus=free`.

The service is intentionally isolated from the existing `/api/*` OTA helper. If the account
service is stopped, anonymous video, live, comics, game and OTA flows continue to use their
existing endpoints.

## First-time administration

After deployment, create the first PocketBase superuser through the local admin UI using an SSH
port forward. Do not put that password in the repository. Configure SMTP before enabling email
verification or password-reset acceptance.

## Client API base

```text
https://bananarey.us/auth-api
```

PocketBase paths are appended below this base, for example:

```text
/api/collections/users/records
/api/collections/users/auth-with-password
/api/collections/users/request-verification
/api/collections/users/request-password-reset
```
