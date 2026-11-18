/// <reference path="../pb_data/types.d.ts" />

// Auto-verify newly created user records when external SMTP relay is not enabled,
// preventing accounts from getting stuck in an unverified state on hosts without port 25/sendmail.
onRecordCreateExecute((e) => {
    if (!e.app.settings().smtp.enabled && e.record) {
        e.record.setVerified(true);
    }
    e.next();
}, "users");

onRecordCreateRequest((e) => {
    e.next();
    if (!e.app.settings().smtp.enabled && e.record && !e.record.verified()) {
        e.record.setVerified(true);
        e.app.save(e.record);
    }
}, "users");

// When SMTP is not enabled, intercept verification email dispatch, mark the record verified,
// and avoid invoking local sendmail (which is absent on the VPS).
onMailerRecordVerificationSend((e) => {
    if (!e.app.settings().smtp.enabled) {
        if (e.record && !e.record.verified()) {
            e.record.setVerified(true);
            e.app.save(e.record);
        }
        return;
    }
    e.next();
}, "users");
