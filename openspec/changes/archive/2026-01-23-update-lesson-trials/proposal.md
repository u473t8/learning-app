# Change: Update lesson trials and answer results

## Why
Lesson trials mix words and examples, and the lesson state no longer stores a separate words list. The answer-checking API now updates lesson state directly. The specs must align with the updated lesson shape and result contract.

## What Changes
- Define lesson trial schema using denormalized prompts and answers
- Remove the lesson :words field and document new lesson state fields
- Specify the answer-checking shape as updated lesson state with :last-result

## Impact
- Affected specs: lesson, data-model
- Affected code: src/client/domain/lesson.cljs, src/client/lesson.cljs, src/client/application.cljs
