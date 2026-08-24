# LifeFlow Architecture

## Architectural overview

LifeFlow is a Java 17 desktop simulation built with Swing. It coordinates donor
profiles, hospital requests, blood inventory, donation appointments, matching,
reporting, and local JSON persistence. The application uses one shared domain
model but exposes three role-specific sessions:

- **Administrator:** manages the complete operational state and performs
  matching, reporting, and oversight tasks.
- **Hospital:** creates and monitors its own requests, reviews volunteers, and
  records completed appointments.
- **Donor:** reviews eligibility and history, finds compatible urgent needs, and
  manages donation appointments.

The design is a layered desktop architecture:

```text
Swing UI and role sessions
          |
          v
Application services and controller
          |
    +-----+-------------------+
    |                         |
    v                         v
Domain model              Persistence adapters
                              |
                              v
                         Local JSON files
```

The focused class diagram is available as
[Mermaid source](uml/lifeflow-core-class-diagram.mmd) and
[rendered SVG](uml/lifeflow-core-class-diagram.svg).

## Layer responsibilities

| Layer | Main responsibilities | Representative types |
|---|---|---|
| Entry point | Installs Swing look and feel, opens account registries, authenticates users, routes sessions, and handles explicit backup recovery. | `Main` |
| UI | Renders English-language frames, panels, tables, dialogs, metrics, forms, validation messages, and navigation. | `LoginPanel`, `LifeFlowFrame`, `HospitalPortalFrame`, `DonorPortalFrame`, admin panels |
| Application/service | Coordinates use cases, validates candidate state, applies eligibility and matching rules, manages accounts, and exports reports. | `LifeFlowController`, `DonationPolicy`, `MatchingService`, `DataValidator`, registries, exporters |
| Domain model | Represents entities, value objects, lifecycle states, inheritance, polymorphism, and copyable application state. | `Donor`, `BloodUnit`, `BloodRequest`, `DonationAppointment`, `LifeFlowState` |
| Persistence | Resolves the storage directory and saves/loads main state and account registries. | `LifeFlowStore`, `JsonLifeFlowStore`, `JsonDonorStore`, `JsonHospitalStore`, `StoragePaths` |

The UI depends on services, and services depend on model and persistence
contracts. Domain classes do not import Swing. Persistence reconstructs model
objects but does not decide user-interface behaviour.

## Startup and session routing

`Main.main` schedules UI startup on Swing's event-dispatch thread and creates:

1. a `HospitalRegistry` backed by `hospitals.json`;
2. a `DonorRegistry` backed by `donors.json`;
3. a `DonorSignupService` that coordinates donor accounts with medical donor
   profiles; and
4. a `LoginDialog` that returns a `LoginResult` for an administrator, hospital,
   or donor.

The selected identity determines which top-level frame opens. Each session
receives a `SessionSwitcher` so it can return to login, change role, or exit
without embedding startup logic in the frame.

Administrator and portal sessions open their own `JsonLifeFlowStore`. The
store's file lock prevents two sessions or processes from modifying the same
data directory concurrently. Closing a frame releases the controller and its
store before another session opens.

## Administrator workspace

`LifeFlowFrame` owns the central `LifeFlowController` and uses `CardLayout` for
eight pages:

1. Dashboard
2. Donors
3. Blood Inventory
4. Blood Requests
5. Matching
6. Reports
7. Appointments
8. Donation Centers

Panels use controller snapshots and refresh callbacks rather than editing the
persisted state directly. `StateObserver` is used by pages that need an explicit
state-change notification contract.

## Domain model and OOP design

### Entities and identity

`Identifiable` defines `getId()`. `Donor`, `BloodUnit`, `BloodRequest`,
`DonationAppointment`, `Hospital`, and `DonorAccount` implement the contract.
`Repository<T extends Identifiable>` demonstrates a generic collection helper
for case-insensitive lookup and monotonic sequential ID generation.

### Inheritance and runtime polymorphism

`BloodRequest` is abstract and stores shared request fields. Its subclasses
override behaviour:

| Type | `getPriority()` | `getKind()` |
|---|---:|---|
| `RegularRequest` | 1 | `REGULAR` |
| `EmergencyRequest` | 2 | `EMERGENCY` |

`MatchingService` operates on `List<BloodRequest>` and calls these methods
through superclass references. The concrete request type therefore changes the
runtime ordering without type-specific sorting code.

The domain-exception hierarchy provides a second inheritance example.
`LifeFlowException` is the common runtime base for validation, duplicate,
missing-entity, eligibility, immutability, and stock errors.

### Encapsulation and controlled mutation

Domain fields are private. Read operations use getters, while changes use
intent-specific operations such as:

- `Donor.updateDetails`;
- `BloodUnit.correctDates`, `markReserved`, `markUsed`, and `markDiscarded`;
- `BloodRequest.updatePendingDetails`, `markFulfilled`, and `markCancelled`;
- `DonationAppointment.markCompleted` and `markCancelled`.

This avoids unrestricted setters for lifecycle-sensitive fields. The approach
is stronger than exposing arbitrary state changes, but the literal
"getters and setters" wording in the brief should be explained to the marker.

## State and transaction flow

`LifeFlowState` is a complete application snapshot containing revision,
donors, units, requests, fulfilments, appointments, and audit logs. Its
constructor and getters make deep copies of mutable entities and collections.

A normal state-changing controller operation follows this sequence:

```text
1. Read one copy of each required collection from LifeFlowState.
2. Validate input and locate referenced entities.
3. Mutate only those captured copies.
4. Build a candidate LifeFlowState with revision + 1.
5. Run DataValidator against the complete candidate.
6. Ask LifeFlowStore to save the candidate atomically.
7. Publish a deep copy only after the save succeeds.
```

If validation or persistence fails, step 7 never occurs. Tests verify that the
published state and revision remain unchanged after a failed save.

The deep-copy rule has an important implementation consequence: a mutation must
be applied to lists captured from a single getter call and those same lists must
be passed to `commit(...)`. Fetching the list again produces a different copy
and would discard the earlier mutation.

## Eligibility and inventory rules

`DonationPolicy` centralises the educational rules:

- age from 18 through 60;
- minimum weight of 45 kg;
- no future donation date;
- a three-month waiting period; and
- a 35-day blood-unit shelf life.

The result is an `EligibilityResult` containing a boolean outcome, reason,
effective last donation date, next eligible date, and human-readable message.
The effective last donation is the latest of the donor's external history and
recorded internal units.

`BloodUnit.getInventoryState` derives whether a unit is scheduled, available,
reserved, expired, used, or discarded for a supplied date. Inventory counts and
matching therefore do not treat future or expired units as available.

These rules are an educational simplification and are not clinical screening or
transfusion guidance.

## Request prioritisation and matching

`MatchingService.ORDER` sorts pending requests by:

1. highest overridden request priority;
2. oldest request date; and
3. case-insensitive request ID.

`BloodInventory` selects valid units and sorts them by expiry date, donation
date, and ID. This is FEFO: first-expiring, first-out.

Two matching modes exist:

- `EXACT`: the unit blood type must equal the request type;
- `COMPATIBLE`: `BloodType.canReceiveFrom` applies ABO/Rh compatibility.

The default queue operation uses exact matching. A specific request may be
processed with a selected `MatchMode`. Matching is all-or-nothing for the
remaining quantity: insufficient stock returns or throws a defined outcome
without consuming units.

## Appointments and volunteer reservations

A donor may book one active future appointment. The controller checks donor
existence, date, eligibility, ownership, linked-request status, and blood-type
compatibility.

When a hospital completes an appointment:

1. a new blood unit is recorded;
2. the appointment becomes `COMPLETED`;
3. an unlinked unit remains general stock; or
4. a linked volunteer unit becomes `RESERVED` for its pending request.

Partial volunteer coverage is stored in a `FulfilmentRecord` while the request
remains pending. Once the committed unit count reaches the requested quantity,
all reserved units become `USED`, the request becomes `FULFILLED`, and other
booked volunteer appointments for that request are cancelled.

Declining a request cancels linked appointments and releases its reserved units.
Automatic cleanup also cancels missed appointments and uncommitted stale
requests after two days for emergency requests or seven days for regular
requests.

## Persistence architecture

### Storage location

`StoragePaths.resolve` uses `LIFEFLOW_DATA_DIR` when set; otherwise it resolves
`~/.lifeflow`. The working directory does not determine persistent data.

### Main state store

`JsonLifeFlowStore` stores one Version 2 envelope containing:

- `formatVersion`;
- `revision`;
- `savedAt`;
- `data`; and
- a SHA-256 checksum over canonical serialisation of format version, revision,
  and payload.

Before replacing a target, the store writes and forces a temporary file, reads
it back, verifies its checksum, reconstructs its model, and validates the full
state. It then requires an atomic filesystem replacement. If atomic replacement
is unsupported, the previous file is preserved and the save fails explicitly.

The store keeps current and previous verified backups. A damaged or missing
primary is never restored silently. `Main.loadState` asks the user before
calling `restoreLatestBackup`, and a corrupt primary is copied into a recovery
directory before replacement.

Version 1 data is checksum-verified, copied for recovery, converted into the
Version 2 model, validated, and then saved. Migration normalises legacy donation
history and overlong expiry dates without overwriting the original if conversion
fails.

Adding or removing persistence DTO fields changes the canonical checksum input.
Such changes require an explicit format migration or reserialisation strategy;
they cannot be treated as a cosmetic edit.

### Account stores

`JsonDonorStore` and `JsonHospitalStore` write their registries through
temporary files and atomic replacement where supported. They do not currently
provide the main store's checksum, backup, migration, or encryption features.
Passwords are stored locally as plain text, and `AdminAuth` contains fixed
default credentials. These are documented educational limitations, not
production security.

## Reporting

`CsvReportExporter` produces inventory, donor, request, appointment, and audit
CSV files. `HtmlReportExporter` produces a self-contained operational summary
that can be printed to PDF. Exporters consume a `LifeFlowState` snapshot, so
report generation does not mutate operational data.

The operational HTML report is not the academic project report required by the
brief. The completed academic report is stored separately at
`output/pdf/BIT1123_LifeFlow_Project_Report_Group_23.pdf`.

## Architectural trade-offs and boundaries

- A single controller gives the Swing application one transaction boundary and
  consistent validation, but `LifeFlowController` is large and is a candidate
  for future use-case extraction.
- Deep copies provide strong isolation for a small educational dataset, at the
  cost of copying complete collections on reads and commits.
- Local JSON makes the application portable and easy to demonstrate, but it is
  not intended for multi-user network deployment.
- Checksums detect corruption and tampering but do not provide confidentiality
  or identity authentication.
- Swing component tests and service tests provide repeatable evidence, but a
  live desktop demonstration is still required for final presentation evidence.

Related documents:

- [Requirements Traceability](requirements-traceability.md)
- [Gap Analysis](gap-analysis.md)
- [Development Plan](development-plan.md)
- [Testing and Code Quality](testing-and-code-quality.md)
