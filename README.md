# LifeFlow

GitHub repository: https://github.com/moha-r/LifeFlow

LifeFlow is a Java Swing educational simulation for recording blood donors,
managing blood units, prioritising regular and emergency requests, and matching
the highest-priority request with available units of the same ABO and Rh group.

> This project is an educational simulation. It must not be used for real
> donation eligibility, crossmatching, transfusion, diagnosis, or treatment.

## Requirements covered

- Modern Java Swing GUI with a dashboard, sidebar, and five focused screens.
- Encapsulation through private fields and accessors.
- Abstraction with the abstract `BloodRequest` class.
- Inheritance through `RegularRequest` and `EmergencyRequest`.
- Runtime polymorphism through `ArrayList<BloodRequest>` and `getPriority()`.
- `ArrayList` collections and a `HashMap<BloodType, Integer>` stock summary.
- One checksum-verified JSON snapshot for local data persistence.
- Atomic saves, automatic JSON backups, recovery, and a single-instance lock.
- Meaningful request-priority and exact-group matching simulation.

## Compile, test, and run

The project uses Java 17, Maven Wrapper, Jackson for JSON, and JUnit 5.

```bash
./mvnw clean test
./mvnw clean package
java -jar target/lifeflow.jar
```

The first launch creates `~/.lifeflow/lifeflow.json` and a verified backup.
Set `LIFEFLOW_DATA_DIR` before launching to use another local folder. The
storage path shown in the application status bar is the file actually in use.

## Ready-made demo

For a clean demonstration, start the application with a temporary local store:

```bash
LIFEFLOW_DATA_DIR="$(mktemp -d)" java -jar target/lifeflow.jar
```

Register donors and units, then create one regular request and one emergency
request. Processing the queue selects the highest-priority request that can be
fulfilled in full: an emergency request leads the queue when its exact stock is
available, otherwise the first regular request with full stock is processed.
Matching uses exact ABO/Rh groups and FEFO ordering; it never partially fulfils
a request.

## Generated deliverables

- `output/pdf/LifeFlow_Project_Report.pdf` - eight-section report with UML,
  testing evidence, and Swing screenshots.
- `output/presentation/LifeFlow_Presentation.pptx` - editable ten-slide deck
  with speaker notes and source links.
- `output/pdf/LifeFlow_Presentation.pdf` - PDF copy of the presentation.

## Project structure

```text
src/main/java/lifeflow/
├── Main.java
├── model/        Donors, units, requests, enums
├── service/      Inventory and matching logic
├── persistence/  Atomic JSON storage, backups, locking, recovery
└── ui/           Dashboard, sidebar, pages, dialogs, and shared theme

src/test/java/    JUnit 5 and legacy assertion-based regression tests
docs/             Architecture, report source, presentation script
output/           Generated PDF and PowerPoint deliverables
```

## Demonstration flow

1. Review live stock and the next priority request on the Dashboard.
2. Add an eligible donor.
3. Record a blood unit for that donor.
4. Create one regular and one emergency request.
5. Process the next request and show that the emergency request is selected.
6. Restart the application and confirm that the saved data returns.

## Local storage safety

- `lifeflow.json` is the single source of truth for donors, units, requests,
  and fulfilment audit records.
- Saves use a validated temporary file followed by an atomic replacement.
- A SHA-256 checksum detects manual edits and incomplete files.
- The last two backups are kept under `~/.lifeflow/backups/`.
- A damaged primary file is never overwritten silently; the application asks
  before restoring a verified backup.
- Runtime JSON, lock, backup, and recovery files are ignored by Git.

## Submission notes

- The GitHub URL is included in the generated report.
- Add the required team names, student IDs, class code, programme, and
  NRIC/passport numbers only to the private LMS submission copy.
- Do not publish government identifiers in a public repository.
