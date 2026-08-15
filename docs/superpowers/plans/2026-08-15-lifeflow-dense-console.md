# LifeFlow Dense Operations Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the inconsistent card dashboard with the approved bounded, dense Swing operations console while preserving all domain and persistence behavior.

**Architecture:** Keep `LifeFlowController`, models, services, and file formats unchanged. Build four focused shared UI classes for bounded layout, page structure, sidebar state, and Java2D icons; then rebuild the five screens on those shared rules. Drive every structural change with headless Swing assertions and finish with live visual checks at three window sizes.

**Tech Stack:** Java 17, standard Swing/AWT, Java assertions, existing text-file persistence; no external libraries.

---

## File Structure

- Create `src/lifeflow/ui/BoundedContentPanel.java`: center one child and cap its width at 1320 px.
- Create `src/lifeflow/ui/PageShell.java`: shared page title, subtitle, actions, toolbar, body, and footer structure.
- Create `src/lifeflow/ui/NavigationIcon.java`: small Java2D icons with consistent geometry.
- Create `src/lifeflow/ui/SidebarPanel.java`: dark navigation, one active page, and compact notice.
- Modify `src/lifeflow/ui/UiTheme.java`: dense colors, fonts, and spacing constants.
- Modify `src/lifeflow/ui/UiComponents.java`: dense buttons, placeholders, tables, badges, banners, and dialogs.
- Modify `src/lifeflow/ui/LifeFlowFrame.java`: compose the shared application shell.
- Modify all five screen panels: use bounded page structure and approved dense layouts.
- Modify `test/lifeflow/ModernUiTests.java`: structural and state assertions for the new design system.
- Do not modify `docs/project-report.md`, `docs/presentation-script.md`, any PDF, or any PPTX.

### Task 1: Shared layout tests and bounded content

**Files:**
- Create: `src/lifeflow/ui/BoundedContentPanel.java`
- Create: `src/lifeflow/ui/PageShell.java`
- Modify: `src/lifeflow/ui/UiTheme.java`
- Test: `test/lifeflow/ModernUiTests.java`

- [ ] **Step 1: Write failing structural tests**

Add assertions that `BoundedContentPanel` caps a child at 1320 px and that
`PageShell` exposes named `pageHeader`, `pageToolbar`, and `pageBody` sections:

```java
private static void boundedContentAndPageShellStayConsistent() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        JPanel child = new JPanel();
        BoundedContentPanel bounded = new BoundedContentPanel(child);
        bounded.setSize(1600, 700);
        bounded.doLayout();
        assert child.getWidth() == UiTheme.CONTENT_MAX_WIDTH;

        PageShell shell = new PageShell("Donor registry", "Manage donors.");
        assert namedComponent(shell, "pageHeader") != null;
        assert namedComponent(shell, "pageToolbar") != null;
        assert namedComponent(shell, "pageBody") != null;
    });
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
verify_out=$(mktemp -d)
rg --files src test -g '*.java' | sort | xargs javac --release 17 -d "$verify_out"
```

Expected: compilation fails because `BoundedContentPanel`, `PageShell`, and
`UiTheme.CONTENT_MAX_WIDTH` do not exist.

- [ ] **Step 3: Add dense theme metrics and minimal shared layout classes**

Add these constants to `UiTheme`:

```java
public static final int SPACE_XS = 8;
public static final int SPACE_SM = 12;
public static final int SPACE_MD = 18;
public static final int SPACE_LG = 22;
public static final int SIDEBAR_WIDTH = 224;
public static final int UTILITY_HEIGHT = 56;
public static final int CONTENT_MAX_WIDTH = 1320;
public static final Color SIDEBAR = new Color(0x172033);
public static final Color SIDEBAR_HOVER = new Color(0x263147);
public static final Color ROW_ALT = new Color(0xFBFCFD);
```

Implement `BoundedContentPanel` with a custom `doLayout()` that centers its
single child and uses `Math.min(getWidth(), CONTENT_MAX_WIDTH)`. Implement
`PageShell` with named header, toolbar, body, and footer panels and setters for
actions, toolbar, body, and footer content.

- [ ] **Step 4: Compile and run all tests**

Run:

```bash
verify_out=$(mktemp -d)
rg --files src test -g '*.java' | sort | xargs javac --release 17 -Xlint:all -d "$verify_out"
java -Djava.awt.headless=true -ea -cp "$verify_out" lifeflow.AllTests
```

Expected: `All tests passed.`

- [ ] **Step 5: Commit the shared layout foundation**

```bash
git add src/lifeflow/ui/UiTheme.java src/lifeflow/ui/BoundedContentPanel.java src/lifeflow/ui/PageShell.java test/lifeflow/ModernUiTests.java
git commit -m "feat: add bounded dense page layout"
```

### Task 2: Sidebar and application shell

**Files:**
- Create: `src/lifeflow/ui/NavigationIcon.java`
- Create: `src/lifeflow/ui/SidebarPanel.java`
- Modify: `src/lifeflow/ui/LifeFlowFrame.java`
- Modify: `src/lifeflow/ui/UiComponents.java`
- Test: `test/lifeflow/ModernUiTests.java`

- [ ] **Step 1: Write failing navigation-state tests**

```java
private static void sidebarHasExactlyOneActivePage() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        SidebarPanel sidebar = new SidebarPanel(page -> { });
        sidebar.showActive("Dashboard");
        assert sidebar.getActiveCount() == 1;
        sidebar.showActive("Donors");
        assert sidebar.getActiveCount() == 1;
        assert sidebar.isActive("Donors");
        assert !UiTheme.SIDEBAR_HOVER.equals(UiTheme.CORAL);
    });
}
```

- [ ] **Step 2: Compile and verify the missing sidebar fails the test**

Use the Task 1 compile command. Expected: missing `SidebarPanel` symbols.

- [ ] **Step 3: Implement icons, sidebar, and shell**

`NavigationIcon` implements `Icon` and paints one 16 x 16 geometric symbol for
each page using `Graphics2D`. `SidebarPanel` stores buttons in a map, uses coral
only for the active item, uses navy-gray for hover, and exposes `showActive`,
`getActiveCount`, and `isActive`.

Replace `LifeFlowFrame.buildSidebar()` with `SidebarPanel`, add the 56 px utility
bar, keep `CardLayout`, and put every screen inside the shared bounded content
area. Remove Unicode navigation glyphs and the oversized bottom notice.

- [ ] **Step 4: Run all tests**

Expected: `All tests passed.` with `-Xlint:all`.

- [ ] **Step 5: Commit the shell**

```bash
git add src/lifeflow/ui/NavigationIcon.java src/lifeflow/ui/SidebarPanel.java src/lifeflow/ui/LifeFlowFrame.java src/lifeflow/ui/UiComponents.java test/lifeflow/ModernUiTests.java
git commit -m "feat: rebuild the LifeFlow application shell"
```

### Task 3: Dense dashboard

**Files:**
- Modify: `src/lifeflow/ui/DashboardPanel.java`
- Modify: `src/lifeflow/ui/UiComponents.java`
- Test: `test/lifeflow/ModernUiTests.java`

- [ ] **Step 1: Write failing dashboard structure assertions**

Assert named components `dashboardMetrics`, `inventoryStatusTable`,
`priorityRequestPanel`, and `requestQueueTable` exist after headless construction.
Assert the inventory table has four columns: blood type, available, stock level,
and status.

- [ ] **Step 2: Run the tests and verify the new table assertions fail**

Expected: named components are absent from the current card dashboard.

- [ ] **Step 3: Rebuild the dashboard**

Use `PageShell` and these sections:

```java
PageShell shell = new PageShell("Operations overview",
        "Monitor available blood stock and process urgent demand.");
shell.setActions(buildQuickActions());
shell.setBody(buildOperationsBody());
```

Build compact metrics, an eight-row inventory status table, a next-request
panel, and a request queue table. Keep all counts and request selection backed by
`LifeFlowController`. Remove stretched quick-action buttons and the eight-cell
blood card row.

- [ ] **Step 4: Run all tests**

Expected: all dashboard and existing controller tests pass.

- [ ] **Step 5: Commit the dashboard**

```bash
git add src/lifeflow/ui/DashboardPanel.java src/lifeflow/ui/UiComponents.java test/lifeflow/ModernUiTests.java
git commit -m "feat: build dense operations dashboard"
```

### Task 4: Shared data workspaces and clear tables

**Files:**
- Modify: `src/lifeflow/ui/UiComponents.java`
- Modify: `src/lifeflow/ui/DonorsPanel.java`
- Modify: `src/lifeflow/ui/InventoryPanel.java`
- Modify: `src/lifeflow/ui/RequestsPanel.java`
- Test: `test/lifeflow/ModernUiTests.java`

- [ ] **Step 1: Write failing table and toolbar tests**

For each screen, find its table and assert:

```java
assert table.getShowHorizontalLines();
assert table.getShowVerticalLines();
assert table.getIntercellSpacing().width > 0;
assert namedComponent(panel, "pageToolbar") != null;
assert namedComponent(panel, "searchField") instanceof JTextField;
assert !((JTextField) namedComponent(panel, "searchField")).getText().isBlank();
```

The search field uses visible placeholder text that clears on focus and returns
when empty.

- [ ] **Step 2: Run the tests and verify current grid-free tables fail**

Expected: failure because current tables call `setShowGrid(false)` and use zero
inter-cell spacing.

- [ ] **Step 3: Implement the shared dense table style**

Update `UiComponents.configureTable` to enable subtle horizontal and vertical
lines, alternate row backgrounds, compact 38 px rows, deliberate header height,
and correct numeric alignment. Add a placeholder field helper named
`searchField` and dense status renderers.

- [ ] **Step 4: Recompose all three data screens**

Use `PageShell` on every screen. Put Add in the page header. Put visible search,
relevant filters, record count, and Edit selected in the shared toolbar. Keep
the existing add/edit dialogs and controller calls, but apply one dialog style,
one inline error location, and consistent Enter/Escape behavior.

- [ ] **Step 5: Run all tests**

Expected: all table, dialog construction, controller, and persistence tests pass.

- [ ] **Step 6: Commit the data workspaces**

```bash
git add src/lifeflow/ui/UiComponents.java src/lifeflow/ui/DonorsPanel.java src/lifeflow/ui/InventoryPanel.java src/lifeflow/ui/RequestsPanel.java test/lifeflow/ModernUiTests.java
git commit -m "feat: unify dense data workspaces"
```

### Task 5: Matching flow and inline feedback

**Files:**
- Modify: `src/lifeflow/ui/MatchingPanel.java`
- Modify: `src/lifeflow/ui/UiComponents.java`
- Test: `test/lifeflow/ModernUiTests.java`

- [ ] **Step 1: Write failing matching-state tests**

Construct Matching with an empty controller and assert `matchingEmptyState` is
visible and processing is disabled. Construct with an emergency request and two
matching units and assert named components `matchingSteps`, `compatibleUnits`,
and `matchingResult` exist.

- [ ] **Step 2: Run and verify the test fails against the current two-card screen**

Expected: the named flow components are absent.

- [ ] **Step 3: Implement the approved matching workspace**

Use `PageShell`. Show the three stages, selected request metadata, required and
available quantities, a table preview of compatible units, the atomic-operation
notice, and one process button. Preserve `controller.processNextRequest` and
display success or shortage inline. Use a modal only for `IOException`.

- [ ] **Step 4: Run all tests**

Expected: `All tests passed.`

- [ ] **Step 5: Commit the matching flow**

```bash
git add src/lifeflow/ui/MatchingPanel.java src/lifeflow/ui/UiComponents.java test/lifeflow/ModernUiTests.java
git commit -m "feat: clarify the matching workflow"
```

### Task 6: Final verification and visual review

**Files:**
- Modify only UI or UI tests if verification reveals a defect.
- Do not modify report, slide, PDF, or PPTX files.

- [ ] **Step 1: Run a fresh Java 17 build and assertion suite**

```bash
verify_out=$(mktemp -d)
rg --files src test -g '*.java' | sort | xargs javac --release 17 -Xlint:all -d "$verify_out"
java -Djava.awt.headless=true -ea -cp "$verify_out" lifeflow.AllTests
```

Expected: no compiler warnings and `All tests passed.`

- [ ] **Step 2: Launch and inspect the application**

```bash
rg --files src -g '*.java' | sort | xargs javac --release 17 -d out
java -cp out lifeflow.Main
```

Inspect Dashboard, Donors, Inventory, Requests, Matching, and add/edit dialogs at
1050 x 680, 1280 x 780, and maximized size.

- [ ] **Step 3: Verify acceptance details**

Confirm one active navigation item, distinct hover, visible table columns,
visible search placeholder, bounded content, no stretched controls, no overlap,
and correct empty/success/shortage states.

- [ ] **Step 4: Verify frozen artifacts and repository hygiene**

```bash
git diff --check
git status --short
git diff --name-only 84bebbd..HEAD | rg '(^output/|project-report|presentation-script)' && exit 1 || true
```

Expected: no report, PDF, PPTX, or slide paths changed.

- [ ] **Step 5: Push the interface branch**

```bash
git push origin codex/modern-ui
```

Expected: the private GitHub branch and existing pull request update without
regenerating documentation artifacts.
