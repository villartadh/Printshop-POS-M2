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

Use the latest `DEVELOPMENT_QUEUE.md` entry rather than earlier historical snapshots in this file. Verified v0.9.7 expanded software candidate is preserved; the native Android preview is now advancing toward feature parity independently of production. Current integrated native costing checkpoint: develop `64ec54322fcbdd7d942ed671114151d413852277`; PR #8 passed full Windows/Linux and Android retry and is merged. Recipe PR #9 remains open.

1. Check fresh integrated develop software and Android CI, then recipe PR #9 retargeted against develop. Prior run 45774433997 failed compile for Spinner minimum height; the repair uses the supported View API and captures native test stack traces and emulator crash buffer. No acceptance claims until all required jobs pass.
2. Inspect the recipe UI pull request's CI. The installed native screen must emit `NATIVE_RECIPE_SCREEN_PASS` along with every preexisting marker and exact successful instrumentation code. Resolve real failures without relaxing checks. Retarget to develop only after #8 is integrated, then run full gates.
3. For Android local verification, use JDK 17, Gradle 8.9, Android SDK 35/build-tools 34.0.0, an API 34 emulator and the checked-in `.github/workflows/android-native.yml`. Run `gradle -p android-native --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`; install preview and instrumentation APKs on a disposable emulator only, then use the exact instrumentation command in CI. The native APK is not an official sales application; use synthetic data exclusively.
4. After recipe UI acceptance, inspect stacked `feature/native-android-pricing-math`. The exact pricing arithmetic has 41 passing local checks; verify JDK 17/Android CI and all parent gates before retarget/merge. Implement persistent pricing preferences, native input fields, measured fractional material costs and safe durable pending stock recovery next. See Android README for explicitly deferred parity gaps. Preserve the distinction between estimated materials, editable selling price and actual stock movements. Physical devices, printing, and official BIR approval are still external gates.
 

## Protected contracts and release limits

- Preserve exact financial arithmetic, atomic transactions, idempotency, inventory and balance protections, immutable audit trails, cancellation fences and safe backup migrations. Never discard unresolved paid transactions.
- No credentials, signing secrets, live databases or customer information in commits, logs or artifacts. No destructive production operations, paid services or external deployments without authorization.
- Keep physical Windows scaling, actual printer output, actual tablet/phone/PWA tests, native Android acceptance and formal BIR review separate from hosted CI. Use disposable Windows desktop for native harness; never run scaling tests on live shop PC. TCP preflight does not prove server identity.
- Reproducible candidates require exact tested SHA, package manifest/checksums, installation and recovery instructions, and documented acceptance limitations. Do not promote production until required gates pass and applicable approvals exist.

## Self-hosted runner transition (connectivity only)

Owner registered a Windows x64 runner (DJ-PC) and started it interactively; the console must stay running and the PC must remain awake and connected to accept jobs. The develop-only, path-filtered `.github/workflows/self-hosted-runner-smoke.yml` checks just routing and architecture, without checkout, credentials, installation or application execution. Its success is NOT a software acceptance gate. Do not enable regular builds on the live printshop PC: use a disposable isolated Windows VM/test machine, synthetic data and a restricted runner; verify environment and each original test. Do not substitute Windows for Linux/KVM Android acceptance. Preserve all hosted and independent gates until an equivalent verified lane is established; no production or BIR claims.

The actual connectivity run 45801371514/job 139845142020 at `e1b9da05bd55cbb605c566b6b94926db55de2426` passed all steps and log marker SELF_HOSTED_WINDOWS_CONNECTIVITY_PASS. Normal hosted Windows/Ubuntu run 45801371390 failed without steps. See [SELF_HOSTED_RUNNER.md](SELF_HOSTED_RUNNER.md) for the disabled-by-default isolated Windows route, required custom label and local preflight. Do not activate on the ordinary PC or change billing settings. Keep all existing full gates, no production claim.



## Current first integration — pricing math PR #10

The feature branch has been reconciled with develop `30307cc42df54b0f022464bd96e24b3078a07731` (owner runner configuration preserved), while keeping exact existing pricing math and Android tests. Historical CI above is not fresh acceptance. Latest parent #9 is integrated; #10 now requires new exact Android and full Windows/Linux gates before merge. #11–#13 remain stacked, update/retarget in order. Self-hosted Windows remains opt-in only on isolated environment. See SELF_HOSTED_RUNNER.md and DEVELOPMENT_QUEUE.md.

## Copy-ready continuation prompt

Continue Printshop POS M2 from the current GitHub repository. Read HANDOFF-README.md, AGENTS.md and the latest DEVELOPMENT_QUEUE.md checkpoint. Reconcile live branches, PRs and CI before changing code. Implement the next eligible task, test with synthetic data, preserve protected baselines, commit verified work and append the exact next action to the queue. Continue eligible milestones without routine approval; report genuine blockers and never claim unexecuted tests or production readiness. Preserve this handoff in all future source packages.
