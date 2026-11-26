# Printshop POS 0.9.6 software pilot operator guide

Use this candidate with synthetic transactions until the installation, hardware and compliance checks have been completed at the shop.

## Windows x64

1. Verify the ZIP against the supplied SHA-256 file and extract it into a new folder. Keep the extracted package for uninstalling this version.
2. Run `INSTALL.cmd`. The installer prints the versioned release path under `%LOCALAPPDATA%\Printshop POS\releases`.
3. Open `RUN-POS.cmd` in that release and keep its terminal open. The launcher opens Edge or Chrome when available. You can also open `http://127.0.0.1:3000/`. Create the first administrator with a unique password.
4. Add the shop's own catalog and prices, stock and payment settings. Open a cashier shift before taking payments. Select a registered customer before accepting a partial payment.
5. Stop with `STOP-POS.cmd` or Ctrl+C in the launcher. Update by installing a newer version alongside the previous release. Review and back up the shared data before switching versions. A downgrade may require restoring a compatible copy.
6. Run `UNINSTALL.cmd` from the extracted package after stopping POS. This removes that version's program files. The shared `%LOCALAPPDATA%\Printshop POS\data` directory, including backups, is retained.

After an abrupt shutdown, reopen `RUN-POS.cmd`. Startup verifies a snapshot of the existing database before opening it. If support is needed, run `SUPPORT.cmd`. It creates a reviewable ZIP in `%LOCALAPPDATA%\Printshop POS\support` containing a status summary.

## Backup and recovery

The launcher creates a verified SQLite snapshot before starting against an existing database. Run `BACKUP.cmd` from the installed release to create an additional verified copy. Keep restricted copies of verified backups and `session.secret` under the shop's backup policy. To verify a database file, run `runtime\node.exe scripts\database-maintenance.mjs verify "PATH-TO-BACKUP"` from the release directory. Drag a backup file onto the installed `RECOVERY-COPY.cmd` to create a verified recovery copy under the data directory. That action preserves the live database. Stop POS and preserve the complete current data directory, including any SQLite WAL/SHM files, before replacing anything. Never mix files from different SQLite snapshots.

The administrator's Business Data JSON export is for review only, not a restorable backup. It contains sensitive financial and customer data and must be protected. The complete SQLite backup contains account hashes, business records and replay protection; retain it securely. Browser-local unconfirmed payments, drawer adjustments and offline sales are not part of server backups. Confirm them on every terminal before restoring; do not clear browser storage. New version-marked databases require all current recovery tables, essential columns and inventory alert triggers. Unversioned older backups may be checked and migrated; verification alone does not prove an old backup has all data.

### Restore a verified backup

Finish synchronizing offline transactions on all terminals and sign them out. Stop POS, drag the selected backup onto `RESTORE-POS.cmd`, and type `RESTORE` when prompted. Restoring brings back the records as of the backup date. The tool verifies the replacement and saves a verified copy of the current database in the recovery folder before switching. Reopen POS and review the records. If the current database fails verification or its journal is still active, the restore stops. Preserve the full data directory for support in that case.

## Receipt and device acceptance

- Open Devices and record the browser and printer readiness results. In Windows, print the printer's driver test page on the intended printer.
- Use a synthetic sale in POS. Preview a receipt at the shop's paper width and use Print / Save as PDF. Select the physical printer and confirm readability, margins, totals, paper feed and any cutter or drawer behavior the shop uses. Reopen Receipt and print again. A print dialog or PDF is evidence of browser output only.
- Keep slips marked non-tax pending BIR review. Record electronic refunds in POS only after confirming the refund with the original provider.
- Use real device and PWA acceptance before serving tablet transactions. The browser PWA has no native Android package in this release.

## Optional HTTPS for shop tablets

The default local HTTP listener stays on loopback. Set the following four environment variables in the shell that launches `RUN-POS.cmd`, then restart the server.

| Variable | Value |
| --- | --- |
| `POS_HTTPS_ORIGIN` | Canonical HTTPS origin such as `https://pos.shop.example:3443` |
| `POS_HTTPS_BIND` | Specific IP address on the POS host's shop network |
| `POS_HTTPS_CERT` | Local path to the PEM certificate chain trusted by all intended tablets |
| `POS_HTTPS_KEY` | Local path to the matching private PEM key |

Set up the first administrator on the local console and use password sign-in on tablets. Match the URL and certificate to the shop's DNS configuration. Limit network access to the authorized shop network and verify certificate trust without bypassing browser warnings. Check login, checkout, offline reconnect, receipt preview and PWA installation on each intended device. Keep the key outside the release and restrict access.

## Development companion (source workspace only)
Run START-LDC.cmd with Node 22.12+ in the 22 series. Option 1 uses the startup gate (locked dependencies, regression, TypeScript, build and isolated API). Option 10 runs the comprehensive software gate after the same checks; it requires installed Chromium, OpenSSL, and a separately checked-out pinned v0.9.5 source at .baseline-v095 with its locked dependencies installed. CI provisions these prerequisites. Stop the managed server before rebuilding. The comprehensive gate stops on first failure and records remaining steps as blocked. Gate evidence is stored under .ldc; the support ZIP includes only generated allowlisted status, current-session gate summary and SHA-256 checksums. Raw logs, databases, settings, credentials and customer records are excluded. Physical device acceptance is separate.

## Troubleshooting and recovery
| Symptom | Operator action |
| --- | --- |
| Server will not start | Do not delete the database. Check the launcher message, stop any other POS instance normally, and run SUPPORT.cmd. |
| Offline checkout or sync conflict | Open Offline Record Recovery. Confirm the original record only after resolving the conflict with the manager; never recreate a paid order. |
| Unconfirmed customer payment | Open Customers; retry the original payment reference. Do not collect cash a second time. |
| Unconfirmed drawer adjustment | Open Cashier Shifts; retry the original reference. Do not move the cash again. |
| Changed price on a queued sale | Preserve the original record. Manager must reconcile the original order and actual amount received; no silent repricing or discard. |
| Invalid backup or schema | Preserve the entire current data directory; do not delete or recreate missing tables. Use a verified backup or escalate. |
| Printer not listed or receipt cut off | Confirm Windows driver test page and paper width, then repeat the synthetic print test. |
| Device not trusted | Check configured HTTPS origin and certificate trust. Never bypass browser security warnings. |
| Shift discrepancies | Count actual cash and record the reconciliation note; never overwrite historical sales to force agreement. |
