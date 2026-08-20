# LifeFlow Presentation Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an editable, visually polished, technically verified 11-slide LifeFlow PowerPoint deck based on the approved reference template.

**Architecture:** The reference deck supplies the presentation master, theme, and visual vocabulary. Current Java source and clean tests supply all technical claims. A temporary artifact-tool workspace holds inspection data, screenshots, assets, and generation scripts; the repository receives only the final `.pptx` deliverable and this plan.

**Tech Stack:** Java 17/Maven, PowerPoint OOXML, `@oai/artifact-tool`, Node.js, native PowerPoint vector shapes, macOS screenshot tools, official UN SDG assets.

---

### Task 1: Build the content evidence pack

**Files:**
- Read: `src/main/java/lifeflow/model/*.java`
- Read: `src/main/java/lifeflow/service/*.java`
- Read: `src/main/java/lifeflow/persistence/*.java`
- Read: `src/main/java/lifeflow/ui/*.java`
- Create: `/tmp/lifeflow-presentation/evidence/project-evidence.md`

- [ ] **Step 1: Run the clean source-of-truth test command**

Run: `./mvnw clean test`

Expected: `Tests run: 228, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 2: Record current capabilities from source**

The evidence file must cover the three roles, package architecture, request hierarchy, `MatchMode`, priority ordering, FEFO sorting, full-quantity behaviour, eligibility, JSON checksum, atomic save, backup recovery, migration, and locking. Every item must name the supporting class or method.

- [ ] **Step 3: Record stale-document conflicts**

Mark old text-file, five-screen, exact-only, and no-account descriptions as obsolete when the current code contradicts them.

### Task 2: Audit and map the reference template

**Files:**
- Read: `/Users/mohammedriyadh/Desktop/Untitled design.pptx`
- Create: `/tmp/lifeflow-presentation/template/template-audit.txt`
- Create: `/tmp/lifeflow-presentation/template/template-frame-map.json`
- Create: `/tmp/lifeflow-presentation/template/deviation-log.txt`

- [ ] **Step 1: Render and inspect all 11 source slides**

Run the bundled template inspector and render tools. Record layout geometry, image slots, font styles, colors, and inherited elements.

- [ ] **Step 2: Map each output slide to a source frame**

Use strong source frames for cover, split layouts, four-step workflow, red section rhythm, and closing. Rebuild overcrowded source frames while preserving the deck's master and theme.

- [ ] **Step 3: Record intentional deviations**

The deviation log must explain removal of lorem ipsum, overlap, dense paragraphs, and any font fallback.

### Task 3: Collect verified visual assets

**Files:**
- Create: `/tmp/lifeflow-presentation/assets/sdg3.*`
- Create: `/tmp/lifeflow-presentation/assets/admin-dashboard.png`
- Create: `/tmp/lifeflow-presentation/assets/donor-dashboard.png`
- Create: `/tmp/lifeflow-presentation/assets/hospital-dashboard.png`
- Create: `/tmp/lifeflow-presentation/assets/sources.md`

- [ ] **Step 1: Download the official SDG 3 icon**

Use an official United Nations source and record the direct URL and access date in `sources.md`.

- [ ] **Step 2: Capture current application screens**

Package and run the current app with fictional demo data. Capture current admin, donor, and hospital views at consistent scale. Do not use old screenshots when the current UI differs.

- [ ] **Step 3: Prepare images for presentation use**

Crop only empty desktop/window chrome. Keep application labels readable and do not alter UI content.

### Task 4: Specify and verify technical diagrams

**Files:**
- Create: `/tmp/lifeflow-presentation/diagrams/relationship-matrix.md`
- Create: `/tmp/lifeflow-presentation/diagrams/workflow-spec.json`
- Create: `/tmp/lifeflow-presentation/diagrams/architecture-spec.json`
- Create: `/tmp/lifeflow-presentation/diagrams/oop-spec.json`
- Create: `/tmp/lifeflow-presentation/diagrams/matching-spec.json`
- Create: `/tmp/lifeflow-presentation/diagrams/storage-spec.json`

- [ ] **Step 1: Define nodes and edges from current code**

Every node and relationship must include its code evidence. Include `BloodRequest` inheritance, controller dependencies, store interface implementation, request ordering, match-mode branch, inventory availability, and storage recovery flow.

- [ ] **Step 2: Check behavioural edges against tests**

Use matching, compatibility, appointment, storage, migration, and controller test classes. Remove any claim that cannot be supported.

- [ ] **Step 3: Freeze diagram labels in B1+ English**

Use short labels such as “Emergency first”, “Use valid units”, “Full quantity required”, and “Use the unit that expires first”.

### Task 5: Generate the PowerPoint deck

**Files:**
- Create: `/tmp/lifeflow-presentation/build/generate-deck.mjs`
- Create: `output/presentation/LifeFlow_Final_Presentation.pptx`

- [ ] **Step 1: Prepare the reference deck as the starter**

Clone and reuse approved source frames, preserving master/layout inheritance. Remove unused source slides only after output slides are complete.

- [ ] **Step 2: Build the 11 approved slides**

Use concise B1+ English, real screenshots, official SDG 3 icon, editable vector diagrams, and speaker notes. Each slide must have one claim and one dominant visual.

- [ ] **Step 3: Add source notes**

Add `[Sources]` notes for the UN icon, external claims, and any non-template visual asset.

- [ ] **Step 4: Save the final deck only as PPTX**

Do not create a PDF. Preserve the existing older presentation file and write the new deck as `LifeFlow_Final_Presentation.pptx`.

### Task 6: Run structural, visual, and language QA

**Files:**
- Read: `output/presentation/LifeFlow_Final_Presentation.pptx`
- Create: `/tmp/lifeflow-presentation/qa/rendered/*.png`
- Create: `/tmp/lifeflow-presentation/qa/montage.png`
- Create: `/tmp/lifeflow-presentation/qa/qa-report.md`

- [ ] **Step 1: Run automated slide tests**

Expected: no out-of-bounds elements, no overflow, no invalid package relationships, and exactly 11 slides.

- [ ] **Step 2: Render every slide**

Inspect all 11 slides individually for hierarchy, contrast, alignment, spacing, cropping, connector routing, and font substitution.

- [ ] **Step 3: Inspect the full montage**

Confirm visual variety, coherent red/white/navy rhythm, and no repeated dashboard-like grid pattern.

- [ ] **Step 4: Review language and claims**

Check that visible copy is B1+, abbreviations are explained, medical scope is clear, and all technical numbers match the evidence pack.

- [ ] **Step 5: Re-run final verification**

Run `./mvnw clean test` and the slide test suite again. Expected: 228 project tests passing and all slide checks passing.

### Task 7: Deliver the editable deck

**Files:**
- Deliver: `output/presentation/LifeFlow_Final_Presentation.pptx`

- [ ] **Step 1: Confirm the output file exists and is non-empty**

Run: `ls -lh output/presentation/LifeFlow_Final_Presentation.pptx`

- [ ] **Step 2: Confirm no new PDF was created**

Compare PDF directory timestamps with the start of the presentation task.

- [ ] **Step 3: Hand off the deck**

Provide a clickable link to the final PowerPoint and report the verified slide count, test count, and diagram-validation method.
