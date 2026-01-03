(function() {
    "use strict";

    if (!("serviceWorker" in navigator)) return;

    function pingWorker(sw) {
        return new Promise(function(resolve, reject) {
            function onMessage(event) {
                if (event.data && event.data.type === "pong") {
                    navigator.serviceWorker.removeEventListener("message", onMessage);
                    resolve();
                }
            }

            navigator.serviceWorker.addEventListener("message", onMessage);
            sw.postMessage({
                type: "ping"
            });
        });
    }

    navigator.serviceWorker
        .register("/js/app/sw.js", {
            scope: "/"
        })
        .then(function() {
            return navigator.serviceWorker.ready;
        })
        .then(function(reg) {
            // ensure the service worker is awake; fetch may not reliably wake an idle worker
            return pingWorker(reg.active);
        })
        .then(function() {
            location.replace("/home");
        })
        .catch(function(error) {
            console.warn("[sw-loader] registration failed", error);
        });
})();