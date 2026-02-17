## Purpose
Define interaction-time UI stability requirements for mobile-first flows so keyboard, focus, and HTMX updates do not cause disruptive relayouts.

## ADDED Requirements

### Requirement: Composer remains stable during mobile keyboard and focus changes
The system SHALL keep the home add-word composer visually stable when the mobile keyboard opens/closes and when focus moves between word and translation inputs.

#### Scenario: Focus switch does not cause composer disappearance
- **WHEN** the keyboard is open and focus moves from the word input to the translation input
- **THEN** the add-word form remains visible without brief removal/reappearance
- **AND** sibling primary actions do not flash into view for a single frame

#### Scenario: Keyboard resize does not cause convulsive jumps
- **WHEN** the mobile visual viewport changes because of keyboard open/close
- **THEN** the composer position updates smoothly without abrupt vertical jumps

### Requirement: Submit interaction avoids visual flicker
The system SHALL keep submit-control visuals stable during add-word request lifecycle.

#### Scenario: Submit button request lock without disruptive restyle
- **WHEN** user submits a valid new word
- **THEN** the submit button enters a request-locked state that prevents double submit
- **AND** the button does not rapidly oscillate between visually distinct states that appear as flicker

### Requirement: HTMX updates are localized during home add flow
The system SHALL update only minimal required DOM fragments during typing and submit on home add flow.

#### Scenario: Submit does not trigger page-shell mutation
- **WHEN** user submits add-word form successfully
- **THEN** the update is limited to form-local behavior and required data refreshes
- **AND** the app shell (`#app`) is not replaced as part of this interaction

#### Scenario: Autocomplete updates only suggestion target
- **WHEN** user types in the word input
- **THEN** only the suggestion list target is swapped
- **AND** surrounding layout containers remain mounted and stable

### Requirement: Horizontal overflow is prevented in interactive views
The system SHALL avoid horizontal scrolling in normal interaction scenarios for home, lesson, and vocabulary views.

#### Scenario: No horizontal scroll during mobile interaction
- **WHEN** user types, focuses inputs, and submits forms on mobile widths
- **THEN** no horizontal scrollbar appears on the page
- **AND** text inputs and action controls fit within their containers

### Requirement: State changes avoid major layout mode toggles
The system SHALL avoid interaction-time state transitions that remove or reinsert major layout blocks for routine add/typing flows.

#### Scenario: Routine state updates keep layout skeleton intact
- **WHEN** interaction state changes (typing, submitting, clearing form)
- **THEN** major layout blocks keep their structural presence
- **AND** visual updates are applied through localized or non-disruptive state changes
