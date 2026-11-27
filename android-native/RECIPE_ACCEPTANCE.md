# Native service-material configuration acceptance

Checkpoint: 2026-11-26 (Asia/Manila).

## Accepted engineering evidence at 64941e9a6bf915c430d4047cad4453247e62d080
- Android CI [45775500094](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45775500094): exact arithmetic, native build + lint, installed preview instrumentation all passed. All seven required markers including NATIVE_RECIPE_SCREEN_PASS and exact INSTRUMENTATION_CODE: -1 observed.
- APK/lint ZIP [15096445781](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45775500094/artifacts/15096445781), outer SHA-256 bea749eb54075c7f3ba67350115afb64c8364ca50ba0c6f61a5e1e9d48585500.
- Native evidence ZIP [15096272467](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45775500094/artifacts/15096272467), outer SHA-256 3ba2fea5ab22150ce662295e955bfb3d9637a307bc92c7c29f8a75ad13cac6d6. Both expire 2026-12-10.
- Tests covered actual catalog navigation, material configuration and persistence, exact per-unit and line estimate, insufficient stock, selling-price independence, clearing service link and unchanged stock.
- Regression repair: Spinner uses supported minimum-height API; UI-thread verification failures now return original stack and capture disposable emulator crash buffer.
- Integrated parent develop 64ec54322fcbdd7d942ed671114151d413852277 passed Android 45775322640 and both Windows/Linux jobs 45775322542.

## Continuation
PR #9 is ready for review against develop. This documentation checkpoint triggers a fresh integrated Windows/Linux and Android gate; inspect exact new SHA and run results before merging. Never reinterpret the previous artifact as coming from a new SHA. PR #10 is stacked on the previously verified source 64941e9a6bf915c430d4047cad4453247e62d080 and adds pricing math (41 local tests); do not cancel its CI or integrate ahead of #9. After #9 acceptance retarget #10 and run all gates. See HANDOFF-README.md and DEVELOPMENT_QUEUE.md for resumption procedure and outstanding native pricing configuration and durable pending stock recovery. No physical-device, printer, actual shop use, BIR or production acceptance.
