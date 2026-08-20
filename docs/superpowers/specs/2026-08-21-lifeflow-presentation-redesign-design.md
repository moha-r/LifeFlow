# LifeFlow Presentation Redesign

## Goal

Create a polished, editable PowerPoint deck for the current LifeFlow project.
The deck will use the visual language of `Untitled design.pptx` while improving
its hierarchy, spacing, clarity, and technical accuracy. The only delivered
artifact is a `.pptx` file; no PDF will be produced.

## Audience and Constraints

- Audience: OOP lecturer and classmates.
- Target duration: about 15 minutes, including a short live demo.
- Visible language: clear B1+ English with short sentences and defined terms.
- Scope: the current Java 17 Swing project, not stale descriptions in old docs.
- Medical scope: educational simulation, not clinical decision support.
- Output: one editable 16:9 PowerPoint deck with speaker notes and sources.

## Content Source of Truth

The current source code and a fresh clean test run are authoritative. Existing
documents may guide the narrative, but claims that conflict with code are
discarded. In particular, the current project uses checksum-verified JSON,
atomic saves, backups, migration, and account-based donor and hospital portals.

## Narrative

The deck tells one connected story: a hospital creates a request, a donor books
an appointment, the hospital records a donation, a blood unit enters inventory,
and the admin fulfils the request using the matching rules. Technical slides then
show how Java OOP, services, and reliable storage support this workflow.

## Slide Map

1. Cover — LifeFlow and the educational simulation scope.
2. Problem and SDG 3 — stock, expiry, urgency, and coordination.
3. Users — admin, donor, and hospital responsibilities.
4. Connected workflow — request to donation to fulfilment.
5. Product experience — current admin, donor, and hospital interfaces.
6. Architecture — Swing UI, controller, services, models, and JSON store.
7. OOP design — abstraction, inheritance, polymorphism, interfaces, composition.
8. Matching logic — priority, mode, availability, FEFO, and full quantity.
9. Reliable storage — checksum, atomic save, backup, migration, and locking.
10. Verification and demo — 228 passing tests and the live-demo sequence.
11. Impact and conclusion — SDG value, limitations, and realistic next steps.

## Visual Direction

Use the reference deck's red, white, and dark medical palette, large imagery,
split layouts, and strong section rhythm. Preserve the reference presentation's
master/layout hierarchy where practical, but repair its crowded text, overlap,
and placeholder problems. Use Lato consistently when the embedded face resolves
correctly. Each slide has one claim, one dominant visual, and generous whitespace.

## Technical Visuals

Technical diagrams are built deterministically as editable PowerPoint vector
shapes. Generative image tools are not used for UML, architecture, or logic.
Before drawing, a relationship matrix records every node, edge, label, and its
supporting class or method. The final diagram is checked against source code,
tests, and the rendered slide. UI images are real screenshots of the running app.

## Sources

The official UN SDG 3 asset is retrieved from an official UN source. External
assets and factual claims are recorded in slide speaker notes using `[Sources]`
entries. Existing template images are treated as template-owned assets.

## Quality Gate

- `./mvnw clean test` must pass before using the test count.
- Every slide is rendered and inspected individually.
- Automated slide checks must report no overflow or out-of-bounds content.
- The full-deck montage must show consistent visual rhythm.
- No placeholder text, lorem ipsum, accidental PDF, or ambiguous technical claim.
- The final file remains editable and opens cleanly in PowerPoint.
