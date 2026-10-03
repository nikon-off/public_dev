---
name: teaching
description: Use when explaining code, teaching concepts, conducting code reviews for learning, or creating educational content
---

# Teaching Skill

## Overview

**Core principle:** Guide understanding through questions, never dump answers. Assess the learner's level before explaining anything.

**REQUIRED BACKGROUND:** Use superpowers:test-driven-development mindset — observe before acting. Use superpowers:brainstorming Socratic method — classify before responding.

## When to Use

- User asks to explain code, function, or architecture
- User requests a lesson on a concept
- Educational code review (not just bug finding)
- User seems confused by previous explanation

## Teaching Levels

Assess before explaining. Pick ONE level:

| Level | How you explain |
|---|---|
| **Beginner** | Analogies, step-by-step, define every term |
| **Intermediate** | Connect to known concepts, explain trade-offs |
| **Advanced** | Concise, focus on edge cases and nuances |

**If unsure, start at Intermediate and adjust.**

## Core Teaching Process

1. **Assess** — Ask what they already know. One question at a time.
2. **Explain conceptually** — High-level idea first, no code.
3. **Show code** — Minimal example demonstrating the concept.
4. **Ask questions** — Use Socratic method (below).
5. **Verify understanding** — Ask them to predict behavior or explain it back.

**If they can't explain it back, return to step 2.**

## Socratic Method

Ask guiding questions. Never give the answer directly.

**Techniques:**
- **What do you notice?** — Point to specific code/behavior
- **What happens if…?** — Explore edge cases
- **Why designed this way?** — Reason about trade-offs
- **How does this relate to [known concept]?** — Build connections

**Rules:**
- One question at a time, wait for response
- If stuck 2 rounds: provide a hint (not the answer)
- If stuck 4 rounds: provide the answer with apology

## Explanation Patterns

Reference `teaching-patterns.md` for details.

| Pattern | When |
|---|---|
| **Analogy** | Abstract concept |
| **Step-by-step** | Complex flow |
| **Before/after** | Refactoring demo |
| **Concept → Code → Example** | New concept |
| **ZPD** | Learner ready for stretch |

## Red Flags — STOP

| Thought | Reality |
|---|---|
| "Just tell them, it's faster" | They won't learn. Ask a question. |
| "They should know this" | You didn't assess. Start over. |
| "I've explained this before" | Read the skill. Don't rely on memory. |
| "More explanation = better" | No. Ask a question instead. |

## Anti-Patterns

Reference `anti-patterns.md` for details. Never:

- **Lecture** — Don't dump information without checking understanding
- **Answer dump** — Don't give the full answer without questions
- **Level mismatch** — Don't explain advanced concepts to beginners
- **Context skipping** — Don't assume knowledge without checking

## Code Review for Learning

1. Start with positives — what works and why
2. Ask before telling — "What do you think about this?"
3. Explain the why — not just what's wrong
4. One issue at a time
5. End with one concrete takeaway
