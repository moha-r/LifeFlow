# LifeFlow Development Plan

## Purpose

This plan separates the verified current system from future work. Items under
**As-Built Baseline** are implemented and tested. Items under **Future Roadmap**
are proposals or submission tasks and must not be described as completed
features.

The plan is grounded in the current source tree, the official BIT1123 brief,
[Requirements Traceability](requirements-traceability.md), and
[Gap Analysis](gap-analysis.md).

## As-Built Baseline

### 1. Domain and OOP foundation

Implemented capabilities:

- private domain state with getters and controlled update/lifecycle methods;
- `Identifiable` plus the generic `Repository<T extends Identifiable>`;
- abstract `BloodRequest` with regular and emergency subclasses;
- overridden request priority and kind used through superclass references;
- typed enums and result records for inventory, eligibility, appointments,
  matching, and request status; and
- a shared domain-exception hierarchy.

Evidence: `ObjectContractTests`, `RepositoryTests`,
`ExceptionHierarchyTests`, and `ExceptionSerializationTest`.

### 2. Eligibility, inventory, and request simulation

Implemented capabilities:

- age, weight, date, and three-month donation-interval checks;
- 35-day shelf-life calculation and derived inventory state;
- emergency-first request priority with deterministic tie-breaking;
- exact and compatible ABO/Rh matching modes;
- FEFO unit selection;
- full-quantity matching without accidental partial consumption; and
- protection for fulfilled, cancelled, used, reserved, and discarded records.

Evidence: `DonationPolicyTest`, `BloodCompatibilityTests`,
`MatchingConsistencyTest`, `ControllerReliabilityTest`,
`DomainReliabilityTest`, and `RequestDeclineTest`.

### 3. Persistent state and reliability

Implemented capabilities:

- one revisioned JSON snapshot for operational data;
- whole-state validation before save and after load;
- SHA-256 checksum verification;
- validated temporary writes and required atomic replacement;
- current and previous verified backups;
- explicit recovery with corrupt-file preservation;
- single-data-directory locking;
- Version 1 to Version 2 migration; and
- separate atomic JSON stores for hospital and donor accounts.

Evidence: `JsonLifeFlowStoreTest`, `JsonMigrationTest`,
`HospitalRegistryTest`, and `DonorRegistryTest`.

### 4. Role-specific user journeys

Implemented capabilities:

- administrator, hospital, and donor authentication paths;
- hospital and donor self-registration;
- eight-page administrator workspace;
- hospital request creation, cancellation, volunteer review, and appointment
  completion;
- donor eligibility, profile, donation history, urgent needs, appointment
  booking/cancellation, and password change; and
- safe role/session switching.

Evidence: `LoginTest`, `AdminWorkspaceLayoutTest`,
`HospitalAppointmentsUiTest`, `DonorPortalUiTest`, and
`DonorInventoryUiTest`.

### 5. Volunteer appointments and coordinated fulfilment

Implemented capabilities:

- sequential appointment IDs and ownership checks;
- one active appointment per donor;
- compatibility checks for linked volunteer requests;
- reserved units for partial volunteer coverage;
- automatic fulfilment when the requested quantity is reached;
- release of reservations and cancellation of appointments when requests close;
  and
- automatic cleanup for stale requests and missed appointments.

Evidence: `AppointmentBookingTest`, `AppointmentValidationTest`,
`DonationAppointmentTest`, and `StaleDataCleanupTest`.

### 6. Reporting and quality baseline

Implemented capabilities:

- inventory, donor, request, appointment, and audit CSV exports;
- a printable operational HTML summary;
- deterministic Maven build and shaded executable JAR;
- 229 passing JUnit tests; and
- `-Xlint:all` compilation with zero warnings.

Evidence: `ReportExporterTest` and the verified `./mvnw clean test` result.

## Future Roadmap

| Priority | Work item | Why it is not complete | Acceptance criteria | Dependencies |
|---|---|---|---|---|
| P0 | Produce the academic project report and PDF. | No current report artifact exists. | Contains all eight required sections, GitHub URL, current UML, sourced SDG analysis, test evidence, screenshots, limitations, and conclusion; every technical claim maps to current documentation. | Approved documentation baseline and team identity data for the private copy. |
| P0 | Produce the slide deck and PDF. | Old slides were removed because they no longer matched the application. | Supports an approximately 15-minute presentation, includes current screenshots and UML, assigns speaking roles, and follows the same facts as the report. | Stable report outline and current screenshots. |
| P0 | Rehearse and package the live demonstration. | Automated tests do not prove presentation timing or operator fluency. | `target/lifeflow.jar` runs from a clean temporary data directory; the hospital-to-donor-to-admin scenario completes without manual data repair; every member can perform the assigned section. | Packaged JAR, final demo script, and team roster. |
| P0 | Assemble private LMS metadata and contribution evidence. | Personal data and team allocation cannot be inferred safely from public Git. | Full name, student ID, class code, programme, NRIC/passport, team leader, contribution summary, and presentation allocation are complete in the private submission only. | Input from every team member. |
| P1 | Confirm the lecturer's interpretation of setters and rubric arithmetic. | The design uses intent-specific mutation rather than conventional `setX`; published rubric weights total 105. | Lecturer guidance is recorded; no code or score interpretation is changed based on assumption. | Lecturer response. |
| P1 | Improve local credential security. | Admin credentials are fixed and account passwords are plain text. | Passwords use salted hashes, admin authentication is configurable, old accounts migrate safely, authentication tests cover both migration and new credentials, and no secret appears in committed documentation. | Separate security design and data-migration plan. |
| P1 | Add measurable test coverage and a desktop smoke test. | No coverage plugin is configured and current Swing tests are primarily component/behaviour tests. | JaCoCo reports are reproducible, an agreed threshold is documented, and one automated or scripted smoke path covers login through persisted result. | Agreement on coverage threshold and GUI automation approach. |
| P2 | Split large controller responsibilities. | `LifeFlowController` coordinates many use cases in one class. | Extracted services preserve the same transaction boundary, deep-copy rules, and persistence rollback behaviour; the complete regression suite remains green. | Architecture design and focused regression tests before refactoring. |
| P2 | Strengthen account-store resilience. | Account stores have atomic replacement but no checksum, backups, version migration, or encryption. | Donor and hospital account data have explicit versions, validated migrations, recovery tests, and documented security properties. | Credential-security design. |
| P2 | Resolve the report-export abstraction. | `ReportExporter` describes a strategy contract while current CSV/HTML exporters are static utilities. | Either concrete exporters implement the interface consistently or the unused abstraction is removed; public behaviour and tests remain unchanged. | Small refactoring plan and exporter tests. |
| P3 | Accessibility and usability review. | Automated structure tests cannot prove keyboard, contrast, screen-reader, or live user experience quality. | Keyboard navigation, focus order, readable scaling, contrast, and representative user tasks are manually audited and documented. | Stable UI and a review checklist. |

## Change-control rules

Future work must follow these rules to keep documentation aligned:

1. Start each functional change with a focused test that fails for the intended
   reason.
2. Preserve `LifeFlowState` deep-copy semantics and use lists from a single
   getter call for each transaction.
3. Treat persistence DTO changes as format changes because checksum input is
   schema-sensitive.
4. Guard both `FULFILLED` and `CANCELLED` requests in every update or processing
   path.
5. Generate IDs by scanning for the highest suffix, not from collection size.
6. Run `./mvnw clean test`; incremental compilation is not accepted as final
   evidence.
7. Update traceability, gap analysis, architecture, and testing documentation in
   the same logical change when their facts change.
8. Do not label roadmap items as implemented before code and verification exist.

## Completion order for submission work

1. Approve this technical documentation baseline.
2. Collect team-only identity and contribution information.
3. Research and write SDG/problem material.
4. Build the report from the eight required headings.
5. Capture current application screenshots from a clean run.
6. Review and export the report PDF.
7. Build slides and a timed speaking script from the approved report.
8. Package the JAR and rehearse the live demo.
9. Perform a final cross-artifact fact check before LMS upload.

Related documents:

- [Architecture](architecture.md)
- [Testing and Code Quality](testing-and-code-quality.md)
- [Requirements Traceability](requirements-traceability.md)
- [Gap Analysis](gap-analysis.md)
