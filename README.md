# LifeFlow

LifeFlow is a Java 17 Swing desktop application that simulates blood donation,
inventory management, hospital requests, donor appointments, and blood-unit
matching. It is an educational Object-Oriented Programming project aligned with
United Nations Sustainable Development Goal 3: Good Health and Well-being.

GitHub repository: https://github.com/moha-r/LifeFlow

> LifeFlow is an educational simulation. It must not be used for real donation
> eligibility, crossmatching, transfusion, diagnosis, or treatment.

## User roles

LifeFlow provides three role-specific experiences:

- **Administrator:** monitors the dashboard; manages donors, inventory, blood
  requests, donation centers, and appointments; processes matching; and exports
  operational reports.
- **Hospital:** registers an account, creates regular or emergency blood
  requests, reviews its requests and volunteers, manages linked appointments,
  and records completed donations.
- **Donor:** creates an account, reviews eligibility and donation history,
  browses urgent blood needs, and books or cancels donation appointments.

The administrator workspace contains eight pages: Dashboard, Donors, Blood
Inventory, Blood Requests, Matching, Reports, Appointments, and Donation
Centers.

## OOP and technical requirements

The project demonstrates the required OOP concepts through working behavior:

- **Classes and objects:** domain classes model donors, hospitals, blood units,
  requests, accounts, appointments, and fulfilment records.
- **Encapsulation:** private state is exposed through accessors and controlled
  update methods that protect linked or completed records.
- **Abstraction:** `BloodRequest` is abstract, while `LifeFlowStore`,
  `Identifiable`, and `StateObserver` define focused interfaces.
- **Inheritance:** `RegularRequest` and `EmergencyRequest` extend
  `BloodRequest`.
- **Polymorphism:** request priority and kind are selected at runtime through
  `BloodRequest` references and overridden methods.
- **Generics and collections:** `Repository<T extends Identifiable>`,
  `ArrayList`, `List`, `HashMap`, `Map`, and `Set` support storage, lookup,
  validation, and summaries.
- **File I/O:** Jackson-backed JSON stores persist application state, donor
  accounts, and hospital accounts. CSV and HTML exporters produce operational
  reports.
- **GUI:** Swing frames, panels, tables, dialogs, card layouts, and shared UI
  components provide clear visual output.

## Final deliverables

- [Group 23 project report (PDF)](output/pdf/BIT1123_LifeFlow_Project_Report_Group_23.pdf)
- [Group 23 presentation (PPTX)](output/presentation/LifeFlow_Presentation_Group_23.pptx)
- [Runnable application (JAR)](output/jar/lifeflow.jar)

GitHub stores the presentation in its original PowerPoint format. Download the
PPTX to present or edit it in Microsoft PowerPoint. The JAR is a shaded,
self-contained build for Java 17 or later.

## Technical documentation

- [Requirements traceability](docs/requirements-traceability.md)
- [Gap analysis](docs/gap-analysis.md)
- [Architecture](docs/architecture.md)
- [Development plan](docs/development-plan.md)
- [Testing and code quality](docs/testing-and-code-quality.md)
- [Core class diagram source](docs/uml/lifeflow-core-class-diagram.mmd)
- [Core class diagram](docs/uml/lifeflow-core-class-diagram.svg)

## Simulation logic

LifeFlow goes beyond basic CRUD operations. Its main rules include:

- donor eligibility checks for age, weight, donation interval, and date;
- automatic blood-unit expiry calculation;
- emergency requests before regular requests, followed by oldest request date;
- exact-group or compatible blood-type matching;
- FEFO selection, using the valid unit that expires first;
- full-quantity fulfilment with no accidental partial consumption;
- linked volunteer appointments that can contribute units to open requests;
- protection for fulfilled, cancelled, used, reserved, or discarded records;
- automatic decline of stale pending requests and an operation audit log.

## Build, test, package, and run

The project uses the Maven Wrapper, Java 17, Jackson, and JUnit 5.

```bash
./mvnw clean test
./mvnw -q clean package -DskipTests
java -jar target/lifeflow.jar
```

The clean test suite currently contains 229 passing tests with zero failures,
errors, or skipped tests.

A verified prebuilt JAR is available at `output/jar/lifeflow.jar` and can be
started directly:

```bash
java -jar output/jar/lifeflow.jar
```

Default administrator credentials:

```text
Username: admin
Password: admin123
```

Hospital and donor users can create their own accounts from the login screen.

## Clean demonstration environment

Package the application, then start it with a temporary storage directory:

```bash
LIFEFLOW_DATA_DIR="$(mktemp -d)" java -jar target/lifeflow.jar
```

A complete demonstration can follow this flow:

1. Register a hospital and a donor account.
2. Sign in as the hospital and create an emergency blood request.
3. Sign in as the donor, review the urgent need, and book a linked appointment.
4. Sign in as the hospital and record the completed donation.
5. Sign in as the administrator and review inventory, request status, matching,
   reports, and the audit history.
6. Restart the application and confirm that the saved data returns.

## Project structure

```text
src/main/java/lifeflow/
├── Main.java
├── model/        Domain entities, enums, results, and generic repository
├── service/      Validation, eligibility, matching, registries, and exports
├── persistence/  JSON stores, atomic saving, backups, migration, and locking
└── ui/           Login, admin workspace, donor portal, and hospital portal

src/test/java/lifeflow/    JUnit 5 and legacy regression coverage
data/demo/                 Fictional demonstration data
output/pdf/                Final Group 23 project report
output/presentation/       Final Group 23 presentation deck
output/jar/                Verified runnable application JAR
```

## Local storage

The default data directory is `~/.lifeflow`. Set `LIFEFLOW_DATA_DIR` to use a
different location. LifeFlow stores:

- `lifeflow.json`: donors, blood units, requests, fulfilments, appointments,
  audit logs, format version, revision, and checksum;
- `donors.json`: donor login accounts;
- `hospitals.json`: hospital login accounts;
- `backups/`: verified main-state backups;
- `lifeflow.lock`: the single-instance storage lock.

The main state store uses SHA-256 checksum verification, validated temporary
files, atomic replacement when supported, automatic backups, recovery, and
Version 1 to Version 2 migration. Account registries also use atomic JSON file
replacement.

## Submission note

The committed Group 23 report contains the member details required by the
official brief. Treat it as a submission artifact and confirm the team's
permission before redistributing it outside the course submission process.
