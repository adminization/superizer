// Chrome's sandbox needs unprivileged user namespaces, and CI runners disable them.
//
// GitHub's ubuntu-24.04 image blocks them through AppArmor, so ChromeHeadless dies at startup with
// "No usable sandbox!" and Karma gives up — which Gradle then reports as "did not discover any
// tests", three layers away from the cause and costing an hour to trace. The sandbox is protection
// against the *page*, and the page here is our own test bundle on a throwaway runner.
//
// Applies everywhere rather than under an `if (process.env.CI)`: a local run that passes with a
// different browser configuration than CI's is a local run that proves less than it appears to.
config.set({
    browsers: ['ChromeHeadlessNoSandbox'],
    customLaunchers: {
        ChromeHeadlessNoSandbox: {
            base: 'ChromeHeadless',
            // --disable-dev-shm-usage: a container's /dev/shm is 64 MB by default, and Chrome
            // crashes part-way through a large wasm bundle rather than saying so.
            flags: ['--no-sandbox', '--disable-dev-shm-usage'],
        },
    },
});
