/// <reference path="../pb_data/types.d.ts" />
migrate((app) => {
    const users = app.findCollectionByNameOrId("users");

    if (!users.fields.getByName("subscriptionStatus")) {
        users.fields.add(new TextField({
            name: "subscriptionStatus",
            required: false,
            max: 32,
            pattern: "^(free|active|pro|expired|canceled)?$",
        }));
    }

    if (!users.fields.getByName("subscriptionExpiresAt")) {
        users.fields.add(new TextField({
            name: "subscriptionExpiresAt",
            required: false,
            max: 64,
        }));
    }

    // Lock collection visibility strictly to the authenticated user's own record.
    users.listRule = "id = @request.auth.id";
    users.viewRule = "id = @request.auth.id";

    // Allow self-registration only when subscription fields are omitted or explicitly "free".
    users.createRule = '(@request.body.subscriptionStatus:isset = false || @request.body.subscriptionStatus = "" || @request.body.subscriptionStatus = "free") && @request.body.subscriptionExpiresAt:isset = false';

    // Prevent clients from mutating subscriptionStatus or subscriptionExpiresAt on profile updates.
    users.updateRule = "id = @request.auth.id && @request.body.subscriptionStatus:isset = false && @request.body.subscriptionExpiresAt:isset = false";
    users.deleteRule = "id = @request.auth.id";

    app.save(users);
}, (app) => {
    const users = app.findCollectionByNameOrId("users");

    users.listRule = "id = @request.auth.id";
    users.viewRule = "id = @request.auth.id";
    users.createRule = "";
    users.updateRule = "id = @request.auth.id";
    users.deleteRule = "id = @request.auth.id";

    app.save(users);
});
