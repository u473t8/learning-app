# Lesson Page

## Goals

- Minimal, fast, and clear UI.
- Works well on slow networks and older devices.
- Reduce steps and friction.

## Typical structure

```
[:main {:class "lesson"}
 [:header {:class "lesson__header"}
  [:h1 {:class "lesson__title"} title]
  [:p {:class "lesson__subtitle"} subtitle]]
 [:section {:class "lesson__content" :id "lesson-content"}
  body]
 [:footer {:class "lesson__actions"}
  action-buttons]]
```

## Transitions

- Use CSS transitions on opacity/transform for content swaps.
- Add a loading modifier for htmx requests.
- Respect reduced motion.

```
.lesson__content {
  transition: opacity 200ms ease, transform 200ms ease;
}

.lesson__content.is-entering {
  opacity: 0;
  transform: translateY(6px);
}

@media (prefers-reduced-motion: reduce) {
  .lesson__content {
    transition: none;
  }
}
```
