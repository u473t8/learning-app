# UI Design Principles

Design principles for Sprecha, inspired by Duolingo's approach to language learning apps.

## Core Philosophy

### 1. Fun & Motivation First
We're not just an education app—we're a motivation app. Learning should feel like play, not work.

- Use vibrant colors and rounded elements ("juicy" design)
- Add playful micro-interactions and animations
- Celebrate progress with visual feedback

### 2. Effortless Experience
Users should never need instructions. The UI must be self-explanatory.

- No tutorial screens or instruction manuals
- Use tooltips contextually, not upfront
- Empty states should explain themselves with illustrations and minimal copy
- Progressive disclosure—reveal features as needed

### 3. Play First, Profile Second
Demonstrate value before asking for commitment.

- Let users experience the app before requiring signup
- Guest/offline mode is a first-class citizen
- Account creation should be minimal (3 screens max)
- Show progress earned before asking to save it

## Visual Design

### Color System
- **Primary**: Vibrant, energetic brand color
- **Semantic colors**: Success (green), warning (orange), error (red)
- Use abstract color naming in code (e.g., `color-success` not `color-green`)
- Ensure WCAG AA contrast ratios

### Typography
- Clear hierarchy: title, subtitle, body, caption
- Readable on small screens (min 16px body text)
- Support for longer Russian/German text

### Illustrations & Icons
- Geometric shapes (circles, squares, triangles)
- Consistent style across all illustrations
- Playful but professional
- Icons should be self-explanatory

### Motion & Animation
- Use animation to provide feedback, not decoration
- Celebrate achievements with micro-animations
- Keep animations short (150-300ms)
- Respect reduced-motion preferences

## Component Patterns

### Buttons
- Large touch targets (min 44x44px)
- Clear visual hierarchy: primary, secondary, text
- Unadorned, flat design
- Obvious tap states

### Progress Indicators
- Show progress everywhere it makes sense
- Use filled shapes (circles, bars) for completion
- Celebrate milestones visually

### Forms
- Minimal fields
- Inline validation
- Clear error states with helpful messages
- Auto-focus first field

### Empty States
- Illustration + short explanation
- Clear call-to-action
- Never leave users wondering "what now?"

### Modals & Overlays
- Use sparingly
- Contextual—appear when relevant
- Easy to dismiss
- Full-screen on mobile for important flows

## Gamification Elements

### Retention Indicators
- Visual progress (color-coded dots/bars)
- Descriptive labels ("Well learned", "Needs review")
- Hover/tap for details

### Streaks & Habits
- Daily engagement tracking
- Visual streak counter
- Celebration animations for milestones
- Gentle reminders, not guilt

### Rewards & Feedback
- Immediate feedback on actions
- XP or points for completed tasks
- Achievement notifications
- Progress summaries

## Mobile-First Design

### Thumb Zones
Reference: `docs/ui/thumb-zones.png`

- Primary actions in bottom 1/3 of screen
- Navigation at bottom or top edges
- Avoid center-screen for frequent taps
- Consider one-handed use

### Touch Targets
- Minimum 44x44px tap targets
- Adequate spacing between interactive elements
- No hover-only interactions
- Visible focus states for accessibility

### Responsive Considerations
- Design for 320px minimum width
- Test with longer translations (Russian, German)
- Flexible layouts, not fixed widths
- Consider both portrait and landscape

## Design Process

### 1. Start with User Behavior
Ground decisions in real user behavior, not opinions. Data does the talking.

### 2. Ship Quality Early
Aim for excellence from day one. Ship polished features, not rough MVPs.

### 3. Iterate Rapidly
Move fast, learn faster. Test assumptions with real users.

### 4. Keep It Focused
Ruthless prioritization. Cut low-impact features. Less is more.

## Anti-Patterns to Avoid

- ❌ Tutorial screens on first launch
- ❌ Mandatory signup before experiencing value
- ❌ Hover-only interactions
- ❌ Small touch targets
- ❌ Unexplained icons or UI elements
- ❌ Loading spinners for local operations
- ❌ Complex multi-step forms
- ❌ Guilt-inducing notifications
- ❌ Feature bloat
