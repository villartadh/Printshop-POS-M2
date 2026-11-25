# 0.9.5 candidate

- Add an interactive packaged database restore that acquires exclusive launcher control, validates the selected backup, preserves a pre-restore snapshot, retires the old journal and atomically replaces the stopped database.
- Cover restore success, invalid backup refusal, previous record preservation and active server refusal with synthetic fixtures.

# 0.9.4 candidate

- Add the native Windows x64 harness for the packaged Node and SQLite runtime, install, upgrade, graceful stop, crash restart, and uninstall. Add Windows Display Settings automation, measured DPI, and per-scale Chromium screenshots.
- Use an OS-owned local control endpoint and a single server process. Add STOP-POS.cmd, browser launch, stale crash-state recovery, and startup backup verification.
- Publish verified SQLite backups atomically without overwriting an existing destination. Add operator backup and recovery-copy commands and privacy-minimized support ZIPs to the Local Development Companion and the Windows distribution.

# 0.9.3 candidate

- Pin the Vite, React plugin, PWA plugin, Tailwind integration, SQLite and chart dependencies. Commit the npm lockfile and install it before invoking the release gates.
- Correct TypeScript narrowing for inventory validation and defer Vite loading to the development path.
- Exercise checkout, pricing, duplicate suppression, reversals, production release, shifts and backup/recovery in a temporary database.
- Add Chromium acceptance and responsive screenshots. Provide a staged Windows x64 runtime and per-version install and removal commands using shared persistent storage.
- Add an opt-in same-origin HTTPS listener for password-authenticated shop devices. First-run setup and PIN sign-in stay on the loopback console.
- Correct cramped tablet catalog cards and surface quotation save and partial-payment errors in the POS. Update Vite and Workbox's glob dependency after the registry audit.
