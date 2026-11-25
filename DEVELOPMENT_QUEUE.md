# M2 autonomous development queue

Target: v0.9.6 consolidated software pilot, not a production release. Preserve main, baseline/v0.9.5, and the original Printshop-POS repo. Use develop only; inspect current head and both CI runs before advancing. Make cohesive atomic changes, with tests and evidence; never bypass failing gates.

## Verified at 36e9b00
108 regression tests, 31 API scenarios, 12 browser workflows; CI 45723560869 and 45723553619 passed. Customer balance collection request identity, durable operator-local payment reservation and unchanged-reference retry submitted; extended API and new browser recovery scenario. Unconfirmed collections require reconciliation before another collection in the same browser/operator, no discard path. Next: financial precision, role and integrity audits.

## Verified at 1a3c6a3
106 regression tests, 30 API scenarios, 12 browser workflows; CI 45721398337 and 45721391645 passed. Real pricing conflict, original-record retry, incomplete acknowledgment, page reload and duplicate recovery passed. Transaction identity fingerprinting submitted; legacy unknown fingerprints require manual reconciliation, and deleted held-order identities are retained. Next financial integrity: balance collection idempotency and rounding audit.

## Acceptance at 6caf8e4
CI 45720069563 passed; CI 45720078529 failed Windows Chromium during quotation insufficient-payment warning (timeout). Root cause unconfirmed. Regression and API passed. Persistent checkout dialog error, specific tender locator and extended original-record recovery browser test submitted; test a real price conflict, unchanged original retry, malformed acknowledgment after server commit, page reload and duplicate recovery with no repeated side effects. Do not promote until both CI gates pass.

## Verified at 1fa9ad9
104 regression tests, 30 API scenarios, 12 browser workflows; CI 45706516650 and 45706507195 passed. Atomic local acknowledgment and owner-only unchanged-record retry UI submitted for verification; dedicated browser interruption/recovery test remains next. No discard or payload editing is permitted.

## Verified at 4d8ffa2
104 regression tests, 30 API scenarios, 12 browser workflows; CI 45687914738 and 45687904604 passed. IndexedDB connection lifecycle cleanup submitted for CI. Explicit operator reconciliation and interrupted synchronization acceptance remain next.

## Verified at e279d10
101 regression tests, 30 API scenarios, 12 browser workflows; CI 45648149738 and 45648134036 passed. Persistent alerts verified. Offline retry classification submitted for CI (three new regression tests); explicit operator reconciliation and IndexedDB lifecycle remain next.

## Verified at f735575
101 regression tests, 29 API scenarios, 12 browser workflows, successful CI runs 45626305527 and 45626296927. Dedicated inventory history/archive browser acceptance passed. Server alert acknowledgment implementation submitted for CI; target 30 API scenarios. Offline acknowledgment deliberately disabled until connected. Database triggers renew alert identity on severity/threshold transitions. Thresholds, alerts, reorder drafts, purchase costs, paginated material history and archive APIs implemented. Native physical acceptance deferred.

## Remaining ordered work
1. Verify dedicated material history and archive browser scenario added with this queue. Fix real failures.
2. Server-persistent per-user inventory alert acknowledgment, with cross-device and offline semantics; preserve severity changes and renewed alerts.
3. Offline reconciliation: inspect durable queue, distinguish transient vs permanent errors, safe retry with original mutation identity, explicit conflict UI and evidence; ensure no silently discarded paid orders, no duplicate effects; test interruption and recovery.
4. Audit financial, inventory and security integrity, role constraints, concurrency and data preservation; repair material defects and add acceptance.
5. Consolidate LDC, operator instructions, troubleshooting, feature matrix, changelog and readiness gates; verify archive and restore behavior, real cross-version upgrade from v0.9.5 with preserved data and historical receipts.
6. After all required software gates pass, prepare versioned v0.9.6 source, Windows x64 candidate, manifests/checksums, evidence and handoff. Do not merge main or promote to production; report actual artifact links and limitations. Stop development automation once software pilot is ready or when a genuinely blocking user action is required.

## Deferred gates
Physical Windows installation/display scaling, physical receipt printer, tablets, BIR compliance. Never claim these passed on the basis of CI. Do not access or modify real business data. No credentials, paid services, external deployment or destructive operations without explicit approval.

## Working method
Use GitHub connector and actual source/CI evidence. Update this queue after meaningful progress. Avoid speculative claims, mock dashboards or repetitive status reports. If CI is running, do not push unrelated changes that cancel it. If blocked, explain accurately. Do not invent artifact paths.
