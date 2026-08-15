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
- Pipe-delimited text files for data persistence.
- Meaningful request-priority and exact-group matching simulation.

## Compile, test, and run

The code uses Java 17 language compatibility and has no external dependencies.

```bash
mkdir -p out
javac --release 17 -Xlint:all -d out $(find src test -name '*.java')
java -ea -cp out lifeflow.AllTests
java -cp out lifeflow.Main
```

Data files are created inside `data/` after the first successful save.

## Ready-made demo

The repository includes fictional demonstration data. Load it before the live
demo with:

```bash
cp data/demo/*.txt data/
java -cp out lifeflow.Main
```

The scenario contains a regular request and an emergency request. Processing
the next request selects the emergency request and matches two `O_NEG` units.

## Generated deliverables

- `output/pdf/LifeFlow_Project_Report.pdf` - eight-section report with UML,
  testing evidence, and Swing screenshots.
- `output/presentation/LifeFlow_Presentation.pptx` - editable ten-slide deck
  with speaker notes and source links.
- `output/pdf/LifeFlow_Presentation.pdf` - PDF copy of the presentation.

## Project structure

```text
src/lifeflow/
├── Main.java
├── model/        Donors, units, requests, enums
├── service/      Inventory and matching logic
├── persistence/  Text-file persistence
└── ui/           Dashboard, sidebar, pages, dialogs, and shared theme

test/lifeflow/    Assertion-based automated tests
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

## Submission notes

- The GitHub URL is included in the generated report.
- Add the required team names, student IDs, class code, programme, and
  NRIC/passport numbers only to the private LMS submission copy.
- Do not publish government identifiers in a public repository.
