## 1. Lesson schema
- [x] 1.1 Update lesson state schema for denormalized trials
- [x] 1.2 Remove words array from lesson state
- [x] 1.3 Define trial object fields (:prompt, :answer, :type, :word-id)

## 2. Answer checking
- [x] 2.1 Specify lesson-state result payload
- [x] 2.2 Update lesson answer handling to use :last-result

## 3. Data model
- [x] 3.1 Add data-model capability spec for lesson documents
- [x] 3.2 Sync openspec/specs/data-model/spec.md after spec approval

## 4. Implementation
- [x] 4.1 Update domain.lesson trial generation and answer checks
- [x] 4.2 Update client lesson flow and UI bindings
- [x] 4.3 Update any tests that assert lesson schema
