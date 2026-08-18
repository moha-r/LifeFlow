# Six-Digit Record IDs

## Objective

LifeFlow will display automatically generated record IDs with six numeric digits:

- donors: `D000001`
- blood units: `U000001`
- blood requests: `R000001`

Six digits are a minimum width, not a maximum. After `D999999`, for example, the next ID is `D1000000`, so generation does not stop at one million records.

## Design

`LifeFlowController` remains the single place that calculates new IDs. Its existing `nextId` helper will continue to scan the saved IDs for the matching prefix, find the greatest numeric suffix, and add one. Only the minimum display width changes from three digits to six digits.

The Donors, Inventory, and Requests dialogs continue to request their generated IDs from the controller. Their ID fields remain read-only. JSON storage requires no migration because IDs are stored as strings.

## Compatibility and Error Handling

Existing IDs remain valid. For example, saved IDs `D001` and `D000009` are both recognised numerically, and the next generated value is `D000010`. Custom IDs without the expected prefix are preserved but do not affect the automatic sequence.

If the numeric suffix reaches Java's maximum `long` value, the controller reports that no further automatic IDs are available instead of wrapping to a duplicate or negative value.

## Verification

Controller tests will verify:

- empty data starts at `D000001`, `U000001`, and `R000001`;
- older short IDs are read correctly and produce six-digit successors;
- values beyond six digits expand instead of being truncated;
- the full Maven test suite and Java 17 build remain successful.

The application will then be rebuilt and restarted from the new runnable JAR.
