# LifeFlow Dense Operations Console Design

Date: 2026-08-15

## Purpose

Replace the inconsistent card-based Swing interface with a dense, coherent
operations console while preserving the existing LifeFlow domain behavior,
controller operations, and text-file format.

The approved direction is **Dense Operations Console** with a bounded content
area. The interface must remain understandable to a new user, simple enough to
explain in an OOP presentation, and compatible with Java 17 without external
libraries.

## Root Cause

The current problem is not Swing or the color palette. Each screen independently
chooses its layout managers, preferred sizes, spacing, and toolbar structure.
There is no shared layout system controlling page width, navigation state,
tables, or responsive behavior.

This produces the visible symptoms:

- `GridLayout` stretches cards and buttons across all available width.
- Hover and active navigation states use the same color.
- Unicode navigation symbols have inconsistent geometry and alignment.
- Tables explicitly disable grid lines and inter-cell spacing, making columns
  visually disappear.
- Search fields have tooltips but no visible placeholders.
- Headers, filters, actions, and empty states differ between screens.
- Uniform white cards and large empty regions weaken visual hierarchy.

The fix must replace the independent layout decisions with shared structural
components. Patching each symptom separately is out of scope.

## Approved Visual Direction

### Application shell

- Dark navy sidebar with a fixed width of 224 px.
- White utility bar with a height of 56 px.
- Resizable window with a minimum size of 1050 x 680.
- Main content centered inside a maximum width of 1320 px.
- Dense spacing scale based on 8, 12, 18, and 22 px.
- Coral is reserved for the primary action, active navigation, and urgent state.
- Only one navigation item may appear active.
- Hover uses a visibly different neutral state from active.
- Navigation icons use small Java2D-painted geometric icons instead of Unicode
  glyphs or external icon libraries.

### Dashboard

The dashboard becomes an operations overview rather than a grid of oversized
cards:

1. Compact donor, available-unit, pending-request, and emergency metrics.
2. Inventory status table showing blood type, available quantity, stock level,
   and status.
3. A focused next-priority-request panel with required and available quantities.
4. A compact request queue table with search and filtering.
5. Small top-level actions for adding a donor, unit, or request.

Dashboard content must remain bounded and readable when the window is maximized.

### Data workspaces

Donors, Inventory, and Requests share one structure:

1. Page title and short operational description.
2. One primary add action in the page header.
3. One toolbar containing visible search placeholder text, relevant filters,
   record count, and the selected-row edit action.
4. A dense table with clear headers, subtle vertical and horizontal separators,
   alternating row backgrounds, deliberate column widths, and aligned numeric
   values.
5. A compact footer showing the visible record count.
6. A clear empty state inside the table area when no records exist or no search
   results match.

The pages must use the same component hierarchy and spacing. They may differ
only in their columns, filters, and domain-specific status badges.

### Add and edit dialogs

- All add and edit operations use one shared modal-dialog style.
- Labels, fields, inline validation, footer actions, and spacing are consistent.
- Enter submits and Escape cancels.
- IDs remain immutable during editing.
- Locked blood type, donation date, unit status, and fulfilled request rules
  remain enforced by the controller and are represented visually as disabled
  fields with a short reason.
- No delete action is introduced.

### Matching workspace

Matching is presented as an explicit operational flow:

1. Display the highest-priority pending request.
2. Display required and available compatible quantities.
3. Show the compatible unit rows that will be used.
4. Explain that fulfillment is atomic before processing.
5. Process the request using the existing matching service.
6. Show the result inline, including request ID and used unit IDs.

Insufficient stock displays an inline warning and confirms that no state changed.
An empty queue displays a purposeful empty state and disables processing.

## UI Architecture

`LifeFlowFrame` remains the application window but only owns the application
shell, navigation, status messaging, and page refresh coordination.

Shared UI responsibilities are divided as follows:

- `UiTheme`: colors, fonts, and the approved spacing scale.
- `UiComponents`: buttons, fields, dialogs, status badges, and table renderers.
- `BoundedContentPanel`: centers content and enforces the 1320 px maximum width.
- `PageShell`: supplies the shared title, toolbar, body, and optional footer
  structure for every page.
- `SidebarPanel`: owns brand, navigation items, active state, and compact
  educational notice.
- `NavigationIcon`: paints consistent geometric icons using Java2D.
- Screen panels: provide only their domain-specific data and actions.

These four shared structural helpers will be implemented as focused classes.
No external UI framework or icon library may be added.

## Data Flow

The domain and persistence data flow remains unchanged:

1. A user action starts in a screen panel or modal dialog.
2. The screen calls `LifeFlowController`.
3. The controller validates, updates the model, and persists successful changes.
4. `LifeFlowFrame` refreshes every screen after a successful mutation.
5. Inline feedback reports success, validation failure, insufficient inventory,
   or storage failure.

The design does not move domain validation back into screen panels.

## Error and Feedback Behavior

- Field validation is shown below the relevant dialog content.
- Successful add, edit, and matching operations use a temporary inline status
  banner.
- Insufficient stock and empty queue states are represented inside Matching.
- Only critical file-loading or file-saving failures use blocking modal dialogs.
- Disabled actions must look disabled and explain their constraint where useful.

## Responsive Rules

- At 1280 x 780 and larger, content uses the full dense console layout up to the
  1320 px maximum width.
- At the 1050 x 680 minimum, spacing compresses and long toolbars wrap or split
  into two rows without overlapping content.
- Tables retain horizontal scrolling if their minimum useful column widths cannot
  fit.
- The sidebar remains 224 px; page content owns the remaining width.
- Maximizing the window must not stretch controls or create oversized empty
  cards.

## Testing and Acceptance

Automated UI assertions will verify:

- Exactly one sidebar item is active after navigation.
- Active and hover colors are distinct.
- Data tables enable visible row and column separation.
- Search fields expose visible placeholder text.
- The three data workspaces use the shared page and toolbar structure.
- Dashboard counts and matching state still refresh after mutations.
- Empty, insufficient-stock, success, used-unit, and fulfilled-request states
  remain correct.

Manual visual verification will cover:

- Dashboard, Donors, Inventory, Requests, Matching, and each modal dialog.
- Window sizes 1050 x 680, 1280 x 780, and a maximized desktop window.
- No clipped controls, overlapping labels, ambiguous navigation state, missing
  column boundaries, or large unintended empty regions.

All existing Java assertion tests must continue to pass with Java 17 and no
external dependencies.

## Non-Goals and Frozen Artifacts

- Domain eligibility, request priority, and matching rules do not change.
- The file format does not change.
- No login, deletion, database, network service, or complex charting is added.
- Existing project report PDFs, presentation PDFs, PowerPoint files, and slide
  content are frozen. They must not be regenerated or edited until the user
  explicitly approves the completed interface and requests the documentation
  update.

## Completion Criteria

The redesign is complete when every screen follows the approved dense console
system, the screenshots no longer exhibit the diagnosed inconsistencies, all
tests pass, and the user approves the live interface. Documentation artifacts
remain unchanged until a separate user instruction.
