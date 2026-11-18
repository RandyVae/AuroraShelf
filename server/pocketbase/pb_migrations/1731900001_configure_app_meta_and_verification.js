/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
    const settings = app.settings();

    settings.meta.appName = "Netgrey";
    settings.meta.appURL = "https://bananarey.us/auth-api";
    settings.meta.senderName = "Netgrey";
    settings.meta.senderAddress = "noreply@bananarey.us";
    settings.meta.hideControls = false;

    settings.trustedProxy.headers = ["X-Real-IP"];
    settings.trustedProxy.useLeftmostIP = false;
    settings.rateLimits.enabled = true;

    app.save(settings);

    if (!settings.smtp.enabled) {
        const unverifiedUsers = app.findAllRecords("users", $dbx.hashExp({ verified: false }));
        for (const user of unverifiedUsers) {
            if (user) {
                user.setVerified(true);
                app.save(user);
            }
        }
    }
}, (app) => {
    // Keep production branding and verified accounts intact on rollback.
});
