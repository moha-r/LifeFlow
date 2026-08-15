# LifeFlow: Blood Donation and Emergency Matching Simulator

## Project Report Draft

Course: BIT1123/BISE2093/DIT1113 Object Oriented Programming
SDG: United Nations Sustainable Development Goal 3 - Good Health and Well-being

GitHub repository: https://github.com/moha-r/LifeFlow

This is the technical submission draft. The private LMS copy must include each
member's full name, student ID, class code, programme, and NRIC/passport number,
while keeping the repository URL shown above.

## 1. Introduction and SDG Background

Reliable access to suitable blood is an important part of healthcare delivery.
Blood banks must record donations, monitor stock, handle requests, and give urgent
cases appropriate priority. LifeFlow is a desktop educational simulation that
models these activities using Java and object-oriented programming.

The application aligns with SDG 3 because it demonstrates how organised donor
records, visible stock, and consistent request prioritisation can support health
services. It does not make clinical decisions and is not a substitute for real
blood-bank testing or professional judgement.

## 2. Problem Statement

A basic record system can store donors and units, but storage alone does not show
how limited blood stock should be checked against competing requests. A useful
simulation must distinguish emergency requests, reject expired or used units,
require the requested quantity, and preserve its data between sessions.

The project therefore addresses the following problem: how can a small desktop
application clearly simulate the recording, prioritisation, and exact-group
matching of blood-bank data without the complexity of a hospital information
system?

## 3. System Objectives

1. Register donors with an ID, name, age, weight, blood group, and last donation.
2. Apply simplified eligibility conditions before recording a new blood unit.
3. Maintain visible stock for all eight ABO and Rh blood groups.
4. Create regular and emergency requests through one consistent interface.
5. Select emergency requests before regular requests using runtime polymorphism.
6. Match only available, non-expired units of the exact requested blood group.
7. Avoid partial fulfilment when the full requested quantity is unavailable.
8. Save and restore donors, units, and request subclasses using text files.

## 4. OOP Design and UML

LifeFlow separates model objects, services, persistence, and the Swing interface.
The central inheritance hierarchy is `BloodRequest`, an abstract superclass with
two subclasses: `RegularRequest` and `EmergencyRequest`. Both override
`getPriority()`. The matching service stores and reads them through superclass
references, so Java selects the correct overridden method at runtime.

Encapsulation is implemented with private fields and controlled accessors.
`BloodInventory` owns an `ArrayList<BloodUnit>`, while its stock summary uses a
`HashMap<BloodType, Integer>`. `FileManager` maps the collections to three
pipe-delimited files. The full UML diagram is provided in `docs/architecture.md`
and in the generated PDF report.

## 5. Implementation Details

### Donor eligibility

For the educational simulation, a donor is accepted when the entered donation
date is not in the future, age is 18-60, weight is at least 45 kg, and either no
previous donation exists or three months have passed. These simplified rules are
based on Malaysian Ministry of Health information and are clearly presented as
simulation rules.

### Request processing

`MatchingService.findNextPending()` loops through pending requests. A request
replaces the current choice when it has a higher polymorphic priority, or when it
has the same priority but an earlier date. `match()` then filters inventory by
exact blood group, `AVAILABLE` status, and expiry date. If the number of units is
insufficient, no state changes. Otherwise, the required units become `USED` and
the request becomes `FULFILLED`.

### Swing interface

The application contains one `JFrame` with Donors, Blood Inventory, Blood
Requests, and Matching tabs. Tables are read-only and sortable. Forms validate
required fields, duplicate IDs, numeric values, dates, quantities, and the file
delimiter. Messages are displayed with `JOptionPane`, while the Matching tab
shows the selected request and result.

### Persistence

Data is saved after successful operations and when the window closes. Missing
files create an empty starting state. Malformed lines identify their file and line
number, and the main window is not opened, avoiding accidental overwrite of
unreadable data.

## 6. Testing and Sample Outputs

The project includes assertion-based automated tests that run without external
libraries. They verify:

- minimum and maximum age and weight boundaries;
- the three-month interval and future-date rejection;
- expiry and `USED` status filtering;
- runtime priority differences between regular and emergency requests;
- emergency-first and oldest-first queue selection;
- exact-group matching and full-quantity behaviour;
- `ArrayList` inventory and `HashMap` counts;
- text-file round trips and restoration of request subclasses;
- empty missing files and descriptive malformed-line errors.

Verified command:

```text
javac --release 17 -Xlint:all -d out <all source and test files>
java -ea -cp out lifeflow.AllTests
All tests passed.
```

Example successful output:

```text
Request R002 was fulfilled successfully.
Matched unit(s): U001, U002
```

Example insufficient-stock output:

```text
Request R003 (EMERGENCY) needs 2 unit(s) of O_NEG,
but only 1 is available. The request remains pending.
```

The generated PDF also includes screenshots of the Blood Requests and Matching
tabs using fictional demonstration data.

## 7. Discussion and Limitations

LifeFlow demonstrates the required OOP concepts through behaviour rather than
unused classes. Its small design is easy to test and explain, and file storage
makes the demonstration repeatable without database setup.

The simulation deliberately uses exact ABO/Rh matching only. It does not model
crossmatching, antibodies, blood components, laboratory screening, clinical
exceptions, user accounts, networking, or concurrent access. Eligibility rules
are simplified and must not be used to determine whether a real person can
donate. Pipe-delimited files are suitable for this class project but not for a
multi-user production system.

## 8. Conclusion

LifeFlow meets the project goal by combining a working Swing interface with a
meaningful priority and matching simulation. Encapsulation protects object state;
inheritance, abstraction, overriding, and runtime polymorphism define request
behaviour; collections manage data; and File I/O provides persistence. The result
is a focused SDG 3 application that is realistic enough to demonstrate software
design while remaining appropriate for an introductory OOP project.

## References

1. City University Malaysia. BIT1123/BISE2093/DIT1113 Object Oriented
   Programming Final Project brief.
2. Hospital Sultan Ismail, Ministry of Health Malaysia. "Syarat-Syarat Penderma
   Darah Yang Layak." https://jknjohor.moh.gov.my/hsi/syarat-syarat-penderma-darah-yang-layak-2/
3. Hospital Wanita dan Kanak-Kanak Sabah, Ministry of Health Malaysia. "Kriteria
   Penderma Darah." https://jknsabah.moh.gov.my/hwkks/en/?id=178&option=com_sppagebuilder&view=page
4. Ministry of Health Malaysia. Handbook on Clinical Use of Blood.
   https://portal.appshtaa.moh.gov.my/images/ProfilJabatan/Transfusi/handbook.pdf
