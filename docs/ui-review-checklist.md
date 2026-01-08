# UI/UX Review Checklist

Use this checklist when reviewing UI/UX implementations for Sprecha.

## 1. Core Experience

### Value Delivery
- [ ] Can users experience core value without signing up?
- [ ] Does offline mode work fully?
- [ ] Is the happy path obvious and frictionless?

### Self-Explanatory UI
- [ ] Can a new user understand the UI without instructions?
- [ ] Are empty states helpful (illustration + explanation + CTA)?
- [ ] Do icons have labels or are they universally understood?
- [ ] Is microcopy clear and action-oriented?

### Feedback & Response
- [ ] Does every action have immediate visual feedback?
- [ ] Are loading states appropriate (or eliminated)?
- [ ] Are success/error states clear and helpful?
- [ ] Do animations feel responsive (150-300ms)?

## 2. Mobile Usability

### Touch Targets
- [ ] All interactive elements ≥ 44x44px
- [ ] Adequate spacing between tap targets (≥ 8px)
- [ ] No tiny close buttons or links

### Thumb Zones (see `docs/thumb-zones.png`)
- [ ] Primary actions in bottom 1/3 of screen
- [ ] Frequent actions reachable with one hand
- [ ] Navigation at screen edges (top/bottom)
- [ ] Destructive actions require deliberate reach

### No Hover Dependencies
- [ ] All functionality works without hover
- [ ] Tooltips accessible via tap (not hover-only)
- [ ] No hover-reveal menus or actions

## 3. Visual Consistency

### Color Usage
- [ ] Consistent semantic colors (success, warning, error)
- [ ] Sufficient contrast (WCAG AA: 4.5:1 for text)
- [ ] Color not the only indicator (icons, text, patterns)

### Typography
- [ ] Consistent type scale throughout
- [ ] Body text ≥ 16px
- [ ] Clear visual hierarchy (title > subtitle > body)
- [ ] Line height comfortable for reading (1.4-1.6)

### Spacing & Layout
- [ ] Consistent spacing rhythm
- [ ] Adequate padding inside containers
- [ ] Aligned elements (no visual "jaggies")
- [ ] Responsive—works at 320px width

### Component Consistency
- [ ] Buttons look consistent across the app
- [ ] Form inputs have unified styling
- [ ] Cards/panels have consistent treatment
- [ ] Icons are from the same family/style

## 4. Gamification & Progress

### Progress Visibility
- [ ] Users can see their progress clearly
- [ ] Retention/mastery indicators are understandable
- [ ] Progress feels rewarding, not punishing

### Feedback & Rewards
- [ ] Achievements are celebrated visually
- [ ] Positive actions get positive feedback
- [ ] Failures are handled gently (not guilt-inducing)

### Streaks & Habits
- [ ] Streak counter is visible but not anxiety-inducing
- [ ] Missed days handled gracefully
- [ ] Encouragement over guilt

## 5. Accessibility

### Visual Accessibility
- [ ] Color contrast meets WCAG AA (4.5:1 text, 3:1 UI)
- [ ] Text resizable without breaking layout
- [ ] Focus indicators visible and clear
- [ ] No flashing/strobing animations

### Screen Reader Support
- [ ] Semantic HTML structure (headings, landmarks)
- [ ] Images have alt text
- [ ] Interactive elements have accessible names
- [ ] Form fields have associated labels

### Motor Accessibility
- [ ] Large touch targets (44x44px min)
- [ ] No time-limited interactions
- [ ] No precision-required gestures
- [ ] Keyboard navigation works (web)

## 6. Internationalization

### Text Handling
- [ ] UI accommodates longer Russian/German text
- [ ] No hardcoded widths that truncate translations
- [ ] Text can wrap without breaking layout
- [ ] Numbers/dates formatted appropriately

### Layout Flexibility
- [ ] Flexible containers, not fixed sizes
- [ ] Button text doesn't overflow
- [ ] Labels don't overlap values

## 7. Performance & Polish

### Perceived Performance
- [ ] Initial render is fast (<1s)
- [ ] No layout shifts after load
- [ ] Skeleton screens for async content (if needed)
- [ ] Local operations feel instant

### Polish Details
- [ ] No orphaned/widow text in important places
- [ ] Images are appropriately sized
- [ ] Transitions are smooth
- [ ] No console errors

## 8. Anti-Patterns Check

Verify the implementation avoids these:

- [ ] ❌ No tutorial/onboarding walls
- [ ] ❌ No mandatory signup before value
- [ ] ❌ No hover-only interactions
- [ ] ❌ No unexplained icons
- [ ] ❌ No tiny touch targets
- [ ] ❌ No loading spinners for local ops
- [ ] ❌ No guilt-inducing copy
- [ ] ❌ No feature creep

## Review Output Format

When reporting issues, use this format:

```
### [Severity] Issue Title
**Location**: file:line or screen/component name
**Problem**: Clear description of the issue
**Impact**: Why this matters (usability, accessibility, etc.)
**Suggestion**: Concrete fix recommendation
```

Severity levels:
- 🔴 **Critical**: Blocks core functionality or major accessibility issue
- 🟠 **Major**: Significant usability problem
- 🟡 **Minor**: Polish issue or improvement opportunity
- 🔵 **Note**: Suggestion for future consideration
