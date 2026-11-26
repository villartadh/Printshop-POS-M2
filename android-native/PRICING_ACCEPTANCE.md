# Native pricing arithmetic acceptance

2026-11-26 — Android engineering preview only.

- Parent recipe PR #9 fully passed Android 45776741968 and Windows/Linux 45776742089 and merged into develop cbb5f0f99b0f259d2a350cd7deb8f00cd9baf44b. Verify fresh integrated develop gates.
- Pricing PR #10 head cd318f2799a4d594ef76237d679434d4ea9442f2 passed Android CI [45776608192](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45776608192), 41 pricing arithmetic checks, all existing arithmetic/build/lint and installed emulator checks. APK/lint archive 15096467393 outer SHA-256 a92480ff32b38f7bd9b05b2b6535ad100832d6fad71d4edc04c40865f9ca194e; evidence archive 15096895672 outer SHA-256 dbe3b9f390686a07515bfb6b99bb02bfbafb2f5da22192c2be04f0553b55025f. Both expire 2026-12-10.
- Retargeted PR #10 to develop and marked ready; this doc triggers a new full regression gate. Inspect actual tested head and all gates before merging.
- Next: pricing preference storage and native settings UI in stacked branch feature/native-android-pricing-settings; advanced quote inputs, fractional material estimate and durable pending stock recovery follow. Handoff and queue remain mandatory; do not confuse arithmetic with complete pricing UI or production/physical readiness.
