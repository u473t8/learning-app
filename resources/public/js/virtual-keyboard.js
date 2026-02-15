(function() {
    "use strict";

    if (!("virtualKeyboard" in navigator)) return;
    if (!navigator.virtualKeyboard) return;

    function updateOverlayMode() {
        // Enable overlay behavior on screens where we use `env(keyboard-inset-*)`.
        // This keeps layout stable and lets CSS lift UI above the keyboard.
        var managed = !!document.querySelector(".home") || !!document.querySelector(".lesson");

        try {
            navigator.virtualKeyboard.overlaysContent = managed;
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
