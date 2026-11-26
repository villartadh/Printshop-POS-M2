# Printshop POS M2 — START HERE: development handoff

This is the permanent continuation entry point for any developer, Codex session, ChatGPT session, or authorized automation. The project must be resumable without access to earlier chats. Read this file and the linked repository records before modifying anything.

## Canonical source and authority

- Repository: https://github.com/villartadh/Printshop-POS-M2
- Active integration branch: `develop`; use isolated feature branches for unverified work.
- Protected historical reference: `main` and `baseline/v0.9.5`, commit `255518ed388bcee3d63ee211e17c199ba984547e`. Do not rewrite history.
- Verified v0.9.6 pilot: `pilot/v0.9.6`, commit `b681596d50ee2fb31d40b5ef087bf58169350429`.
- Original feature reference: https://github.com/villartadh/Printshop-POS (read-only comparison).
- Read [AGENTS.md](AGENTS.md), [DEVELOPMENT_QUEUE.md](DEVELOPMENT_QUEUE.md), [HANDOFF_SNAPSHOT.md](HANDOFF_SNAPSHOT.md), [FEATURE-MATRIX.md](FEATURE-MATRIX.md), [CHANGELOG.md](CHANGELOG.md), [OPERATOR-GUIDE.md](OPERATOR-GUIDE.md), [BIR-GAP-ANALYSIS.md](BIR-GAP-ANALYSIS.md), and [CI workflow](.github/workflows/software-acceptance.yml).
- The live Git history and CI evidence override stale version/status statements in historical documentation. This file defines the continuation procedure; the queue is the mutable status ledger.

## Product and scope

A reliable, local-first, touch-friendly printing-shop POS in Philippine pesos: cashier, configurable services and material costing, inventory, expenses, shifts, customers and receivables, quotations, receipts, production, reporting, settings, backup/restore, synchronization, and diagnostics. Preserve implemented features and compare original feature parity. Owner direction includes standalone Windows packaging, native Android screens and standalone APK (not a WebView/PWA relabeled as native), web/PWA, accessible layouts, kiosk controls where supported, and safe data upgrades including historical backup compatibility. These broader product requirements are distinct from the 13 expanded software acceptance milestones. Audit missing requirements before claiming full completion.

## Checkpoint recorded 2026-11-26 (Asia/Manila)

Version 0.9.7 expanded engineering candidate; not a production release.
- Latest inspected develop commit before this documentation: `a137b3b71580e5a32a05f3d678383ea8bf6a5401`.
- Mixed-schema repair: `e0987b34985618b11a292332ca0e2e8665e973d8`; CI [45750255433](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45750255433) passed both OS jobs.
- Follow-up CI [45752139154](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45752139154) passed both OS jobs.
- Native tooling [PR #2](https://github.com/villartadh/Printshop-POS-M2/pull/2), branch `prepare/native-acceptance-v097`, head `74258658b5fadf5ad22a0bc10896ea10d0b2d4b4`. CI [45752179676](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45752179676): Ubuntu passed, Windows packaging step in progress at inspection. Recheck live; do not infer eventual success.
- Historical verified counts at 73fd7e3: 118 regression, 35 API, 17 browser, four source upgrade/restore and five hosted Windows lifecycle scenarios. Seven additional preflight tests submitted on PR #2; derive current counts from actual evidence.

## Expanded acceptance milestone ledger

1. Virtual receipt printing — previously passed.
2. Display scaling simulation — previously passed (not actual OS DPI).
3. Mobile/tablet emulation — previously passed (not real devices).
4. Hosted Windows lifecycle — previously passed.
5. BIR technical gap analysis — completed analysis only, not approval.
6. Financial ledger diagnostics — previously passed.
7. Backup integrity/export privacy — previously passed.
8. Expense integrity safeguards — previously passed.
9. Durable expense recovery — previously passed.
10. Concurrency/stress correctness — previously passed, not throughput certification.
11. Financial conflict reconciliation — implemented; repair and follow-up CI both OS jobs passed; confirm exact revision and evidence in queue before marking accepted.
12. One-click physical acceptance tools — PR #2 awaiting full acceptance; audit scope, including printer and device tools, before closing.
13. Final expanded software acceptance — pending integrated revision, evidence and packages.

## Exact resumption procedure

1. Obtain authorized repository access. Clone or open the existing checkout, inspect `git status`, preserve uncommitted work, fetch without destructive resets, and inspect `git log -5 --oneline`, current branch, open PRs and Actions. Never assume this checkpoint is current.
2. Read the queue from beginning to end, prioritizing its latest entries. Identify the exact starting SHA, version, active task, dependencies, failed or running CI, and any existing concurrent automation. Do not duplicate work or cancel an unrelated running gate.
3. If CI is running, inspect its actual state; avoid pushes that cancel it. If failed, identify the failing step and reproduce with synthetic data. If passing, verify the tested SHA and both OS jobs. Keep a feature branch isolated until its required gates pass; merge only verified, nonconflicting changes, then verify integrated develop again.
4. Select the highest-priority eligible task: financial/data integrity and security first, then remaining acceptance, feature parity and cross-platform scope. Fix actual defects; add tests. Do not stop at a plan. Defer external hardware/credential/legal blockers explicitly and continue independent work.
5. Use Node 22.12+ within major 22 and locked dependencies. From repository root, execute:
   ```sh
   npm ci
   npm test
   npm run lint
   npm run build
   npm run acceptance
   npx playwright install chromium
   npm run acceptance:browser
   npm run acceptance:https
   ```
   HTTPS requires OpenSSL; Linux browser dependencies may require `npx playwright install --with-deps chromium`. Use `node validate.mjs` for the standard local gate. Follow the checked-in CI workflow for actual pinned baseline upgrade/restore and Windows package tests. Never use a live shop database. Missing tools are BLOCKED, not PASS.
6. Review diffs, security, migration compatibility and test evidence. Commit cohesive changes, run CI, verify both OS results, preserve artifact IDs/checksums and distinguish test fixtures from physical acceptance. Do not merge/promote merely because a build succeeds.
7. Before ending ANY session, append a checkpoint to DEVELOPMENT_QUEUE.md using the template below. Commit or preserve unfinished work on an identifiable branch. If unable to write the repo, provide the exact patch/checkpoint and explain why. A documentation-only follow-up commit does not retroactively change which source SHA was tested.
8. Resume next time from that checkpoint, not this historical snapshot. Do not ask the owner to repeat requirements or type continue for routine tasks within an authorized execution session. Scheduled continuation requires an actual configured automation; do not promise background work without it.

## Mandatory checkpoint template

```markdown
## Checkpoint YYYY-MM-DD HH:mm (Asia/Manila)
- Version / branch / starting SHA / ending SHA:
- Milestone and status: NOT STARTED | IN PROGRESS | IMPLEMENTED | ENGINEERING PASS | MANUAL UAT PENDING | BLOCKED | ACCEPTED | RELEASED
- Changes and commits/PRs:
- Commands actually executed; passed/failed/skipped counts:
- CI run IDs, tested SHA, OS results:
- Artifact names, IDs, SHA-256, retention/expiry if known:
- Unfinished work and external blockers:
- Safety/data migration considerations:
- Exact next action (file, command, expected result):
- Acceptance and release decision:
```

## Immediate next action — 2026-11-26

Always read the latest DEVELOPMENT_QUEUE.md and compare actual branches, PRs and CI. The v0.9.7 expanded software candidate and baseline are protected. Native preview is separate from production.

1. Service-material editor #9 passed full gates and integrated at develop `cbb5f0f99b0f259d2a350cd7deb8f00cd9baf44b`. Inspect fresh integrated Android 45777503538 and software 45777503573.
2. Pricing math #10 passed Android 45776608192, reconciled a concurrent README edit without changing source, head `885514bba0e9d0090ebd1c2360de532e7253f8ae`. Check Windows/Linux 45777644474 and Android 45777644497; merge only when all required gates pass.
3. Native pricing settings branch `feature/native-android-pricing-settings`: additive schema v4, append-only preference/tier snapshots, optimistic revisions, native editor; 21 local profile and 27 local SQLite fixture checks passed. Android CI must verify v3→v4 migration and actual native settings flow. Require NATIVE_PRICING_STORE_PASS and NATIVE_PRICING_SETTINGS_PASS along with every prior marker and exact terminal success code. Retarget to develop only after parent accepted, re-run full gates. See android-native/PRICING_SETTINGS.md.
4. Build advanced quote input fields and exact fractional material estimation next. Advanced service selection and stale legacy quote output currently intentionally blocked; replace this only when the actual method, page/copy/dimension inputs, bulk rate and explicit material basis are supported. Then durable pending stock recovery, checkout, receipt, authentication and remaining native parity.
5. Use JDK 17, Gradle 8.9, Android SDK 35/build-tools 34.0.0, API 34 disposable emulator. Run `gradle -p android-native --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` and the exact instrumentation command in `.github/workflows/android-native.yml`. Use synthetic data only; no real shop database. Physical devices, printing and official BIR acceptance remain separate.
 

## Protected contracts and release limits

- Preserve exact financial arithmetic, atomic transactions, idempotency, inventory and balance protections, immutable audit trails, cancellation fences and safe backup migrations. Never discard unresolved paid transactions.
- No credentials, signing secrets, live databases or customer information in commits, logs or artifacts. No destructive production operations, paid services or external deployments without authorization.
- Keep physical Windows scaling, actual printer output, actual tablet/phone/PWA tests, native Android acceptance and formal BIR review separate from hosted CI. Use disposable Windows desktop for native harness; never run scaling tests on live shop PC. TCP preflight does not prove server identity.
- Reproducible candidates require exact tested SHA, package manifest/checksums, installation and recovery instructions, and documented acceptance limitations. Do not promote production until required gates pass and applicable approvals exist.

## Copy-ready continuation prompt

Continue Printshop POS M2 from the current GitHub repository. Read HANDOFF-README.md, AGENTS.md and the latest DEVELOPMENT_QUEUE.md checkpoint. Reconcile live branches, PRs and CI before changing code. Implement the next eligible task, test with synthetic data, preserve protected baselines, commit verified work and append the exact next action to the queue. Continue eligible milestones without routine approval; report genuine blockers and never claim unexecuted tests or production readiness. Preserve this handoff in all future source packages.
