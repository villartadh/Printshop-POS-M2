# Native preview durable stock recovery

Scope: preview-only local material movements, not a sale, checkout, live inventory, sync, tax or receipt. No real shop data. Requires schema v5 (additive from v1–v4). Existing catalog, ledger, recipes and pricing histories are not rewritten. Legacy direct movement replay remains compatible; new screen movements use staged identities.

## Protocol

1. Validate active material, catalog revision, exact stock revision, signed quantity in thousandths, printable reason and balance bounds. Commit a UUID plus immutable payload in `stock_requests`, initially PENDING. Partial unique index allows only one pending request per material.
2. Stage does not consume/receive stock. Pending material cannot be edited, archived, or changed by another direct movement. The native screen reloads pending records from SQLite (not Activity state). It displays identity, signed quantity and reason; new actions stay disabled.
3. Retry the same identity. In one SQLite transaction append the exact existing stock ledger format and mark APPLIED, or both roll back. Repeat of APPLIED returns the original historical result, not a second mutation.
4. If the operator explicitly cancels an unrecorded request, verify there is no ledger row and mark CANCELLED with a resolution timestamp. Never delete audit records. Cancelled identity cannot be used again. Applied cannot be cancelled (correction is a distinct reasoned ledger movement).
5. An ambiguous failure is not a reason to discard the identity or generate a replacement. Reopen stock screen, inspect the pending payload and recorded history, retry or explicitly cancel unrecorded request. If data cannot be read, actions remain disabled. The update trigger enforces immutable payload, one terminal transition and matching ledger for APPLIED. No auto-retry or auto-cancel.

## Verification and continuation

- `python3 android-native/tests/stock_request_schema_test.py`: 76 host SQLite checks across old v1–v4 fixtures, constraints, immutability, cancellation, atomic rollback and preserved rows. Host SQL verification is not Android acceptance.
- 126 existing standalone Java checks passed locally; Java parser checked modified CatalogStore, StockActivity and NativeQuoteSmoke (three files, zero parse errors; no Android type/build claim).
- CI adds mandatory SQLite schema tests and installed `NATIVE_STOCK_RECOVERY_PASS` alongside every existing native marker and successful instrumentation exit. Actual instrumentation covers legacy v4 fixture, migration preservation, durable pending reopen, precise injected trigger failure between ledger insert and state update, successful retry/replay, lock, cancellation, original identity, database reopen and screen finish/reopen. It does not claim an actual process-kill or physical device test.
- No SDK locally; require JDK17, Android SDK35, Gradle8.9, API34 emulator, full Android build/lint/installation tests and Windows/Linux software acceptance against current develop before merger.
- If hosted Actions report no steps/logs, retain draft and retry only when diagnostics become available. Resolve PR #10 then #11 first; retarget this major integrity milestone to develop and verify exact current combined gates before merge. Do not create further micro/staging branches.
