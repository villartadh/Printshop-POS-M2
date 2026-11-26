# Printshop POS 0.9.7 expanded acceptance candidate

Use the separately staged Windows x64 distribution for installation and the [operator guide](OPERATOR-GUIDE.md) for setup, backups and device checks. The software acceptance checks use synthetic data. Windows, actual devices, a physical printer and BIR review require independent acceptance.

On a source checkout, use Node 22.12+ in the 22 series and run `node validate.mjs` for the locked installation, regression suite, type check, production build and API acceptance. Use `npm run acceptance:https` with OpenSSL, and `npm run acceptance:browser` with Playwright Chromium or an explicit `CHROMIUM_PATH`.

## Native Windows acceptance

Use a disposable English Windows 11 x64 console session with a single monitor that offers 100%, 125% and 150% scaling. Install the source workspace's locked development dependencies and Playwright Chromium on that test machine. This setup is for release acceptance. Operator installations use the bundled runtime.

Set `POS_WINDOWS_ACCEPTANCE=disposable-desktop`, then run `node scripts/windows/native-acceptance.mjs OLD_RELEASE NEW_RELEASE NEW_EVIDENCE_DIR`. Supply the extracted 0.9.5 baseline and 0.9.6 candidate Windows packages. The harness checks the bundled runtime, installs into temporary LocalAppData, uses synthetic transactions and captures PNGs and native DPI measurements. It restores the original scaling in its cleanup. The runner must permit execution of the checked-in PowerShell script under its normal execution policy.

## Historical notes

The following sections record the status of earlier releases.
### Printshop POS 0.4.0 — offline synchronization engineering candidate

## Windows launch
Extract to a writable local folder. Install Node.js 22 LTS (22.12+ within major 22) if absent. Double-click START-PRINTSHOP-POS.cmd. Internet is required for first dependency installation. The launcher stops on failed installation, tests, type checking or build. It then launches the local server and browser. Create your own administrator on first launch. No default password or sample sales are installed. Configure real services, prices, taxes, inventory and payment methods before taking sales. Keep the server window open; Ctrl+C stops it.

Manual: npm install; npm test; npm run lint; npm run build; npm start. Open http://127.0.0.1:3000. The responsive browser/PWA is not a native executable or APK. LAN access is disabled by default; HTTPS and a security review are needed before exposing it to other devices.

## Data safety
Original archive is unchanged. Its data directory contained SQLite WAL/SHM sidecars without the primary database. This release initializes a fresh SQLite database; it does not claim recovery of historic records. Preserve the original archive. For a full local backup, stop the server and copy the entire data directory to protected storage, including the session secret. Do not overwrite a live database. JSON export is not a complete restore system.

## Gate status
Dependency-free Node tests: 61 passed. npm registry DNS failed in the build environment; dependency installation, full TypeScript check, production build, API integration, browser workflows, physical receipt printing and native/mobile installation are NOT verified. No dependency lockfile could be generated. This is not a production acceptance or a BIR-certified invoicing system.

## Known remaining risks
Reversal and collection logic has SQLite fixture tests; full API integration, dated cash-flow reporting and historical reconciliation remain pending. Server-authoritative catalog pricing is implemented; full API integration and role/security review remain required. Offline multi-user isolation, synchronization conflicts, payment verification, backup restoration, hardware and device integration require validation. Electronic payment entries are bookkeeping, not payment processing. No actual shop data or prices were fabricated.

## v0.1.1 changes
- Enforces line and order arithmetic, customer existence, receipt uniqueness, valid linked cashier shift, and configured inventory existence on the server. Client-supplied catalog pricing is still not authoritative.
- Restricts shift closing/movements to owner or manager/admin, rejects invalid opening cash and overdrawing the expected drawer.
- `npm run db:backup` generates a consistent SQLite snapshot including committed WAL data. Existing database is backed up at each successful launcher preflight; backups are not encrypted or automatically pruned. Keep protected external copies and protect the data/session.secret separately.
- `node scripts/database-maintenance.mjs verify PATH` checks integrity, required tables and foreign keys. `node scripts/database-maintenance.mjs recover-copy PATH` creates a verified separate copy; it does not overwrite live data. Stop the server before any manual replacement; preserve the entire data directory, including sidecars, and never mix WAL/SHM files. Recovery cutover has not been integration tested.
- Settings JSON export is explicitly labeled partial. It is not a database restore artifact.
- Node 22 built-in SQLite is experimental; snapshot tests passed on 22.16.0. No dependency installation or runtime gate could be completed due to registry DNS failure.

## v0.1.2 changes and operating rules
- Full-sale refunds record the original cash/electronic split in an append-only reversal table; cash reduces the acting manager/admin’s open drawer, not the original closed shift. Electronic refunds must be performed externally before recording; no funds are transferred by POS. Unpaid sales are voided rather than refunded.
- Reversals atomically update customer balance, spend, loyalty and related print-job payment state; pending jobs cancel, started jobs retain production status. Material consumption is never automatically reversed; use a physical stock return/adjustment only when appropriate.
- Cash-only customer collections allocate oldest open receivables first, update sale/job/customer/shift records and store allocation rows. Requires an open shift; overpayments and mismatched legacy balances are rejected. Sales with later collections require manual reconciliation before reversal; there is no automated reversal path for those yet. Collection retry idempotency is not implemented; inspect balances before retry after an uncertain response.
- Account credit is not cash tender; use a partial/unpaid sale with a registered customer. Every sale requires the cashier's own open shift.
- Receipt preview exposes manager/admin reversal actions and marks reversed original sale records. Payment-method summary deducts cash change. Reports still use original sale dates, not the dates of collections or refunds; they are not a complete accounting ledger or cash-flow report.
- Tests use Node SQLite fixtures with the actual table schema and explicit transactions, not the uninstalled Express/better-sqlite3 stack. 20 tests passed; registry DNS failure remains.

## v0.1.3 — Catalog-authoritative pricing candidate

The server recomputes catalog item prices, quantities, discounts and configured taxes for new sales, held orders, quotations and offline transaction submissions. It rejects mismatched amounts instead of silently changing a payment total, and substitutes trusted catalog metadata and consumables. Custom items require an administrator or manager. Existing cashier discount functionality remains available; a manager approval policy for discretionary discounts is not yet implemented. Payment method IDs and types are checked against enabled methods; external settlement is not verified. Previously accepted mutations from the same cashier retain their stored values for retry. A changed catalog price can block a stale offline or held transaction; an operator-facing conflict resolution workflow remains outstanding. Quantity changes recalculate pricing rather than scaling an old total; pages/copies lines must be removed and reconfigured to change page/copy counts. Formula validation and rounding edge cases still need broader integration testing. No actual shop rates were invented or seeded. Full dependency install/build/browser tests remain blocked by npm registry DNS failure (EAI_AGAIN). This candidate is not production accepted.

## v0.2.0 — Saved-order and production workflow
- Recall synced held orders and quotations from POS without deleting them. A checkout can reference the source; the server records a unique conversion within the same sale transaction. A failed sale rolls back the conversion, and a converted source stays in the historical records. The same customer must be retained. Recalled carts use current catalog validation; source line changes are allowed.
- Production transitions require pending → production → quality check → ready → completed, with rework allowed. A linked sale must be paid in full before completion. Cancellation of linked jobs requires financial reversal; production already started may require manual resolution. Payment status and deposits cannot be edited from the job screen.
- Standalone manual priced jobs are unpaid quotations only and cannot enter production; use POS quotations and checkout for billable jobs. Legacy unlinked paid jobs require reconciliation. No automatic migration or fabricated payment is made.
- 44 tests passed, including nine workflow logic tests and three SQLite conversion fixture tests. Shared workflow module strict TypeScript check passed. Registry DNS lookup failed; full build, browser/API and physical acceptance remain blocked.
- Limitations: no separate digital customer approval signature, no direct manual-job-to-sale linking, no electronic balance collection, no complete offline conversion conflict resolution; the UI requires an online connection to start recalled checkout but a mid-request outage can still queue the sale. Do not take duplicate payment after uncertain checkout. Existing bootstrap pagination limits recall history.

## v0.3.0 — Printing and device diagnostics
- Separate print preview window and standalone HTML export share paper-specific layouts for 58 mm, 80 mm, A4, Letter and custom roll widths. Thermal length and paper selection remain printer-driver controlled. Use Print / Save as PDF in the operating system dialog; no direct PDF generator. Popup blocking is reported.
- Dashboard history marks documents as reprints/copies; repeated preview openings in one modal also mark copies. This is not a persistent print audit or confirmation of physical output. Held orders and quotations cannot select sales document types. Removed fabricated QR-like matrix; the legacy setting now shows a text reference. Sales slips and invoice drafts are explicitly not tax invoices.
- Device test prints use synthetic non-sale content even with an empty database; keyboard input test accepts a keyboard-wedge scanner. Peripheral chooser actions only discover/authorize devices; direct ESC/POS, drawer control and camera decoding are not implemented.
- 52 dependency-free tests passed; strict TypeScript check of the new print helper and service passed. Registry DNS still fails; full build and browser/physical printer validation remain blocked.
- Manual acceptance: test fresh database diagnostic slip; print long customer/item names on each paper format; verify no app chrome, clipped amounts or fake QR; compare exported HTML and preview; select Save as PDF; reopen a historical sale and verify copy label; cancel print and confirm no sale changes; test popup blocked and scanner input. Confirm actual printer margins and thermal roll length in driver. Do not use as BIR tax invoice until compliance is separately established.

## v0.4.0 offline synchronization
- Owner-filtered queues, maximum 50 records per batch, 15-second retry cadence, single in-flight sync per tab. Conflicts are retained and excluded from automatic retries; Settings shows owned conflict messages. Provisional receipts indicate unconfirmed server status.
- Stock adjustments share a stable client mutation ID for online and offline paths; SQLite stores the result atomically with the adjustment and rejects conflicting reuse. Legacy unowned stock entries are not automatically applied; preserve browser data and reconcile manually.
- Offline mode requires an already authenticated session; no offline sign-in after restart. Browser cache is not encrypted or fully isolated by account; shared-device privacy, cross-tab races, lost-response cases, shift closure, conflict resolution, and full IndexedDB/API integration require testing. Do not clear browser data with outstanding mutations.
- 61 dependency-free tests passed (9 new policy tests); shared offline policy strict TypeScript check passed. The stock idempotency route and real browser queue have not been integration tested. Registry DNS still unavailable.

## v0.5.0 — Local Development Companion
On Windows open `START-LDC.cmd` (or run `node scripts/ldc.mjs`). Node 22.12+ in the 22 series is required. Select 1 to install missing dependencies, test, typecheck, build, back up an existing database, and start a supervised local production server. Open http://127.0.0.1:3000 when ready. Keep the console open. Options 2–8 provide validation, status, stop/restart, a privacy-minimized JSON diagnostic report, backup, and runtime log location. Option 0 stops the session-owned server and exits. Do not run the legacy launcher concurrently. LDC only stops its own child process and checks a per-launch health identifier. Logs stay in `.ldc/`; raw runtime logs may contain sensitive information and are not included in the diagnostic report. The report is not a ZIP support archive. No bundled runtime, graphical dashboard, crash auto-restart or display scaling automation is provided. Windows process behavior and full application startup require validation on Windows with dependencies installed. Abrupt console termination may leave the child running; the next LDC will refuse to adopt an existing healthy server.

## v0.6.0 — Cross-platform layout and Windows app-window candidate
- Layout policy clamps desktop previews to the actual available viewport; mobile and tablet previews remain available on larger screens. Tablet POS uses a side-by-side catalog/cart; mobile navigation honors hidden modules and includes safe-area padding. Mobile checkout reserves space for the cart bar; focus mode cannot hide mobile navigation.
- Windows app-window option: run START-LDC.cmd, select 1 to pass the full gate and start the managed server, then select 9 to open Edge or Chrome in app-window mode. The launcher checks the managed server instance and uses the same loopback origin as normal browser access. This is not a native executable or installer. Closing the browser does not stop the LDC server; use option 4 or 0.
- Removed remote Google font requests and runtime font caching; system fonts are used unless installed locally. Development service worker disabled to avoid stale dev resources. Existing production PWA configuration retained.
- Android/iOS/tablet access from a separate device still requires a separately configured trusted HTTPS host; no LAN listener or firewall changes made. No APK or native desktop binary is included.
- 69 regression tests passed (5 new policy/launcher tests); JavaScript syntax checks passed. Registry DNS EAI_AGAIN prevents dependency installation, full TypeScript/build and real browser tests. No physical Windows/Android or scaling acceptance. Preserve v0.5.0 and do not promote this candidate for business use.

## v0.7.0 — Local security and database indexing candidate
- Local HTTP only: rejects non-loopback Host and foreign browser Origin; non-loopback bind disabled. Not a remote deployment solution.
- Shared fixed-window credential limit: 10 requests/minute, including successful attempts, reset on restart. New/changed passwords 8–128 characters; existing shorter passwords remain usable. PINs 4–6 digits.
- Last active administrator protected; changed credentials, roles or activation revoke sessions; non-admin session listing restricted to own sessions.
- API no-store, framing and MIME protection, reduced error logging; eight nonunique SQLite indexes for frequent reads.
- 75 regression tests pass, including six new pure policy tests; strict standalone policy TypeScript check passes. npm ping fails EAI_AGAIN; actual middleware, database migration, performance and browser behavior not integration tested. No claim of comprehensive access control, secure remote access or production readiness. Global rate limit can delay legitimate local logins; browser cache and full bootstrap authorization still require review.

## v0.8.0 — Isolated API acceptance gate

`npm run acceptance` starts a production server on a temporary loopback port in a temporary working directory, with a fresh SQLite database, synthetic administrator and no demo data. It checks HTML serving, authentication, origin restrictions, shift reconciliation, persistence after restart and logout. It removes the temporary database after testing. The LDC and Windows startup gate now require this check after build. JSON evidence is written to `.ldc/acceptance-*.json`; missing prerequisites are BLOCKED and return nonzero. A passing result does not establish browser interaction, checkout, printing, offline behavior or physical device acceptance. No real customer database is used. The isolated runtime gate has not been exercised here because npm dependencies are unavailable.

## v0.9.0 Windows installation candidate

Fresh installation: extract the entire ZIP, install Node.js 22 (22.12 or later in the 22 series), then run `INSTALL-WINDOWS.cmd`. It checks the source manifest and stages a new immutable-by-installer version under `%LOCALAPPDATA%\Printshop POS\releases\0.9.0`. Open `INSTALLED-LDC.cmd` there, select 1 to validate, install dependencies, build and start, then 9 to open the desktop window. Keep the LDC console open. First-run admin setup happens in the application. Internet is needed for first dependency installation. The source manifest detects corruption, not publisher authenticity; compare the ZIP SHA-256 with the independently delivered digest.

Installed versions share `%LOCALAPPDATA%\Printshop POS\data` (including session secret and backup/recovery subdirectories). Only use the installed LDC launcher for installed business operation; source launchers retain portable behavior. Existing portable databases are not automatically migrated. Stop all POS instances, preserve the entire previous data folder, and plan a verified migration before switching existing business records. Do not run different releases simultaneously. Old releases are preserved, but database schema compatibility for rollback is not guaranteed. A verified backup is required before starting a new release against existing data. No automatic activation or rollback is claimed.

The installer's filesystem operations were tested on Linux with Node 22. Windows batch execution, dependency provisioning, native executable distribution, and actual application acceptance remain unverified. This is not a self-contained installer or an accepted production release.


## v0.9.1 release readiness
Run `node scripts/readiness.mjs` for a fail-closed evidence report in `.ldc/release-readiness.json`. It does not install dependencies or promote a release. Independent Windows, browser, printer and compliance evidence remains required. START-PRINTSHOP-POS.cmd now routes to the supervised LDC rather than starting an unowned server. Use LDC option 1 then 9. This is not a production release.

## v0.9.2 — Automatic startup
After installing, open RUN-POS.cmd to validate, build, start and open the Windows browser application automatically. Keep its console open; Ctrl+C stops the supervised server. INSTALLED-LDC.cmd remains available for diagnostics. The source START-PRINTSHOP-POS.cmd uses the source data location unless POS_DATA_DIR is supplied. Initial npm provisioning still needs network access and a compatible Node.js 22 runtime. No production acceptance is claimed.

The GitHub Actions artifacts are the tested distribution, not the raw development checkout. The versioned source archive contains its regenerated SHA-256 release manifest. The Windows distribution includes Node.js and SQLite; installation does not require npm. Physical acceptance remains deferred. See [feature matrix](FEATURE-MATRIX.md).

The verified v0.9.6 software pilot is preserved at pilot/v0.9.6; v0.9.7 extends automated deferred acceptance. Virtual print evidence is not physical printer evidence.
