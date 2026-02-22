(() => {
    /**
     * Checks if the app is already installed and running as a standalone PWA.
     */
    const isStandalone = () => {
        return (
            window.navigator.standalone === true ||
            window.matchMedia('(display-mode: standalone)').matches ||
            window.matchMedia('(display-mode: fullscreen)').matches
        );
    };

    // Exit early if app is already installed
    if (isStandalone()) return;

    // Get the install button element
    const installButton = document.getElementById("install-button");

    // Exit if install button doesn't exist in the DOM
    if (!installButton) return;


    /**
     * Detect iOS Safari (iPhone/iPad/iPod, not in standalone mode).
     * Also catches iPad Safari identifying as Mac (post-iPadOS 13).
     */
    const isIOS = () => {
        return (
            /iphone|ipod|ipad/i.test(window.navigator.userAgent) ||
            (window.navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));
    };

    // --- Platform branching ---

    if (isIOS()) {
        const overlay = document.getElementById('app-install-guide');

        if (!overlay) return;

        // On iOS: show install button immediately — no beforeinstallprompt exists
        installButton.hidden = false;


        window.addEventListener("app:install-requested", () => {
            overlay.hidden = false;
        });

        window.addEventListener('app:guide-dismissed', (event) => {
            overlay.hidden = true;
        });


    } else {
        // Android / Chromium path — existing behavior
        let installPrompt = null;

        window.addEventListener("beforeinstallprompt", (event) => {
            event.preventDefault();
            installPrompt = event;
            installButton.hidden = false;
        });

        window.addEventListener("app:install-requested", () => {
            if (installPrompt) {
                installPrompt.prompt();
                installPrompt.userChoice.finally(() => {
                    installPrompt = null;
                });
            }
        });

        window.addEventListener("appinstalled", () => {
            installPrompt = null;
            installButton.hidden = true;
        });
    }
})();
