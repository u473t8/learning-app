# htmx in Hiccup

## Hiccup attribute patterns

- Prefer explicit attrs maps: `[:div {:class "block"}]`.
- Use `:id` only when needed for htmx targets; otherwise class-based targeting.
- Avoid inline HTML strings.

## Common htmx attributes

- `:hx-get`, `:hx-post`, `:hx-put`, `:hx-delete`
- `:hx-trigger` (e.g. "click", "keyup changed delay:300ms")
- `:hx-target` (CSS selector or "this")
- `:hx-swap` (e.g. "outerHTML", "innerHTML", "beforeend")
- `:hx-indicator` for loading state
- `:hx-boost` on links when needed

## Example snippet

```
[:button {
  :class "lesson__action"
  :hx-post "/lesson/next"
  :hx-target "#lesson-content"
  :hx-swap "outerHTML"
  :hx-indicator ".lesson__spinner"}
 "Continue"]
```