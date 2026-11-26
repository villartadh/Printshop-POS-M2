# Printshop POS M2 — development handoff

Baseline: v0.9.5, commit `255518ed388bcee3d63ee211e17c199ba984547e`, preserved on `baseline/v0.9.5` and main. The baseline branch is a reference, not an enforced immutable tag. Work on develop or isolated feature branches; do not force-push or merge unverified work.

Codex reported 88 regression tests, 20 API checks, seven Chromium workflows, HTTPS, TypeScript, build and Linux distribution smoke checks passed. These are historical reported results, not independent GitHub CI evidence.

Target: v0.9.6 final software pilot candidate. Audit and fix incomplete controls, first-run business setup, financial and offline workflows, LDC, backup/restore and self-contained distribution. Use synthetic data only. Rebuild release manifest and checksums at packaging. Run cumulative tests on clean installs and export evidence. Native shop Windows, display scaling, physical printer and tablet acceptance remain deferred; BIR review open. Do not promote v1.0.0 or claim deferred tests passed. GitHub-hosted Windows software tests are distinct from shop-device acceptance.

CI: software-acceptance.yml runs Linux/Windows build, regression, API and Chromium checks; HTTPS on Linux. Packaging and distribution-specific checks remain to be added. Never add secrets or actual business databases to GitHub.

## Verified candidate engineering checkpoint
At 1b34246: CI 45731676204 and 45731669288 passed on Ubuntu and Windows. 113 regression tests, 33 API checks, 14 Chromium workflows and four actual v0.9.5 upgrade/restore scenarios, Linux HTTPS and staged Windows runtime smoke. LDC structured gate evidence and checksummed support archive submitted for verification. No production promotion; release version and source manifest still need regeneration at packaging.

## v0.9.6 packaging submission
At 2291837, CI 45732402008 and 45732394799 passed: 115 regression, 33 API, 14 Chromium, four upgrade/restore scenarios. Version 0.9.6 assigned to develop only. The final release workflow must produce versioned Windows ZIP and source ZIP separately, with SHA-256 manifests and software evidence. Check both current runs before reporting availability. Source manifest generated from tested tracked files in CI; the old committed v0.9.5 manifest is historical and excluded from the new source archive. No promotion or deployment authorized.

## Verified v0.9.6 pilot reference
Commit b681596d50ee2fb31d40b5ef087bf58169350429; both CI 45733269332 and 45733263601 passed; preserved at pilot/v0.9.6. Run 45733269332 artifacts: Windows 15078390589, source 15078020902, Windows evidence 15078320811, Linux evidence 15077557213. v0.9.7 is a separate expanded-acceptance candidate. No production approval.
