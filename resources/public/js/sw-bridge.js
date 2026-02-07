(function() {
    "use strict";
    if (!navigator.serviceWorker) return;

    var APPLY_UPDATE_TIMEOUT_MS = 15000;
    var applyUpdateTimeoutId = null;

    function showUpdateVeil() {
        var veil = document.getElementById("sw-update-veil");
        if (veil) veil.classList.add("sw-update-veil--active");
    }

    function hideUpdateVeil() {
        var veil = document.getElementById("sw-update-veil");
        if (veil) veil.classList.remove("sw-update-veil--active");
    }

    function clearApplyUpdateTimeout() {
        if (applyUpdateTimeoutId !== null) {
            clearTimeout(applyUpdateTimeoutId);
            applyUpdateTimeoutId = null;
        }
    }

    function armApplyUpdateTimeout() {
        clearApplyUpdateTimeout();
        applyUpdateTimeoutId = setTimeout(function() {
            hideUpdateVeil();
            document.body.dispatchEvent(new CustomEvent("sw-update-timeout"));
        }, APPLY_UPDATE_TIMEOUT_MS);
    }

    // Direct update trigger from button click
    window.applySwUpdate = function() {
        showUpdateVeil();
        armApplyUpdateTimeout();

        navigator.serviceWorker.getRegistration().then(function(r) {
            if (r && r.waiting) {
                r.waiting.addEventListener("statechange", function() {
                    if (this.state === "redundant") {
                        clearApplyUpdateTimeout();
                        hideUpdateVeil();
                    }
                });
                r.waiting.postMessage({
                    type: "SKIP_WAITING"
                });
            } else if (r && r.installing) {
                // Race: a newer SW started installing, evicting the one that was waiting.
                // Wait for it to finish installing, then skip.
                r.installing.addEventListener("statechange", function() {
                    if (this.state === "installed") {
                        this.postMessage({
                            type: "SKIP_WAITING"
                        });
                    } else if (this.state === "redundant") {
                        clearApplyUpdateTimeout();
                        hideUpdateVeil();
                    }
                });
            } else {
                // Nothing to update; dismiss veil.
                clearApplyUpdateTimeout();
                hideUpdateVeil();
            }
        }).catch(function() {
            clearApplyUpdateTimeout();
            hideUpdateVeil();
        });
    };

    // New SW took control; reload into fresh version.
    navigator.serviceWorker.addEventListener("controllerchange", function() {
        clearApplyUpdateTimeout();
        location.reload();
    });

    // Detect new SW reaching "installed" state and show update toast.
    navigator.serviceWorker.getRegistration().then(function(r) {
        if (!r) return;
        r.addEventListener("updatefound", function() {
            var sw = r.installing;
            if (sw) {
                sw.addEventListener("statechange", function() {
                    if (sw.state === "installed" && navigator.serviceWorker.controller) {
                        document.body.dispatchEvent(new CustomEvent("sw-update-available"));
                    }
                });
            }
        });
    });
})();
