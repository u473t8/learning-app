## Context

The app uses Hiccup + HTMX with BEM CSS and minimal custom JS. The current home mobile composer combines state-dependent CSS changes, keyboard-sensitive viewport math, and frequent HTMX swaps. During focus and submit, this creates visible jumps and brief flashes of unrelated controls (for example, `НАЧАТЬ УРОК`).

## Goals / Non-Goals

**Goals:**
- Keep composer and surrounding layout visually stable while keyboard is open.
- Ensure submit interactions update only required fragments and avoid visual flicker.
- Eliminate avoidable horizontal scrolling in interactive views.
- Preserve current feature set and user flows.

**Non-Goals:**
- Redesigning page visual language or component styling.
- Replacing HTMX with a client-side state framework.
- Adding heavy animation frameworks or new runtime dependencies.

## Decisions

### 1) Prefer geometry-stable layout primitives on mobile
- Use sticky/flow positioning with safe-area offsets for composer regions.
- Avoid `vh`-driven fixed geometry for active composer areas when keyboard can resize viewport.
- Keep component heights bounded by container constraints rather than viewport-width formulas.

### 2) Keep layout structure invariant during interaction states
- Remove state rules that switch major blocks with `display: none` during normal typing/submitting.
- Keep form shell and sibling controls mounted; only update content/value/state where needed.
- Avoid rules that toggle overflow modes based on rapidly changing child content unless strictly necessary.

### 3) Minimize HTMX churn to smallest practical scope
- Keep add-word submit as local interaction (`hx-swap="none"` with no page-shell mutations).
- Restrict dynamic updates to targeted fragments (suggestion list and validation OOB fields only).
- Tune input trigger delays to reduce rapid repaint cycles without harming responsiveness.

### 4) Stabilize request-state visuals for submit controls
- Preserve anti-double-submit behavior (`disabled` or pointer lock), but avoid request-state style transitions that alter button geometry or perceived color jumps.
- Treat request state as functional lock, not a dramatic visual mode switch.

### 5) Enforce overflow safety defaults
- Add base safeguards against horizontal overflow at root level.
- Replace viewport-width-based control sizing with container-relative sizing + max width where possible.

## Risks / Trade-offs

- **Risk:** Over-constraining overflow can hide legitimate content in edge cases.  
  **Mitigation:** Keep intended vertical scroll zones explicit and verify long-text scenarios.

- **Risk:** Reducing visual request-state feedback may make submit feel less obvious.  
  **Mitigation:** Retain clear pressed state and disabled semantics while removing disruptive transitions.

- **Risk:** Longer autocomplete debounce can feel less immediate for fast typists.  
  **Mitigation:** Keep delay moderate and verify typing cadence manually on mobile.

- **Risk:** Sticky/fixed changes may affect desktop layout unexpectedly.  
  **Mitigation:** Scope adjustments to mobile breakpoints and verify desktop parity.

## Migration Plan

1. Introduce overflow and sizing safety changes that are layout-preserving.
2. Stabilize home mobile composer positioning and remove state-driven major relayout toggles.
3. Limit add-word submit to local updates and neutral request-state visual churn.
4. Tune autocomplete update cadence and ensure no late-response flash after submit/reset.
5. Run mobile-focused manual verification (keyboard open, focus switch, repeated submit, no horizontal scroll).

Rollback: revert changed CSS blocks and home/application view handlers as a single unit if regressions appear.

## Open Questions

- Should the same stability constraints be applied immediately to vocabulary footer interactions, or follow as a separate change?
- Do we want a reusable "interaction-stable submit button" pattern for other forms beyond home?
