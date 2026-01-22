# lesson Specification

## Purpose
The lesson spec defines the behavior and state management for interactive learning sessions where users practice vocabulary through trials. It covers lesson initialization, trial progression, and configuration options for customizing the learning experience.
## Requirements
### Requirement: Lesson state includes options object
The system SHALL create lesson state with an options object containing configuration settings.

#### Scenario: Lesson initialization with options
- **WHEN** creating initial lesson state
- **THEN** the state includes an `options` object with `trial-selector`
- **AND** `trial-selector` defaults to `:random` if not specified

### Requirement: Lesson advancement uses options
The system SHALL use trial-selector from lesson options when advancing to next trials.

#### Scenario: Trial advancement with options
- **WHEN** advancing to the next trial
- **THEN** the selection uses the `trial-selector` from lesson `options`
- **AND** maintains backward compatibility for lessons without options

