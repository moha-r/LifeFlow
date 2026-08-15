# LifeFlow Architecture

## Design goal

Keep the implementation simple enough for every team member to explain while
demonstrating the required object-oriented concepts through real behaviour.

## Package responsibilities

- `lifeflow.model`: state and single-object rules.
- `lifeflow.service`: inventory queries and cross-object matching.
- `lifeflow.persistence`: text-file mapping only.
- `lifeflow.ui`: Swing input, tables, messages, and orchestration.
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
    }
    class BloodUnit {
        -String id
        -String donorId
        -BloodType bloodType
        -LocalDate donationDate
        -LocalDate expiryDate
        -UnitStatus status
        +isAvailable(LocalDate) boolean
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
    LifeFlowFrame --> MatchingService
    LifeFlowFrame --> FileManager
```

## Main data flow

```text
Text files -> FileManager -> ArrayLists/BloodInventory -> Swing tables
Swing form -> model validation -> service simulation -> updated collections
Updated collections -> FileManager -> text files
```

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
