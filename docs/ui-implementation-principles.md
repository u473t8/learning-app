# UI Implementation Principles

This document captures the UI implementation principles for this project.

## Goals

- Keep UI minimal, fast, and easy to understand.
- Prefer clarity over cleverness.
- Avoid JS unless required by htmx limitations.

## Markup (Hiccup)

- Use explicit attrs maps and semantic elements.
- Avoid raw HTML strings.
- Keep structure shallow and readable.

## Interactions (htmx)

- Prefer htmx attributes over custom JS.
- Keep targets local and explicit.
- Use `hx-indicator` for loading state.

## Styling (BEM)

- One block per component.
- Use `block__element` and `block--modifier` consistently.
- Keep block names short and domain-specific.

## Motion

- Use CSS transitions for state changes.
- Keep durations short and subtle.
- Respect `prefers-reduced-motion`.

## Accessibility

- Preserve semantic hierarchy and focus order.
- Ensure text remains readable at small sizes.
- Provide visible focus states when interactive.
