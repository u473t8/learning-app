# BEM CSS

## Naming

- `block` for the component root.
- `block__element` for children.
- `block--modifier` for variants.
- Keep block names short and domain-specific.

## Structure

- Use a single block per component.
- Favor layout on the block or key elements.
- Use modifiers for state, not separate blocks.

## Example

```
.lesson {}
.lesson__header {}
.lesson__title {}
.lesson__content {}
.lesson__actions {}
.lesson--loading {}

.lesson--loading .lesson__content {
  opacity: 0.6;
}
```
