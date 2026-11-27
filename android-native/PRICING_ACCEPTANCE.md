# Native pricing arithmetic acceptance

2026-11-26 — Android engineering preview only.

- Parent recipe PR #9 fully passed Android 45776741968 and Windows/Linux 45776742089 and merged into develop cbb5f0f99b0f259d2a350cd7deb8f00cd9baf44b. Verify fresh integrated develop gates.
- Pricing PR #10 head cd318f2799a4d594ef76237d679434d4ea9442f2 passed Android CI [45776608192](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45776608192), 41 pricing arithmetic checks, all existing arithmetic/build/lint and installed emulator checks. APK/lint archive 15096467393 outer SHA-256 a92480ff32b38f7bd9b05b2b6535ad100832d6fad71d4edc04c40865f9ca194e; evidence archive 15096895672 outer SHA-256 dbe3b9f390686a07515bfb6b99bb02bfbafb2f5da22192c2be04f0553b55025f. Both expire 2026-12-10.
- Retargeted PR #10 to develop and marked ready; this doc triggers a new full regression gate. Inspect actual tested head and all gates before merging.
- Next: pricing preference storage and native settings UI in stacked branch feature/native-android-pricing-settings; advanced quote inputs, fractional material estimate and durable pending stock recovery follow. Handoff and queue remain mandatory; do not confuse arithmetic with complete pricing UI or production/physical readiness.

## Pricing foundation and current Android documentation

Native Android Activities using Android widgets and Java, without a browser, WebView, network permission, shop credentials or access to existing shop database files. Separate preview app ID. Local synthetic-only SQLite catalog, audited stock movements and native quotation calculator with dynamic service-material estimates. No receipt issuance, sale, synchronization, production signing or claim of physical acceptance.

## Build and test
JDK 17, Gradle 8.9, Android SDK platform 35 and build tools 34.0.0. Run `gradle -p android-native --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`. Follow `.github/workflows/android-native.yml` for pure Java tests and API 34 disposable-emulator instrumentation, including required pass markers. Debug signatures for engineering UAT only. See HANDOFF-README.md and latest DEVELOPMENT_QUEUE.md before proceeding.

## Native pricing foundation
`PricingMath` defines fixed, per-unit, per-page/copy/sheet, area in square feet, linear feet/meters, and tiered-bulk arithmetic. Service count or pages × copies, measured dimensions (ft/in/cm/m), 4-decimal measurement then 2-decimal effective units, minimum 0.1 measured unit, highest qualifying tier, exact centavo totals with half-up rounding. Unit conversions use the exact physical ratios (desktop's m→ft approximation may differ at rounding boundaries); invalid, ambiguous, duplicate or excessive inputs rejected. Quantity limit 100,000 effective service units, 3-decimal dimensions. All calculations are standalone and never modify stock or recorded prices. Pricing preferences, tier editing, native pricing fields, material estimation for fractional billed units, attributes, formula, fees, tax and rounding settings are not connected yet; do not present the engine as a completed pricing screen or full parity.

## Remaining
Pricing configuration and measured material sufficiency; durable pending stock movement recovery; authentication, local SQLite migrations and atomic audited checkout, durable replay/stock/receivables, production, reports, receipt printing, secure synchronization and backups, native managed-device kiosk behavior with owner authorization, physical device/orientation/layout/IME acceptance. No-scroll usability not certified; scroll fallback is intentional for small or accessibility-scaled screens. Do not attach a live shop database or use the preview to record payments.
