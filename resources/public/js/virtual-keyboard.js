(function() {
    "use strict";

    if (!("virtualKeyboard" in navigator)) return;
    if (!navigator.virtualKeyboard) return;

    function updateOverlayMode() {
        // Only enable overlay behavior on the home page.
        var onHome = !!document.querySelector(".home");

        try {
            navigator.virtualKeyboard.overlaysContent = onHome;
        } catch (_err) {
            // Ignore failures (unsupported, non-secure context, etc.).
        }
    }

    // Initial run.
    updateOverlayMode();

    // HTMX navigations replace #app; refresh overlay mode after swaps.
    document.body.addEventListener("htmx:afterSwap", updateOverlayMode);
    window.addEventListener("pageshow", updateOverlayMode);
})();
