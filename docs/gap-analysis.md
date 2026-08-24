# LifeFlow Gap Analysis

## Purpose

This document contains every requirement in
[Requirements Traceability](requirements-traceability.md) that is not currently
classified as `MET`. It separates application defects from missing submission
evidence so the team does not change working code to solve a documentation
problem.

Severity means:

- **Critical:** blocks a mandatory submission or prevents the application from
  running.
- **High:** threatens a major 15-20 mark rubric category.
- **Medium:** weakens evidence, quality, security, or presentation readiness.
- **Low:** creates ambiguity or polish risk without blocking the project.

## Critical gaps

| Related requirements | Evidence | Rubric or submission impact | Required action | Action type |
|---|---|---|---|---|
| SUB-04, REP-01 | The repository intentionally has no current project-report PDF or slide-deck PDF because the obsolete versions were removed. | A mandatory LMS submission would be incomplete without both PDFs. | After this documentation is approved, create the English report and slide deck, verify their content against the traceability matrix, export both to PDF, and assemble the private LMS package. | Submission deliverable |

No critical application-runtime gap was found. The clean build succeeds and all
229 automated tests pass.

## High gaps

| Related requirements | Evidence | Rubric or submission impact | Required action | Action type |
|---|---|---|---|---|
| SDG-01, REP-02, REP-03 | The code and README clearly select SDG 3 and implement a blood-donation coordination simulation, but there is no formal sourced SDG background or realistic problem analysis in a current report. | SDG Problem Understanding & Relevance is worth 15 marks. | Research credible SDG 3 and blood-supply sources, then write a concise background, problem statement, and explicit link from each problem to a verified LifeFlow capability. | Report and research |
| REP-05 | A current Mermaid UML source and SVG now exist, but the final report does not yet embed or explain them. | System Design & UML is worth 15 marks. | Insert the verified SVG in the report and explain inheritance, realisation, aggregation/composition, multiplicities, and service dependencies. | Report deliverable |
| REP-06 | Architecture and implementation evidence exist in code and technical documentation, but no final Implementation Details section exists. | Functionality, OOP, and data-handling categories total 50 published marks. | Build the report section from `architecture.md`, using small code extracts and avoiding unsupported claims. | Report deliverable |
| REP-07 | Automated testing evidence exists, but current screenshots/sample outputs have not been captured from the rebuilt application. | Testing evidence and report quality are weakened without visible output. | Run the packaged JAR against a temporary data directory and capture representative login, dashboard, request, appointment, matching, and report screens. | Demonstration evidence |

## Medium gaps

| Related requirements | Evidence | Rubric or submission impact | Required action | Action type |
|---|---|---|---|---|
| OOP-03 | Domain classes use getters and intent-specific mutation methods such as `updateDetails`, `correctDates`, and lifecycle transitions, but they do not expose conventional `setX` methods. | A marker interpreting "getters and setters" literally may question this requirement even though the design has stronger encapsulation. | Explain that controlled domain operations replace unrestricted setters. Add conventional setters only if the lecturer confirms that method naming is mandatory. | Lecturer clarification / possible code change |
| REP-08 | Limitations are identified in the current technical docs but are not yet discussed critically in a report. | Report Quality is worth 5 marks and explicitly asks for critical discussion. | Discuss educational medical rules, plain-text local credentials, hard-coded admin credentials, lack of encryption, lack of measured test coverage, and absence of automated end-to-end desktop testing. | Report deliverable |
| PRE-01, PRE-02, PRE-03 | The current repository has a demo flow but no current slides, timed script, or completed live-demo rehearsal. | Presentation & Demonstration is worth 5 marks. | Produce the slide deck after the report outline is stable, assign speaking time, package the JAR, and rehearse the complete scenario to approximately 15 minutes. | Presentation deliverable |
| SUB-01, SUB-02, SUB-03, PRE-04 | The public repository does not establish the team roster, leader, personal identifiers, speaking allocation, or equal contribution. | The LMS instructions and Team Contribution category cannot be proven from code. | Maintain a private roster and contribution table containing the required identities, submission owner, tasks, and presentation sections. | Human/team evidence |
| Security limitation | `AdminAuth` contains default credentials; `Hospital`, `DonorAccount`, `JsonHospitalStore`, and `JsonDonorStore` persist passwords as plain text. | This does not violate an explicit brief item, but it limits professional quality and must not be presented as production security. | State the limitation in the report. If future scope allows, migrate to salted password hashes and configurable administrator authentication with a backward-compatible account migration. | Future code improvement |
| Test-depth limitation | Maven runs 229 tests, but no JaCoCo or equivalent coverage plugin is configured and Swing tests do not automate a complete rendered desktop journey. | The suite is strong behavioural evidence, but a numerical coverage claim or full end-to-end claim would be unsupported. | Do not invent coverage percentages. Add JaCoCo and a repeatable GUI smoke test only as future work. | Future quality improvement |

## Low gaps and ambiguities

| Related requirements | Evidence | Rubric or submission impact | Required action | Action type |
|---|---|---|---|---|
| SRC-01 | The GitHub URL is present in `README.md`, but no current final report exists in which to place it. | The explicit source-submission instruction is not complete until the report contains the URL. | Insert `https://github.com/moha-r/LifeFlow` on the report cover/front matter or repository section. | Report deliverable |
| REP-04, REP-09 | System objectives and conclusion are not yet written as final report sections. | These are required report headings but can only be finalised after the evidence and discussion are stable. | Derive measurable objectives from traceability, then write the conclusion last. | Report deliverable |
| SUB-05 | The official brief says the presentation date is TBA. | Scheduling remains uncertain but does not affect application correctness. | Confirm the date and update the private rehearsal schedule. | Lecturer/team coordination |
| Rubric arithmetic | Published rubric weights sum to 105 while the project is described as 40% of the course. | Recalculating or silently normalising the weights could misrepresent the lecturer's rubric. | Preserve the printed weights and ask the lecturer how the final mark is normalised if needed. | Lecturer clarification |

## Evidence-backed conclusion

The current gap profile is **delivery-heavy, not functionality-heavy**. The
mandatory Java, GUI, OOP, collections, file-handling, processing, and output
requirements have direct implementation and automated-test evidence. The
largest remaining risks are the report, slides, sample outputs, presentation
rehearsal, private identity data, and team-contribution evidence.

No application feature should be changed solely to make the documentation sound
stronger. Any future code change should begin with a separate decision and test
plan, then update this analysis after verification.
