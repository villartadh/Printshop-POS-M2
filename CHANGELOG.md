# 0.9.7 expanded acceptance candidate

- Preserve v0.9.6 software pilot on a dedicated reference branch.
- Add actual receipt-preview virtual PDF evidence for 58mm, 80mm, A4, Letter and custom formats, with geometry, non-tax marking, amounts and overflow checks.
- Physical printer and regulatory acceptance remain separate.

# 0.9.6 software pilot candidate

- Guided shop setup, validated settings and immutable historical receipt branding.
- Complete date-range finance reports and Philippine business dates.
- Reservation-aware inventory, severity-based notifications, supplier reorder drafts, weighted average purchase costs, paginated stock history, archive and restore.
- Durable offline queue acknowledgments, original-record conflict retry and lost-response recovery.
- Immutable sale request identities, cash collection and drawer adjustment replay protection, exact centavo reconciliation.
- Held-order ownership, privileged customer discounts and restricted staff/audit exposure.
- Version-marked SQLite backups and actual v0.9.5 upgrade and restoration acceptance.
- LDC comprehensive gate, structured diagnostics and SHA-256 support archive checksums.
- Versioned source and bundled Windows x64 artifacts, pending final CI on this release commit.
- No physical printer, shop installation/display scaling, tablet hardware or BIR acceptance is claimed.

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
