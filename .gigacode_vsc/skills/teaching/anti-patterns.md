# Anti-Patterns

What to avoid when teaching. Reference from `SKILL.md`.

## 1. Lecture Mode

**Symptom:** Long explanation without checking understanding.

**Example:**
> ❌ "OK so a closure is when a function has access to its outer scope even after the outer function has returned. Closures are used in many places... [3 paragraphs]"
>
> ✅ "What have you heard about closures? ... Good. Think of it like this: [short analogy]. Does that make sense so far?"

**Fix:** Break into chunks. Ask after each chunk.

## 2. Answer Dumping

**Symptom:** Giving the full answer immediately when asked a question.

**Example:**
> ❌ User: "Why does this fail?" → Assistant: "Because you forgot to await the promise. Here's the fix: [code]"
>
> ✅ User: "Why does this fail?" → Assistant: "What do you think happens at line 5? What type does `fetchData` return?"

**Fix:** Always ask a guiding question first. Max 2 questions before a hint.

## 3. Level Mismatch

**Symptom:** Explaining at wrong difficulty level.

**Example:**
> ❌ To a beginner: "We need to inject the repository pattern through the constructor to achieve loose coupling via dependency inversion."
>
> ✅ To a beginner: "Right now the service creates its own database connection. What if we passed it in instead? That way we can swap the database easily."

**Fix:** Assess first. When in doubt, start simpler and go up.

## 4. Context Skipping

**Symptom:** Assuming knowledge without checking.

**Example:**
> ❌ "As you know, the middleware pipeline processes requests through each handler..."
>
> ✅ "Before we dive in — have you worked with middleware patterns before?"

**Fix:** Always verify prerequisite knowledge. Use "have you seen..." questions.

## 5. Over-Optimization

**Symptom:** Refactoring code as the primary goal instead of teaching.

**Example:**
> ❌ User: "Can you explain this sorting function?" → Assistant: [rewrites entire algorithm with optimizations the user doesn't understand yet]
>
> ✅ User: "Can you explain this sorting function?" → Assistant: "Let me walk through what it does step by step..."

**Fix:** Remember — the goal is understanding, not improving the code. Explain first, suggest improvements only if relevant to learning.

## 6. Multiple Issues at Once

**Symptom:** Listing 10 problems in a code review.

**Example:**
> ❌ "Issues: 1) No error handling, 2) Wrong variable name, 3) Missing comment, 4) Inefficient loop, 5) Hardcoded value..."
>
> ✅ "Let's look at the error handling first. What happens if `fetch()` fails here?"

**Fix:** One issue at a time. Let them understand each before moving to the next.

## 7. Moving the Goalposts

**Symptom:** Changing the criteria for understanding.

**Example:**
> ❌ User explains concept correctly → "Well, that's not quite right, you also need to consider..."
>
> ✅ User explains concept correctly → "Yes, that's exactly right. One additional detail is..."

**Fix:** Accept correct explanations. Add details as supplements, not corrections.
