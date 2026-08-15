# LifeFlow Architecture

## Design goal

Keep the implementation simple enough for every team member to explain while
demonstrating the required object-oriented concepts through real behaviour.

## Package responsibilities

- `lifeflow.model`: state and single-object rules.
- `lifeflow.service`: inventory queries, matching, validation, and UI-facing operations.
- `lifeflow.persistence`: text-file mapping only.
- `lifeflow.ui`: dashboard, sidebar navigation, data pages, dialogs, and theming.
- `lifeflow.Main`: application start-up and data loading.

## UML class diagram

```mermaid
classDiagram
    class Donor {
        -String id
        -String name
        -int age
        -double weightKg
        -BloodType bloodType
        -LocalDate lastDonationDate
        +isEligible(LocalDate) boolean
        +recordDonation(LocalDate) void
        +updateDetails(...) void
    }
    class BloodUnit {
        -String id
        -String donorId
        -BloodType bloodType
        -LocalDate donationDate
        -LocalDate expiryDate
        -UnitStatus status
        +isAvailable(LocalDate) boolean
        +updateExpiryDate(LocalDate) void
    }
    class BloodRequest {
        <<abstract>>
        -String id
        -String requesterName
        -BloodType bloodType
        -int quantity
        -LocalDate requestDate
        -RequestStatus status
        +getPriority() int
        +getKind() String
        +updatePendingDetails(...) void
    }
    class RegularRequest
    class EmergencyRequest
    class BloodInventory {
        -ArrayList~BloodUnit~ units
        +getAvailableUnits(BloodType, LocalDate) ArrayList~BloodUnit~
        +getStockCounts(LocalDate) HashMap~BloodType,Integer~
    }
    class MatchingService {
        +findNextPending(List~BloodRequest~) BloodRequest
        +match(BloodRequest, LocalDate) ArrayList~BloodUnit~
    }
    class FileManager
    class LifeFlowController
    class LifeFlowFrame

    BloodRequest <|-- RegularRequest
    BloodRequest <|-- EmergencyRequest
    Donor "1" --> "0..*" BloodUnit
    BloodInventory o-- BloodUnit
    MatchingService --> BloodInventory
    MatchingService --> BloodRequest
    FileManager --> Donor
    FileManager --> BloodUnit
    FileManager --> BloodRequest
    LifeFlowController --> MatchingService
    LifeFlowController --> FileManager
    LifeFlowFrame --> LifeFlowController
```

## Main data flow

```text
Text files -> FileManager -> LifeFlowController -> dashboard and tables
Swing dialog -> controller validation -> model/service operation
Updated collections -> FileManager -> text files -> refresh all five screens
```

## UI composition

`LifeFlowFrame` contains the fixed sidebar, a `CardLayout`, and the temporary
status bar. `DashboardPanel` shows live counts, blood-group availability, quick
actions, and the next priority request. Donors, inventory, requests, and
matching each use a focused panel, while `UiTheme` and `UiComponents` keep
colors, typography, buttons, cards, tables, and status badges consistent.

## Persistence schemas

```text
donors.txt
id|name|age|weightKg|bloodType|lastDonationDate

blood_units.txt
id|donorId|bloodType|donationDate|expiryDate|status

requests.txt
id|kind|requesterName|bloodType|quantity|requestDate|status
```

Dates use `yyyy-MM-dd`. Missing files represent empty collections. A malformed
line stops start-up and reports its file and line number to prevent silent data
loss.
