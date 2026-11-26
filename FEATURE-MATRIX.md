# Printshop POS 0.9.6 — software pilot feature and acceptance matrix

Engineering checkpoint: commit 22918376a973538ab0bc8815a5103e4c24954d68, CI runs 45732402008 and 45732394799. The final release commit must pass independently.

| Capability | Scope / evidence |
| --- | --- |
| POS and pricing | Catalog/quantity/discount/tax, held orders, quotations, cash and manual electronic payment bookkeeping; automated |
| Receipts | HTML export, print preview and historical branding; browser automated; physical printer deferred |
| Production | Sale-linked print jobs and payment-gated completion; automated |
| Customers | Receivables, cash collection and recovery; automated |
| Cashier | Shift, drawer movements and replay-safe recovery; automated |
| Inventory | Availability, thresholds, alert acknowledgment, supplier drafts, cost/history, archive/restore; automated |
| Reports | Complete Manila-date finance range; automated; not audited accounting statements |
| Offline | Sale and stock queue, owner isolation, conflict/replay recovery; automated; browser-local records must be preserved |
| Access | Authentication, targeted authorization, role-protected discounts and mutation ownership; automated; full external security assessment not performed |
| Backup | Verified SQLite snapshot, current and legacy restore and actual baseline upgrade; automated |
| LDC | Startup/comprehensive software checks and privacy-minimized support ZIP; regression-tested |
| Windows | Bundled x64 runtime and isolated install/start/stop/restart/uninstall smoke; automated CI, not physical shop installation |
| Browser/PWA | Responsive Chromium workflows; Linux HTTPS gate; actual tablets and native display scaling deferred |
| Payment processing | Manual recording only; no real gateway, card or wallet settlement |
| Compliance | Non-tax pilot receipts; BIR review deferred |

Known limitations: legacy replay without a fingerprint requires reconciliation; unconfirmed local records are not centrally locked across terminals; no automatic destructive discard; server backup excludes browser IndexedDB and session.secret; offline price conflicts require operator reconciliation; physical stock is not automatically returned on refunds; no native Android APK or macOS package. Do not use for real sales until shop acceptance and compliance are completed.

## v0.9.7 extended acceptance
Virtual PDF output for all five receipt formats passed CI 45734634658 and 45734628690. Browser DPR and Android touch/offline tests have been submitted, not yet verified. Emulation cannot certify native Windows DPI or physical tablet installation.

CI 45737463509 and 45737457229 passed measured 100/125/150% Chromium DPR simulation, Android touch checkout and offline reconciliation, tablet portrait/landscape and PWA readiness checks. A hosted Windows bundled baseline/candidate lifecycle gate is submitted for verification. The baseline is rebuilt from pinned v0.9.5 source and lockfile; it is not a byte-identical historical installer. Actual OS DPI, real devices and physical printer remain unverified.
