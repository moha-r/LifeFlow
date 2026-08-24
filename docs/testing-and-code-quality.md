# LifeFlow Testing and Code Quality

## Verified baseline

The documentation rebuild started from this clean command:

```bash
./mvnw clean test
```

Verified result on 24 August 2026:

```text
Production source files compiled: 73
Test source files compiled:       35
Tests run:                        229
Failures:                         0
Errors:                           0
Skipped:                          0
Compiler warning lines:           0
Build result:                     SUCCESS
```

The Maven compiler targets Java release 17 and enables `-Xlint:all`. The
verification machine ran a newer JDK while still compiling against the Java 17
language/API target. The project therefore documents Java 17+ as its runtime
requirement rather than claiming a dependency on the verification machine's JDK.

## Build and verification commands

### Full source-of-truth check

```bash
./mvnw clean test
```

Always use a clean build for final evidence because stale incremental output can
hide compilation changes.

### Test summary only

```bash
./mvnw test 2>&1 | grep -E 'Tests run: [0-9]+, Failures' | tail -1
```

### Package the executable JAR

```bash
./mvnw -q clean package -DskipTests
```

Expected artifact:

```text
target/lifeflow.jar
```

Skipping tests is appropriate only after a successful clean test run has already
provided evidence for the same source revision.

## Test-suite structure

The suite combines focused JUnit 5 classes with a legacy assertion suite invoked
through `LegacySuiteTest`. New regression tests use class names ending in
`Test`, ensuring Maven Surefire discovers them directly.

| Area | Representative test classes | Main evidence |
|---|---|---|
| Domain and object contracts | `ObjectContractTests`, `DonationAppointmentTest`, `ExceptionHierarchyTests`, `ExceptionSerializationTest` | Equality, hashing, identity, inheritance, lifecycle, exception data, and serialisation |
| Eligibility and compatibility | `DonationPolicyTest`, `BloodCompatibilityTests` | Age, weight, dates, waiting period, expiry, and ABO/Rh compatibility |
| Inventory and matching | `DonorInventoryArchitectureTest`, `MatchingConsistencyTest`, `ControllerReliabilityTest`, `DomainReliabilityTest` | Inventory state, ID generation, FEFO, request order, all-or-nothing matching, audit consistency, and rollback |
| Requests and stale cleanup | `RequestDeclineTest`, `StaleDataCleanupTest` | Cancellation, immutability, grace periods, missed appointments, reservation release, and urgent-needs filtering |
| Appointments and volunteers | `AppointmentBookingTest`, `AppointmentValidationTest` | Booking, ownership, eligibility, compatibility, partial reservations, completion, persistence, and invalid-state rejection |
| Main persistence | `JsonLifeFlowStoreTest`, `JsonMigrationTest` | Round trips, checksum detection, backups, recovery, atomic safety, file locking, and Version 1 migration |
| Accounts and signup | `HospitalRegistryTest`, `DonorRegistryTest`, `DonorSignupServiceTest`, `LoginTest` | Registration, authentication, persistence, rollback, role login, and password changes |
| Swing UI structure and behaviour | `AdminWorkspaceLayoutTest`, `DonorInventoryUiTest`, `HospitalAppointmentsUiTest`, `DonorPortalUiTest`, `LoginTest` | Navigation, bounded layouts, tables, filters, role actions, enablement rules, and status presentation |
| Exported output | `ReportExporterTest` | CSV escaping/content, audit filtering, appointments, HTML metrics, and HTML escaping |
| Legacy regression coverage | `LegacySuiteTest` and manual `*Tests` runners | Earlier donor, unit, request, inventory, matching, repository, and controller assertions |

## Requirement coverage by behaviour

### Data creation and management

Tests create and persist donors, accounts, hospitals, units, requests, and
appointments. Duplicate IDs, invalid values, missing references, and ownership
violations are rejected with typed domain exceptions.

Representative evidence:

- `DonorSignupServiceTest.signupCreatesAccountAndProfileTogether`
- `ControllerReliabilityTest.unitCreationPersistsTheUnitAndDonationHistoryTogether`
- `AppointmentBookingTest.bookCreatesAppointmentWithSequentialIds`
- `HospitalRegistryTest.registrationCreatesAccountWithGeneratedId`

### Simulation and decisions

The suite verifies that emergency priority does not prevent the next actually
fulfillable request from being processed, matching uses FEFO, incompatible units
are excluded, and insufficient stock does not consume units or increment the
revision.

Representative evidence:

- `MatchingConsistencyTest.nextFulfillableMayBeLowerPriorityThanNextPending`
- `ControllerReliabilityTest.matchingUsesFefoAndPersistsTheAuditInOneSnapshot`
- `ControllerReliabilityTest.insufficientStockDoesNotSaveOrChangeRevision`
- `BloodCompatibilityTests.abposCanReceiveFromEveryone`

### Transaction integrity

Controller operations build, validate, and save a complete candidate snapshot
before publishing it. Failure tests use controlled stores to prove that state,
revision, units, requests, and audit history roll back together.

Representative evidence:

- `ControllerReliabilityTest.failedSaveLeavesThePublishedStateUnchanged`
- `ControllerReliabilityTest.failedMatchingSaveRollsBackUnitsRequestAndAuditTogether`
- `JsonLifeFlowStoreTest.invalidCandidateNeverReplacesTheLastGoodFile`

### Persistence and recovery

Temporary directories isolate tests from user data. Persistence tests corrupt
or remove controlled fixture files and verify explicit failure/recovery
behaviour.

Representative evidence:

- `JsonLifeFlowStoreTest.checksumDetectsRevisionTampering`
- `JsonLifeFlowStoreTest.fallsBackToPreviousBackupAndMakesItCurrent`
- `JsonLifeFlowStoreTest.refusesASecondProcessLockForTheSameDirectory`
- `JsonMigrationTest.invalidLegacyHistoryLeavesOriginalFileUntouched`

### Appointments and volunteer fulfilment

Tests cover booking eligibility, one active booking, request compatibility,
hospital ownership, cancellation, unit creation, partial reservations, full
volunteer fulfilment, request closure, and reservation release.

Representative evidence:

- `AppointmentBookingTest.volunteerDonationsReserveUnitsUntilRequestIsComplete`
- `AppointmentBookingTest.matchingCompletesPartiallyReservedRequest`
- `AppointmentBookingTest.decliningRequestReleasesReservedUnitsBackToStock`
- `AppointmentValidationTest.twoActiveBookingsForSameDonorAreRejected`

### GUI and output

Swing tests locate named components, inspect models and layout constraints,
trigger actions, and verify enablement and messages. Export tests read generated
files and verify content and escaping.

Representative evidence:

- `AdminWorkspaceLayoutTest.sidebarUsesCompactGroupedNavigationAndRestrainedActiveState`
- `HospitalAppointmentsUiTest.recordButtonEnablesOnlyForCompletableBooking`
- `DonorPortalUiTest.urgentNeedsShowOnlyCompatiblePendingRequests`
- `ReportExporterTest.summaryHtmlContainsMetricsAndEscapesMarkup`

## Test-design practices

- **Fixed clocks:** time-sensitive controller, eligibility, and registry tests
  use fixed `Clock` instances so dates remain deterministic.
- **Temporary storage:** persistence tests use per-test directories and do not
  touch `~/.lifeflow`.
- **Real domain objects:** matching and validation tests exercise real objects
  rather than replacing business rules with mocks.
- **Failure-state assertions:** tests check both the exception/outcome and the
  absence of partial state changes.
- **Boundary coverage:** age, weight, waiting period, future dates, expiry,
  cancellation, fulfilled state, ownership, and duplicate IDs have explicit
  cases.
- **UI isolation:** Swing component tests exercise frames/panels without relying
  on production user files.

## Code-quality strengths

### Clear package boundaries

Model, service, persistence, and UI packages have distinct responsibilities.
Domain code does not depend on Swing, and persistence is accessed through a
`LifeFlowStore` interface in the main controller.

### Explicit domain vocabulary

Enums, records, typed exceptions, and expressive method names make status and
failure states visible. `MatchResult` distinguishes no request, insufficient
stock, and fulfilment rather than returning an ambiguous boolean.

### Defensive state handling

`LifeFlowState` deep-copies mutable state. The controller validates and saves a
candidate before publishing it. `DataValidator` checks cross-entity invariants,
not only individual fields.

### Persistence safeguards

The main store combines whole-state validation, canonical checksum verification,
forced temporary writes, atomic replacement, backups, explicit recovery,
migration, and a single-instance lock.

### Compiler hygiene

The project compiles all main and test sources under `-Xlint:all` with zero
warning lines in the verified clean build.

## Known limitations

### No measured coverage percentage

No JaCoCo or equivalent Maven plugin is configured. The project can state the
number and subject of tests, but it must not claim a line, branch, or method
coverage percentage.

### No complete automated desktop journey

UI tests verify component behaviour and layout, but they do not replace a full
human-run workflow using the packaged application. The final submission still
needs current screenshots and a rehearsed live demonstration.

### Educational security model

Hospital and donor passwords are stored as plain text in local JSON files, and
administrator credentials are fixed in `AdminAuth`. The software must not be
presented as production-ready authentication.

### Educational medical model

Eligibility and ABO/Rh rules are simplified for OOP simulation. They do not
cover clinical screening, component-specific compatibility, crossmatching,
diagnosis, treatment, or regulatory workflows.

### Controller size

`LifeFlowController` centralises transaction rules consistently but is large.
Future extraction must preserve its atomic commit and rollback semantics rather
than splitting methods without a transaction boundary.

### Account-store resilience differs from main-state resilience

Account stores use temporary files and atomic replacement where available, but
they do not currently have main-state checksum, backup, recovery, or migration
features.

## Quality gate for future changes

A change is not ready to document as complete until all applicable checks pass:

1. A focused regression test fails before the fix or feature.
2. The focused test passes after the smallest implementation change.
3. `./mvnw clean test` succeeds.
4. The final summary reports zero failures, errors, and skipped tests.
5. Compiler output contains zero warnings.
6. Persistence changes include checksum/schema migration analysis.
7. Status-changing code guards fulfilled and cancelled records.
8. Documentation, traceability, and UML are updated when facts change.
9. A live Swing check is performed for user-visible changes.

Related documents:

- [Architecture](architecture.md)
- [Requirements Traceability](requirements-traceability.md)
- [Gap Analysis](gap-analysis.md)
- [Development Plan](development-plan.md)
