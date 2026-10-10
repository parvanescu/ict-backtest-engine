# Descriptive-file development rules

Rules for reading the descriptive files scattered under `system/` (or
wherever this project keeps them) and turning them into real code.
A descriptive file is a spec, not code — comments only, no implementation —
until explicitly told to execute it. This document is about *how to read
them*; see `CLAUDE.md` for how sessions work in general.

## What a descriptive file is

A source file (e.g. `.py`, `.ts`) containing only structured comments — no
real implementation yet. Recognized sections, in the shape already
established:

- `## Description` — what this component is and why it exists.
- `## Fields` — the state it holds.
- `## Methods` — its public surface: method names, and their intent.
- `## Where exactly is used in the system` — its consumers/relationships.

Treat this shape as the convention for every descriptive file, not just
the one it first appeared in.

## Reading rules

1. **Read every descriptive file in a component together, as one unit,
   before implementing any single one of them.** A "component" here means
   a package made of multiple descriptive files describing two halves of
   one thing (e.g. a builder and the object it builds), not two
   independent things. Implementing one without having read the other
   risks interfaces that don't actually compose.
2. **Actively check the logical structure and consistency of what was
   read — don't just passively absorb it.** Check: do the `## Fields`
   match how `## Methods` actually uses them? Does one file's described
   interface match what another file in the same component assumes about
   it (e.g. a builder's described return value is what its consumer
   actually expects to receive)? Do two sections, or two files in the same
   component, describe the same thing two different, contradicting ways?
   This is a stronger bar than rule 7 below — actively look for
   inconsistencies before implementing, don't just wait to trip over one.
   Surface anything found rather than silently resolving it one way.
3. **A descriptive file specifies the interface only** — public shape
   (fields, method names, intent), not implementation. Even in languages
   with no formal `interface` keyword, the intent carries over: `## Methods`
   is the contract to implement, not a hint about internals. How it works
   internally is mine to design when told to execute, following clean-code
   and `CLAUDE.md` standards — not something to ask the user to spec out
   further.
4. **OOP, translated to how this codebase already does it**: one class,
   one responsibility, one file where that holds. Prefer the patterns
   already in use — a structural-interface mechanism where the language has
   one, a `<Thing>Builder` class that validates config and constructs the
   real object via `build()` when a descriptive file implies "construct
   this from config." Don't invent a new convention when an existing one
   already fits. If a request implies adding a same-named method with a
   different signature than one that already exists, and the language
   doesn't support overloading, don't silently shadow it or invent a
   differently-named twin; unify into one method dispatched on an optional
   parameter and say plainly that's what happened, since it's an easy
   thing to not notice was even a collision.
5. **Clean, not over-engineered.** No abstraction beyond what the
   `## Methods` list actually calls for, no speculative generality for
   requirements that aren't written down, no comments restating what the
   code already says. Follow `CLAUDE.md` in full otherwise — validate real
   behavior before calling anything done, plan before executing anything
   non-trivial.
6. **A descriptive file existing is not a command to implement it.**
   Only implement when explicitly told to execute. Writing and refining
   descriptive files is deliberately a separate step from building them.
7. **Ambiguous or underspecified spec → ask, don't guess.** Especially
   when a descriptive file's shape would conflict with a convention an
   already-implemented component uses (naming, builder pattern,
   encapsulation approach) — flag the conflict rather than silently
   picking one side.
8. **Triage every requirement by uncertainty before implementing
   anything: low, medium, or high.** Implement low-uncertainty items only.
   Medium or higher → do not implement; surface it as a specific,
   answerable question, with concrete resolution options where possible
   rather than an open-ended "what did you mean." If a chain of dependent
   components has any medium+ link, treat everything **downstream** of it
   as blocked too — even a piece that looks unambiguous in isolation, if
   its shape depends on how the unresolved link gets resolved.
9. **A descriptive file's named technology is a starting hypothesis, not
   a lock-in.** If a spec names a specific library or storage approach and
   implementing it for real surfaces enough friction (e.g. hand-rolled
   glue code that a different library would avoid), it's worth stopping
   and researching alternatives mid-build, the same way `CLAUDE.md`
   requires for any dependency choice. Don't treat a spec's named library
   as fixed just because it's already written down, if implementing it
   surfaces a real problem.
10. **Simplifying or removing already-built, already-tested functionality
    is a legitimate outcome, not a failure to resist**, once real usage
    clarifies it isn't needed. When adding a new periodic/background
    concern to a class that already has one, fold it into the existing
    loop/thread rather than standing up parallel machinery for it, unless
    there's a real reason they can't share one.
