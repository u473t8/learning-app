---
name: ui-builder
description: Build UI with Hiccup markup and htmx attributes, plus BEM CSS styles. Use when implementing or updating UI components, the lesson page, or transition animations in this project, especially when output must be Hiccup + htmx + BEM.
---

# UI Builder

## Overview

Implement UI for this project using Hiccup markup with htmx attributes and BEM CSS. Focus on minimal, fast, accessible UI that matches the product philosophy and avoids unnecessary JS.

## Workflow

1. Always invoke `gh-task-executor` to obtain the shaped task context.
2. Confirm the UI goal (component view, lesson page, or transition animation) and required htmx interactions.
3. Draft semantic Hiccup structure with htmx attributes and BEM class names.
4. Add BEM CSS for layout, visuals, and motion. Respect reduced motion.
5. Provide both markup and CSS in the response with short rationale if needed.

## References

- For UI implementation principles, read `docs/ui-implementation-principles.md`.
- For htmx in Hiccup and attribute patterns, read `references/htmx-hiccup.md`.
- For BEM CSS conventions and examples, read `references/bem-css.md`.
- For lesson page structure and transitions, read `references/lesson-page.md`.
- For project philosophy, read `docs/philosophy.md`
