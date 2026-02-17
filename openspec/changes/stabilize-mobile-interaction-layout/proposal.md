## Why

Home page interactions on mobile currently cause visible layout instability: the composer shakes when the keyboard opens, elements briefly disappear/reappear during focus changes, and submit interactions can flicker. This breaks perceived quality and slows down word entry.

## What Changes

- Stabilize mobile composer geometry so keyboard open/close and focus transitions do not cause convulsive relayout.
- Make add-word submit interaction visually stable (no rapid press/disabled/normal flicker).
- Reduce HTMX-driven DOM churn so only minimal fragments update during typing and submit.
- Prevent avoidable horizontal overflow on home, lesson, and vocabulary views.
- Keep major layout blocks persistent during interactions (no brief removal/reappearance in normal flow).

## Capabilities

### New Capabilities
- `ui-interaction-stability`: Defines visual stability, minimal HTMX update scope, and scroll/overflow constraints for interactive mobile flows.

### Modified Capabilities
- _(none)_

## Impact

- Affected code: `src/client/views/home.cljs`, `src/client/application.cljs`, `resources/public/css/blocks/home.css`, `resources/public/css/blocks/vocabulary.css`, `resources/public/css/blocks/lesson.css`, `resources/public/css/base/reset.css`, `resources/public/js/word-autocomplete.js`.
- Affected behavior: mobile keyboard/focus transitions, submit-state rendering, autocomplete update cadence, and layout overflow handling.
- Validation focus: manual mobile checks for keyboard transitions, focus switch stability, submit stability, and absence of horizontal scroll.
