# Coding Principles

This document describes the values that guide design and implementation in this project.
These are not rules to be applied mechanically. They are lenses for reasoning about code,
trade-offs, and design pressure.

The goal is clarity, correctness, and learning speed — not conformity.

---

## 1. Start with the truth of the problem, not the shape of the tools

**Intent**
Understand the actual constraints and invariants of the problem before choosing libraries,
frameworks, or architectural patterns.

**Design smell**
The system is explained primarily in terms of tools (“this is a pipeline”, “this is a hook”)
rather than domain facts.

**Example**
If events may arrive out of order, this property should be explicit in the data model or
processing logic, not implicitly handled by a chosen abstraction.

---

## 2. Build the simplest thing that can explain itself

**Intent**
Code should communicate its intent when read later, without requiring external explanation.

**Design smell**
Understanding a piece of code requires comments like “this is subtle” or “don’t touch this”.

**Example**
A straightforward loop with explicit state is often preferable to a compact but opaque
functional composition.

---

## 3. Fight accidental complexity early

**Intent**
Complexity accumulates faster than it is removed. Clever solutions tend to harden over time.

**Design smell**
The code feels impressive but fragile; small changes introduce fear or uncertainty.

**Example**
Deleting a generic helper and duplicating a few lines of logic can reduce overall complexity
by restoring locality and readability.

---

## 4. Put invariants in code, not in your head

**Intent**
Correctness should be enforced structurally, not socially.

**Design smell**
Correct behavior depends on undocumented assumptions about call order, valid values, or
implicit state.

**Example**
Prefer data structures, types, or assertions that make invalid states impossible or explicit.

---

## 5. Make data and control flow obvious

**Intent**
A reader should be able to follow execution and data transformation without searching
through layers of indirection.

**Design smell**
Answering “what happens next?” requires jumping across files, macros, or hidden dispatch.

**Example**
Prefer explicit function calls and data passing over implicit context, reflection, or
action-at-a-distance.

---

## 6. Optimize for feedback

**Intent**
Fast feedback enables better decisions than theoretical optimality.

**Design smell**
Trying ideas feels expensive due to slow builds, long startup times, or heavy setup.

**Example**
A rough prototype that runs quickly often teaches more than a polished architecture that
takes minutes to validate.

---

## 7. Measure when it matters

**Intent**
Human intuition about performance is unreliable beyond trivial cases.

**Design smell**
Optimizations are justified by assumptions rather than evidence.

**Example**
Write the clear version first, then measure real bottlenecks before changing structure.

---

## 8. Prefer directness over abstraction

**Intent**
Abstractions introduce cognitive and structural cost. They must justify their existence.

**Design smell**
An abstraction exists “just in case” or before real duplication appears.

**Example**
Two explicit functions are often clearer than one parameterized abstraction with flags.

---

## 9. If it hurts to test, the design is wrong

**Intent**
Testability is a reflection of conceptual clarity.

**Design smell**
Tests require complex setup, deep mocking, or knowledge of internal structure.

**Example**
Pure functions over explicit data are usually trivial to test without ceremony.

---

## 10. Every line should pay for itself

**Intent**
Code is a long-term liability. Each line adds maintenance cost.

**Design smell**
A line exists “for safety” or “for the future” without a clear current purpose.

**Example**
Removing unused or speculative code is often a net improvement, even if it feels risky.

---

## Notes on Use

These principles are meant to guide reasoning, not to enforce compliance.
Violating a principle is acceptable when it results in clearer, more truthful code.
When principles conflict, prefer clarity and understanding over consistency.
