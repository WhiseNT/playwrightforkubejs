// Copy this file into kubejs/client_scripts to verify the core API.
Playwright.run("playwright-for-kubejs-status-smoke", function (testPage) {
    return testPage.status().ready(120000).then(function () {
        return testPage.status().all().then(function (snapshot) {
            return Playwright.expect(snapshot).toBeTruthy().then(function () {
                console.info("[Playwright For KubeJS] client status query passed");
                return snapshot;
            });
        });
    });
}).then(function (result) {
    console.info("[Playwright For KubeJS] smoke test completed");
}).catchError(function (error) {
    console.error("[Playwright For KubeJS] smoke test failed: " + error);
});
