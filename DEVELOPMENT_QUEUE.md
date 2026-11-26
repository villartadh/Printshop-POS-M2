# M2 autonomous development queue

Target: v0.9.7 expanded automated acceptance following the verified v0.9.6 software pilot, not a production release. Preserve main, baseline/v0.9.5, and the original Printshop-POS repo. Use develop only; inspect current head and both CI runs before advancing. Make cohesive atomic changes, with tests and evidence; never bypass failing gates.

## Verified at 2291837
115 regression tests, 33 API scenarios, 14 browser workflows, four actual upgrade/restore scenarios; CI 45732402008 and 45732394799 passed. LDC consolidation accepted. Version 0.9.6, feature matrix, troubleshooting and versioned source/Windows packaging submitted. Source manifest is generated in CI from allowlisted tracked files, excluding old committed manifest. Next: inspect both release CI runs and actual artifact IDs, verify software readiness, update handoff without invalidating the tested artifact identity, and stop automation if software pilot ready. Do not merge/promote or claim physical/BIR acceptance.

## Verified at 1b34246
113 regression tests, 33 API scenarios, 14 browser workflows and four actual cross-version upgrade/restore scenarios; both CI 45731676204 and 45731669288 passed. LDC startup vs comprehensive gate, structured session evidence and allowlisted SHA-256 support ZIP submitted. Next: verify LDC, finalize operator documentation/feature matrix and prepare versioned source and Windows candidate packaging. Physical gates remain separate.

## Upgrade acceptance at 07201d2
Both Ubuntu jobs passed actual baseline upgrade and restore; both Windows jobs failed during server startup (CI 45731210729 and 45731205631). Suspected Node ESM absolute Windows drive-path loader resolution; convert loader to file URL and retain bounded startup stderr with sensitive lines removed. This is a harness repair, not a database migration change. Keep all original migration and restore assertions mandatory. Next: verify both OS gates, then LDC consolidation and release evidence.

## Verified at 2f6b772
113 regression tests, 33 API scenarios, 14 browser workflows; CI 45730582869 and 45730574833 passed. Version-aware backup gate accepted. Added CI gate with pinned actual v0.9.5 server and its locked dependencies, synthetic sales, collections, inventory, legacy/current restoration, unchanged historical columns, session secret and current receipt/idempotency evidence. Uses current static build only as server startup prerequisite; tests actual baseline backend, not baseline UI. No real data. Next: resolve upgrade gate then LDC and packaging.

## Verified at 686cd27
112 regression tests, 32 API scenarios, 14 browser workflows; CI 45730007855 and 45729999746 passed. Checkout and targeted authorization accepted. Schema-version marker and strict marked database completeness checks submitted; legacy version 0 remains eligible for migration, future versions rejected. Financial ledger/replay table snapshot comparison and expanded non-restorable JSON export tests added. Next: actual baseline upgrade and restore acceptance, LDC evidence and release documentation. Browser-local unresolved records and session.secret remain outside SQLite snapshot.

## Acceptance at 2a52b08
CI 45728874513 passed; CI 45728882856 failed Windows browser quotation tender entry (actual '20.0010', expected '10'). Regression/API passed. Checkout fields now initialize in layout phase before paint; browser waits for exact initial tender before entry, retaining strict value and insufficient-tender assertions. Cause suspected initialization race, not yet proven. Do not bypass gate. Backup audit: verified SQLite snapshot copies complete database, but JSON export omits financial recovery tables, and current database verifier only checks legacy core tables; strengthen verification with version-aware completeness and recovery evidence after checkout gate passes.

## Verified at c375ca7
112 regression tests, 32 API scenarios, 14 browser workflows; CI 45728179085 and 45728172352 passed. Drawer recovery accepted. Targeted authorization fixes submitted: cashier held-order deletion ownership, deletion audit, validated customer data and privileged discounts, limited staff email and audit log exposure. Shared order recall remains available across authorized terminals. Full role/data minimization and backup completeness still require review.

## Verified at 58602b0
112 regression tests, 32 API scenarios, 13 browser workflows; CI 45727403146 and 45727394780 passed. Centavo precision accepted. Durable cash drawer movement references, atomic audit and recovery UI submitted; new browser recovery test. Pending local adjustment blocks same-operator drawer changes and closure; other terminals remain independent. Next: security/authorization and backup completeness audit.

## Verified at 8eb6efe
108 regression tests, 31 API scenarios, 13 browser workflows; CI 45724461293 and 45724453844 passed. Durable customer payment recovery passed. Exact centavo validation and drawer precision submitted; no historical balances are rewritten. Next: audit monetary adjustment idempotency, role authorization and backup completeness.

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

## Expanded acceptance checkpoint
v0.9.6 b681596d50ee2fb31d40b5ef087bf58169350429 verified by CI 45733269332 and 45733263601; preserved at pilot/v0.9.6. Artifacts (30-day retention): Windows 15078390589, source 15078020902, Windows evidence 15078320811, Linux evidence 15077557213 in run 45733269332. User authorized continuing feasible deferred milestones. v0.9.7 candidate begins virtual receipt PDF geometry checks on actual rendered previews for all five supported formats. Next: verify CI, add mobile/touch and DPR acceptance, hosted Windows upgrade/restore and BIR gap analysis with official sources. Do not stop at software pilot; continue until feasible expanded acceptance is complete. Physical hardware and official approval cannot be inferred from simulation.

## Verified at 268e249
Both CI runs 45734634658 and 45734628690 passed the actual receipt virtual PDF checks across 58mm, 80mm, A4, Letter and custom formats. Submitted two additional browser scenarios: measured Chromium 100/125/150% device-scale simulation with checkout bounds, and Android touch emulation with offline cash sale, reconciliation, PWA manifest/service-worker and tablet geometry evidence. Physical OS scaling, actual installed PWA, physical printing and real devices remain unverified. Next: verify both gates then packaged Windows upgrade/recovery, BIR gap analysis and resilience.

## Expanded gate failure at 7c8ab7e
CI 45735694564 and 45735685964 failed Windows and Linux before new DPI measurement, waiting for Synthetic Print in a fresh browser context. Earlier gates passed. Fresh contexts now await actual password authentication response and explicitly select POS (desktop sidebar or mobile navigation), with scoped failure screenshots; offline test awaits offline indicator before checkout. Suspected navigation/initialization issue, not confirmed until CI passes. Preserve all existing DPR, geometry, touch and reconciliation assertions.
