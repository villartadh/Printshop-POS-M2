# Printshop POS M2 — development handoff

Baseline: v0.9.5, commit `255518ed388bcee3d63ee211e17c199ba984547e`, preserved on `baseline/v0.9.5` and main. The baseline branch is a reference, not an enforced immutable tag. Work on develop or isolated feature branches; do not force-push or merge unverified work.

Codex reported 88 regression tests, 20 API checks, seven Chromium workflows, HTTPS, TypeScript, build and Linux distribution smoke checks passed. These are historical reported results, not independent GitHub CI evidence.

Target: v0.9.6 final software pilot candidate. Audit and fix incomplete controls, first-run business setup, financial and offline workflows, LDC, backup/restore and self-contained distribution. Use synthetic data only. Rebuild release manifest and checksums at packaging. Run cumulative tests on clean installs and export evidence. Native shop Windows, display scaling, physical printer and tablet acceptance remain deferred; BIR review open. Do not promote v1.0.0 or claim deferred tests passed. GitHub-hosted Windows software tests are distinct from shop-device acceptance.

CI: software-acceptance.yml runs Linux/Windows build, regression, API and Chromium checks; HTTPS on Linux. Packaging and distribution-specific checks remain to be added. Never add secrets or actual business databases to GitHub.
