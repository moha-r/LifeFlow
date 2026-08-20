# AGENTS.md

Java 17+ Swing desktop app (Maven). UI is English; the user communicates in Arabic.

## Commands
- Build + test (always clean — incremental compile goes stale):
  `rm -rf target/classes target/test-classes && ./mvnw clean test`
- Test count summary: `./mvnw test 2>&1 | grep -E 'Tests run: [0-9]+, Failures' | tail -1`
- Package jar: `./mvnw -q clean package -DskipTests` → `target/lifeflow.jar`
- Run: `pkill -f lifeflow.jar; nohup java -jar target/lifeflow.jar > /tmp/lifeflow_run.log 2>&1 & disown`
- Verify the window: `swift -e 'import CoreGraphics; ... CGWindowListCopyWindowInfo ...'` (owner "Main", name contains "LifeFlow")

## Architecture traps (learned the hard way)
- **`LifeFlowState` getters return deep copies.** Any mutation must be done on lists captured from a single `getX()` call and passed to `commit(...)` — passing a second copy silently loses mutations (was a real bug in `completeDonationAppointment`).
- **Checksum fragility:** `JsonLifeFlowStore` computes SHA-256 over the canonical Jackson serialization of `ChecksumContent{data, formatVersion, revision}`. **Adding/removing a field in payload DTOs (e.g. `RequestData`) invalidates every JSON file written by older builds** → app shows "JSON storage checksum does not match" on load. Mitigation: re-serialize stored envelopes through the new POJO classes and recompute checksums, or bump the format version with a migration.
- **Infinite-loop trap:** never generate ids as `"H" + (size + 1)` / `"H" + (size + 2)` in a while loop — both candidates can be taken after deletions (fixed: monotonic max+1 scan).
- Controller must guard **both FULFILLED and CANCELLED** records (updatePendingRequest, processSpecificRequest, declineRequest, unit edit paths).
- Test classes ending in `Test` are JUnit 5 (`@Test`); classes ending in `Tests` (e.g. `LifeFlowControllerTests`) are legacy manual runners invoked from `AllTests` — add new tests to a `*Test` class so Surefire runs them.
- Surefire can be flaky → the clean-build command above is the source of truth.