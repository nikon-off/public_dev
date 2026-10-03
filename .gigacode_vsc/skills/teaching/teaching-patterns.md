# Teaching Patterns

Collection of explanation patterns for the teaching skill. Reference from `SKILL.md`.

## 1. Analogy-Based Explanation

Map an unknown concept to a familiar one.

**When:** Abstract concepts, new domains

**Structure:**
1. Identify the core mechanism of the target concept
2. Find a familiar domain with the same mechanism
3. State the analogy explicitly
4. Note where the analogy breaks (avoid misconceptions)

**Example:**
> "A JavaScript Promise is like ordering food at a restaurant. You give your order (request), continue doing other things (code keeps running), and when the food is ready (response arrives), you get it. If the kitchen runs out of ingredients (error), they send back a rejection."

## 2. Step-by-Step Walkthrough

Execute code line by line, explaining each step.

**When:** Complex flows, control structures, algorithms

**Structure:**
1. State what the code is supposed to do (high-level)
2. Walk through each line/block
3. Show the state changes (variables, data flow)
4. Summarize the overall pattern

**Example:**
> "Let's trace `fetchUser(id)` step by step:
> 1. Line 1: Checks cache — miss, so we continue
> 2. Line 3: Makes HTTP request to `/users/${id}`
> 3. Line 5: Awaits response — this is where we pause
> 4. Line 7: Parses JSON body
> 5. Line 9: Caches result, returns user"

## 3. Before/After Refactoring

Show broken/problematic code, then improved version.

**When:** Teaching refactoring, code smells, best practices

**Structure:**
1. Show the 'before' code
2. Ask what's wrong (Socratic)
3. Explain each issue
4. Show the 'after' code
5. Explain what changed and why

**Example:**
> "Before: [function with 200 lines, no error handling]
> What issues do you notice?
> After: [extracted into 4 focused functions with proper error handling]
> Key changes: single responsibility, explicit errors, testable units"

## 4. Concept → Code → Example Pipeline

Three-phase explanation for new concepts.

**When:** Introducing a completely new concept

**Structure:**
1. **Concept** — What is it? Why does it exist? (no code)
2. **Code** — Minimal implementation (no examples yet)
3. **Example** — Realistic usage scenario

**Example:**
> "Concept: Dependency injection is passing dependencies instead of creating them internally.
> Code: `function UserService(repo) { this.repo = repo; }`
> Example: `const service = new UserService(new SqlUserRepo());`"

## 5. Zone of Proximal Development (ZPD)

Challenge the learner slightly above their current level, with support.

**When:** Learner has mastered basics and is ready for more

**Structure:**
1. Confirm current level
2. Present a problem slightly above that level
3. Provide scaffolding (hints, partial solutions)
4. Gradually remove support as they improve

**Example:**
> "You understand basic promises. Now: what happens if we chain 3 async operations and the middle one fails? Let me show you the pattern, then you try modifying it."

## When to Combine Patterns

| Situation | Patterns to combine |
|---|---|
| New beginner, new concept | Analogy → Concept→Code→Example |
| Intermediate, reviewing code | Before/After + Socratic questions |
| Advanced, edge cases | Step-by-step walkthrough |
| Stuck learner | ZPD + hint-based Socratic |
