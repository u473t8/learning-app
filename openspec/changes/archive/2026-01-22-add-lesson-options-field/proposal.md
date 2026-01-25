# Change: Add lesson options field

## Why
The current lesson document stores `trial-selector` directly, mixing configuration options with lesson state. We need a cleaner structure that groups configuration options together and allows for future extensibility (difficulty levels, time limits, etc.).

## What Changes
- Add `options` object to lesson document schema containing `trial-selector`
- Update code to read/write `trial-selector` from `options.trial-selector`
- Update data-model spec with new lesson document structure
- **BREAKING:** Changes persisted lesson document format

## Impact
- Affected specs: data-model, lesson
- Affected code: src/client/domain/lesson.cljs, src/client/lesson.cljs, tests
- Migration needed for existing lesson documents with old structure