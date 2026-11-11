# Garage explorer implementation and verification

The existing Thymeleaf fragment fetches `SlotMapService.Snapshot` through the existing customer/staff/admin slot-map endpoints. All floors, zones, space identities, counters, operational states and selectability come from `Snapshot.bays`. No backend, database or reservation business rules were changed by this redesign.

The browser sorts floor/zone/space number, sizes rows from the container and CSS bay width, and builds fixed-size rows around shared drive aisles. Small zones share rows. A partial pair balances its two rows; an individual row still has a drive aisle. The floor is an illustrative arrangement, not a surveyed facility plan.

Inspection is distinct from selection: a click/Enter or two-second pointer hover updates details; the action button writes the existing `parkingSpaceSelect` (`parkingSpaceId`) and fires its normal change event for reservation pricing. The existing list alternative remains supported. Polling retains separate inspected/selected state, disables selection while stale, and clears a selection only if the backend withdraws selectability. Only the literal boolean `occupied === true` renders a vehicle.

## Files

- `src/main/resources/templates/ui/parking-map.html`: shared controls, live statistics, legend and compact detail/preview panel.
- `src/main/resources/static/js/parking-map.js`: dynamic geometry, inspection/selection, polling and keyboard navigation.
- `src/main/resources/static/css/redesign.css`: consolidated garage styles and responsive layout; unrelated page styles retained.
- `src/main/resources/templates/customer/reservation-create.html`: wider page container only; form names and pricing logic preserved.
- `src/main/resources/static/images/parking/asphalt.svg`: reusable subtle floor texture.
- `src/main/resources/static/images/parking/vehicles/{sedan,suv,compact,hatchback}-top.svg`: four detailed overhead vehicle silhouettes and stable body colours.
- `src/test/js/parking-map.test.cjs`: DOM regression tests. Generated fixtures exist only in tests and never in the application.

## Verification (2026-10-06)

- Spring compilation succeeded; the app started against its configured local SQL Server.
- All 9 `SlotMapServiceTest` / `SlotMapControllerTest` tests passed.
- All 16 frontend tests passed: 1/4/12/20/25/40/65 spaces, floors/zones/feature filters, single EV zone, accessibility, occupancy versus empty reservations, maintenance, hover timing, inspection/selection separation, refresh retention, removed bays, pending/failed requests, recovery, form field serialization and responsive row rebuilding.
- Browser inspected the real customer reservation page at desktop (1440px), tablet (1024px) and mobile (390px). Desktop map/detail ratio was approximately 78/22, with no horizontal garage overflow at desktop or tablet. Mobile retains 84×168px bays and stacks details below; the internal floor can scroll slightly on narrow screens. No page-wide horizontal overflow was observed.
- Actual database facility used for visual verification returned 4 spaces on 1 floor with 3 zones. Larger counts and multiple floors were tested in the DOM suite, without adding fabricated spaces to the live database.
- In the live browser, inspecting EV-01 after selecting A-02 kept A-02 selected after refresh, with its LKR 250/hour reservation rate intact and EV-01's separate LKR 800 two-hour estimate in the details.
- No browser JavaScript errors were recorded. All 5 SVG files parsed as XML. `git diff --check` passed.
- Screenshot: `target/garage-desktop.jpg` (generated, ignored).

The broader `ReservationServiceMockitoTest` run passed 5 of 6 tests. Its existing `testBookAndPayReservationWithOnlinePayment` expects a separate `Payment Received` / `PAYMENT_SUCCESSFUL` notification, while the unchanged service sends `Booking Confirmed & Paid` / `RESERVATION_CONFIRMED`. This unrelated expectation was left unchanged. No real booking or payment was submitted during visual verification.

## Repeat the tests (PowerShell)

```powershell
mvn '-Dtest=SlotMapServiceTest,SlotMapControllerTest' test
# Test-only library under Maven's ignored output directory; no frontend framework or project dependency added.
npm install --prefix target/garage-tests --no-save --package-lock=false jsdom@26
$env:NODE_PATH = Join-Path (Get-Location) 'target/garage-tests/node_modules'
node --test src/test/js/parking-map.test.cjs
```
