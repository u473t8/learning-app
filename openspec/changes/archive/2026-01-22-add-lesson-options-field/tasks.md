## 1. Create change proposal and spec deltas
- [x] Create proposal.md, tasks.md, design.md files
- [x] Create spec deltas for data-model and lesson capabilities
- [x] Run openspec validate to ensure deltas are correct

## 2. Update domain/lesson.cljs
- [x] Modify initial-state to store trial-selector in options object
- [x] Update advance and select-trial functions to read from options
- [x] Add backward compatibility for existing lessons without options

## 3. Update client/lesson.cljs
- [x] Ensure trial-selector is passed through to domain functions
- [x] Update any direct access to trial-selector field

## 4. Update tests
- [x] Fix domain/lesson_test.cljs function call signatures
- [x] Update test fixtures to use new options structure
- [x] Add tests for options field backward compatibility

## 5. Update application.cljs
- [x] Fix function call signatures and missing arguments
- [x] Ensure trial-selector is properly passed through

## 6. Migration strategy
- [x] Add migration logic for existing lesson documents (backward compatibility in advance function)
- [x] Update lesson loading to handle both old and new formats

## 7. Validation and cleanup
- [x] Run tests to ensure all changes work
- [x] Update any remaining references to old trial-selector field
- [x] Run openspec validate and archive the change