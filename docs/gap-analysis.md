# LifeFlow Gap Analysis

## Purpose

This document contains every requirement in
[Requirements Traceability](requirements-traceability.md) that is not currently
classified as `MET`. It separates completed repository artifacts from actions
that still depend on the team, lecturer, presentation session, or LMS.

Severity means:

- **Critical:** blocks a mandatory submission or prevents the application from
  running.
- **High:** threatens a major 15-20 mark rubric category.
- **Medium:** weakens evidence, quality, security, or presentation readiness.
- **Low:** creates ambiguity or polish risk without blocking the project.

## Critical gaps

| Related requirements | Evidence | Rubric or submission impact | Required action | Action type |
|---|---|---|---|---|
| SUB-04, REP-01 | The repository contains the final 36-page report PDF, the final 17-slide PPTX, and the runnable JAR. It does not contain an LMS receipt, and the presentation has intentionally been retained in its editable PPTX format. | The repository package is ready, but the official soft-copy PDF wording may require a separate slide PDF before LMS upload. | Leave the GitHub PPTX unchanged. If the lecturer or LMS requires a slide PDF, export that same deck during final submission, upload the required files through the appointed representative, and retain confirmation. | LMS submission |

No critical application-runtime, report-content, or slide-content gap was found.
The clean test baseline remains 229 passing tests, and the packaged JAR launches
successfully.

## High gaps

No remaining gap threatens a 15-20 mark technical category. SDG analysis,
system design and UML, application functionality, OOP, collections, file I/O,
testing, and the required report sections are present in the final report and
supported by the repository evidence.

## Medium gaps

| Related requirements | Evidence | Rubric or submission impact | Required action | Action type |
|---|---|---|---|---|
| OOP-03 | Domain classes use getters and intent-specific mutation methods such as `updateDetails`, `correctDates`, and lifecycle transitions, but they do not expose conventional `setX` methods. | A marker interpreting "getters and setters" literally may question this requirement even though the design has stronger encapsulation. | Use the explanation in Section 4 of the report. Add conventional setters only if the lecturer confirms that method naming is mandatory. | Lecturer clarification / possible code change |
| PRE-02, PRE-03, PRE-04 | The final PPTX, current screenshots, demonstration instructions, and runnable JAR exist, but timing, confidence, live execution, and participation cannot be established from files. | Presentation & Demonstration and Team Contribution still depend on human delivery. | Rehearse to approximately 15 minutes, assign each member a speaking or demo section, run the scenario from a clean data directory, and verify that every member can complete the assigned part. | Presentation and team evidence |
| SUB-01, SUB-02 | The report identifies five members and records their contributions, but repository evidence cannot appoint the LMS representative or prove the accuracy of work performed outside Git. | Submission ownership and collaboration evidence require team confirmation. | Approve the contribution matrix as a team and name the single LMS representative. | Human/team evidence |
| Security limitation | `AdminAuth` contains default credentials; `Hospital`, `DonorAccount`, `JsonHospitalStore`, and `JsonDonorStore` persist passwords as plain text. | This does not violate an explicit brief item, but it limits professional quality and must not be presented as production security. | Keep the limitation visible in the report. Treat salted password hashes and configurable administrator authentication as future work requiring a migration design. | Future code improvement |
| Test-depth limitation | Maven runs 229 tests, but no JaCoCo or equivalent coverage plugin is configured and Swing tests do not automate a complete rendered desktop journey. | The suite is strong behavioural evidence, but a numerical coverage claim or full end-to-end claim would be unsupported. | Do not invent coverage percentages. Add JaCoCo and a repeatable GUI smoke test only as future work. | Future quality improvement |

## Low gaps and ambiguities

| Related requirements | Evidence | Rubric or submission impact | Required action | Action type |
|---|---|---|---|---|
| SUB-05 | The official brief says the presentation date is TBA. | Scheduling remains uncertain but does not affect application correctness or artifact readiness. | Confirm the date with the lecturer and update the team rehearsal schedule. | Lecturer/team coordination |
| Rubric arithmetic | Published rubric weights sum to 105 while the project is described as 40% of the course. | Recalculating or silently normalising the weights could misrepresent the lecturer's rubric. | Preserve the printed weights and ask the lecturer how the final mark is normalised if needed. | Lecturer clarification |

## Evidence-backed conclusion

The remaining gaps are external delivery tasks rather than missing project
content. The mandatory Java, GUI, OOP, collections, file-handling, processing,
output, report, and slide requirements have direct artifacts and supporting
evidence. The final tasks are to confirm the contribution record, rehearse and
deliver the presentation, export a slide PDF only if required, and complete the
LMS upload.

No application feature should be changed solely to make the documentation sound
stronger. Any future code change should begin with a separate decision and test
plan, then update this analysis after verification.
