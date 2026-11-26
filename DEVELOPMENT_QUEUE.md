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

## Confirmed acceptance blocker at 85c7e27
Both CI runs 45736550714 and 45736544035 failed at DPR 1.25 with HTTP 429, confirming the real global credential throttle (10 attempts/60 seconds) triggered by repeated synthetic logins. DPR 1.0 reached and completed its assertions. The browser harness now uses a shared password authentication helper, validates the bounded Retry-After and error message, waits for the real window to expire, and retries once only on 429; invalid credentials fail immediately. No production security settings changed. Retest both platforms; mobile scenario still not executed to completion. Continue remaining milestones after the gate passes.

## Verified at fdf3179
CI 45737463509 and 45737457229 passed: 115 regression, 33 API, 16 browser and four actual source-backend upgrade scenarios, virtual PDF and simulated DPR/touch/mobile offline evidence. Credential throttle remains unchanged. Submitted hosted Windows bundled upgrade and recovery gate using pinned v0.9.5 rebuilt runtime package, side-by-side v0.9.7 install, live backup, active restore refusal, abrupt termination, duplicate drawer recovery, invalid backup rejection, current/legacy restore and uninstall preserving data. Await Windows CI. Next: BIR dated official requirements gap analysis and resilience/security; actual physical devices remain blocked without authorized access.

## Verified at 40cd473
CI 45738233108 and 45738227476 passed including hosted Windows bundled upgrade and restore. Initial dated BIR-GAP-ANALYSIS.md committed with official BIR sources, current applicability distinctions, gap register and ordered controls. Universal non-tax notice added to previews, exports and copied summaries; existing virtual receipt tests expanded to check all sale document types and quotation. Await CI. Next: annex-level review and non-fiscal audit/reconciliation hardening, security and resilience; no official registration, real data, fiscal issuance or legal compliance claims. Actual physical hardware remains separate.

## Verified at c815371
CI 45739252121 and 45739245965 passed regulatory marking assertions. Detailed RMO 24-2023 IV(5) crosswalk added, with full RR 7-2024 and RR 6-2026 withholding update references. Submitted non-fiscal read-only ledger review on verified current backups with source SHA-256 and redacted finding IDs, three targeted regression tests. Await CI; next validate actual backup integration, improve privileged export minimization and resilience. Annex templates/official classification remain unverified; no fiscal issuance or claims of approval.

## Verified at f1292e8
CI 45740525294 and 45740517209 passed 118 regression, 33 API, 16 browser, four backend cross-version and five packaged Windows lifecycle scenarios. Submitted actual isolated snapshot ledger review (valid backup plus deliberately altered disposable copy, SHA-256 unchanged) and JSON export schema 1.2.0 minimization: omit replay/request/result identities and alert acknowledgment metadata, retain business and audit records, no-store, consistent settings snapshot, authorization assertions. Full SQLite snapshot retains all 24 tables and credentials; never distribute it as review JSON. Next: verify CI; expand resilience and data integrity with expense and pending monetary reconciliation, then physical acceptance tools. No fiscal issuance or official approval.

## Verified at 9df2567
CI 45741698650 and 45741690660 passed 118 regression, 34 API, 16 browser, four backend upgrade and five hosted Windows scenarios. Submitted centavo-strict expense creation, atomic creation audit, additive void metadata migration (preserves prior rows), reasoned admin/manager void with exact-repeat acknowledgment, active-report exclusion and form in-flight guard. Existing historical delete losses cannot be recovered; expense create lost-response idempotency remains unfinished. Await both CI runs. Next: durable monetary recovery and concurrency stress, then local acceptance harnesses. No real data or fiscal approval.

## Verified at a44311d
CI 45743589792 and 45743583154 passed expense centavo and audited void. Submitted operator-scoped durable expense reservation, atomic server mutation reference/digest binding, exact replay including post-void, strict acknowledgment and UI recovery after reload; internal request fields excluded from JSON export. API exact/rejected/void replay and real corrupted acknowledgment browser scenario submitted. Additive nullable expense columns preserve legacy records. Pending records have no destructive discard; definitive rejection still needs reconciliation. Await CI. Next: concurrency/fault injection and safe reconciliation for pending monetary actions, physical acceptance harness.

## Verified at decbdff
CI 45744443359 and 45744436626 passed 118 regression, 34 API, 17 browser, four backend upgrade and five hosted Windows lifecycle scenarios, including expense replay after browser reload. Submitted a bounded single-server HTTP concurrency scenario: 16 identical requests each for sales, expenses, drawer adjustments, stock purchases and collections; distinct competing stock/collection requests; direct durable row and audit counts; original replay after shift close. No production load or real data used. Await CI (35 API scenarios). Next: safe cancellation/reconciliation of definitively rejected monetary requests and one-click acceptance tools. Do not claim throughput or multi-server testing from this synthetic check.

## Concurrency result at df4bf39
CI 45745211026 passed both OS jobs; CI 45745216611 passed Windows and all Ubuntu API checks including concurrency, but Ubuntu browser failed in the earlier expense recovery scenario: forwarded undefined because the pending UI appears before route.fetch resolves. Synchronize the test on actual corrupted HTTP acknowledgment, explicit confirmation error and dialog closure before reload. Retain all original correctness assertions. Await both CI runs; no concurrency production throughput claims. Next financial conflict reconciliation.

## Verified at 73fd7e3
CI 45746004831 and 45746000146 passed Ubuntu and Windows, including 118 regression, 35 API, 17 browser, four source-backend upgrade/restore and five hosted Windows lifecycle scenarios. Expense recovery synchronization fixed without weakening assertions. Submitted audited operator-attested cancellation for unapplied collection, drawer and expense requests: serialize cancellation and application, refuse committed identities, persist fence and audit, clear only original local reservation after verified response. Schema 2 adds cancellation ledger, complete backup validation and version 1 migration projection. New API and drawer browser gates submitted; await both CI runs before claiming acceptance. No real cash activity or physical acceptance performed. Remaining: native physical acceptance entry points and final expanded candidate verification.

## Mixed-schema restore failure at 80c1ef0
CI 45747776409 passed regression, type check, build and API on both operating systems, then failed the version 1 projection after all four actual v0.9.5 upgrade/restore scenarios passed. Root cause: verify() mutated module-level schema requirements during version 2 validation, contaminating subsequent version 1 checks in the same process. Derive version-specific tables and columns locally; preserve strict schema 2, foreign-key and integrity checks. Existing mixed-version upgrade scenario reproduces the defect. Await full CI before further features or promotion.

## Verified at e0987b3
PR CI 45750255433 passed both Ubuntu and Windows jobs, including regression, API, actual baseline upgrade/restore, browser, Linux HTTPS and Windows package gates. Mixed schema verification repair confirmed by full workflow. Cancellation ledger is included in this tested revision. Push-triggered counterpart has not been independently located; do not infer a second passing run. Next: validate native acceptance branch via PR against develop (shared package/port safeguards and seven tests); preserve physical and BIR gates separately. Do not promote production.

## Checkpoint 2026-11-26 13:02 (Asia/Manila)
- Version/branch/tested SHA: 0.9.7, develop, 448d0eb962d60c0de279f52d733c481d358683a1 (merged PR #2). Starting SHA for this checkpoint is the same; this documentation commit is not the artifact SHA.
- M11 ENGINEERING PASS: financial cancellation and mixed-schema restoration verified in 45750255433, 45752139154 and integrated CI.
- M12 IN PROGRESS: native one-click launcher and seven preflight regression tests integrated; PR CI 45752179676 and integrated CI 45755191206 both OS jobs passed. Physical printer and actual-device guided acceptance tooling still needs a scoped evidence workflow; do not count physical testing as passed. Native server identity guard remains a hardening opportunity: packaged lifecycle generates a private LDC_INSTANCE after filtering environment, so do not assume a caller-supplied identifier survives.
- M13 integrated software gate ENGINEERING PASS for tested SHA only: 45755191206 both OS jobs passed regression, lint, build, API, pinned baseline upgrade/restore, browser, Linux HTTPS and applicable packaging. Final milestone remains pending M12 scope and subsequent final integrated verification. Exact current test counts not retrieved from logs.
- Verified artifacts in run 45755191206: source 15087910744 (artifact SHA-256 706301c97a537de7878598fad1ef66bd11ed5f395a4f86e6bec50f84aec3b57a), Windows x64 15088220146 (artifact SHA-256 03eff60bf48efb32e007aa17a21adf7030e79a43ed0c5475e6fca2cf0a03d3fb), expire 2026-12-26; Linux evidence 15087930730 and Windows evidence 15087293962 expire 2026-12-10. These are outer GitHub artifact digests, not inner release ZIP hashes. Source packaging requires handoff documentation.
- Next exact action: implement a synthetic-only guided printer/actual-device acceptance evidence workflow with explicit operator PASS/FAIL/BLOCKED and no credentials or business data. Inspect OPERATOR-GUIDE.md and existing Devices UI, add tests, retain native desktop isolation requirements. Then run integrated CI and reassess M12/M13. Continue broader original feature parity and standalone native Android implementation after expanded gate. No production promotion, physical acceptance or BIR approval.

## Checkpoint 2026-11-26 — expanded engineering acceptance complete
- Tested version/branch/SHA: 0.9.7, develop, ebf026a084aa4893043c91b784c8518d4fd99fe7. CI 45759866888: both Ubuntu and Windows jobs completed successfully; all applicable regression, lint, build, API, pinned baseline upgrade/restore, browser, Linux HTTPS, source and Windows packaging gates passed. Exact test counts not retrieved from logs.
- M12 ENGINEERING PASS: native launcher plus guided physical printer and real-device PWA operator-attested evidence recorder, seven new synthetic tests, LDC menu and operator guide included in tested revision. M13 ENGINEERING PASS: integrated software acceptance. Expanded engineering milestones 13/13; physical/native-device execution and regulatory approval are NOT passed.
- Artifacts verified at exact tested SHA: source 15090031101 (SHA-256 52de237f14988a7288640d7ce2504c91780a4240a0517da3d6b31c8549d71c7c); Windows x64 15089556980 (SHA-256 35ea87b89b32a513945d4fc438d6bc7cbca74a71fccb16be9f15e807f9ad21f9); both expire 2026-12-26. Windows evidence 15089611818, Linux evidence 15089387385, expire 2026-12-10. Digests refer to outer GitHub artifacts. Run: https://github.com/villartadh/Printshop-POS-M2/actions/runs/45759866888.
- Next action: continue broader original requirements beyond expanded acceptance. Inventory existing Android/PWA/native architecture and original feature parity before selecting a testable standalone Android deliverable; keep candidate and immutable baseline intact. Do not claim a PWA wrapper as standalone offline POS or physical acceptance. Physical setup, real printer, real phone/tablet and BIR approval remain open. No production promotion.
