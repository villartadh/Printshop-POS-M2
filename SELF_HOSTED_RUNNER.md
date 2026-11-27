# Isolated self-hosted Windows acceptance — operator and continuation procedure

## Status
Connectivity-only test succeeded on DJ-PC at develop `e1b9da05bd55cbb605c566b6b94926db55de2426`, run [45801371514](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45801371514), job 139845142020; actual log marker `SELF_HOSTED_WINDOWS_CONNECTIVITY_PASS`. Same-head hosted software run 45801371390 failed without steps for both operating systems. Quota remains a hypothesis, not a confirmed account balance.

The normal `Software acceptance` Windows/Ubuntu matrix and every existing command remain required. Linux and native Android (Linux/KVM emulator) are unchanged. By default both lanes remain GitHub-hosted. Optional self-hosted Windows routing is *not enabled*, and the owner has confirmed DJ-PC is both the development and live printshop host, and authorized use of the same physical machine. Its ordinary runner is NOT isolated. A dedicated disposable Windows guest on DJ-PC is the preferred route, not direct full builds under the shop account. Connectivity is not application acceptance.

## Prepare an isolated test host before enabling

1. Use a disposable Windows x64 VM on DJ-PC or a separate dedicated development machine (not the live shop Windows instance). Do not share live POS folders, user profile, printers, customer or credential stores with the guest, or mount production data. Use a non-administrator runner account, private repository access restricted to trusted maintainers, disposable snapshots, synthetic data, and no signing or deployment credentials. An untrusted workflow can execute arbitrary code; labels and environment variables are routing/diagnostic controls only, not a security boundary. Never install arbitrary PR code on a production PC.
2. Install Git for Windows with Bash on PATH and PowerShell 7 (`pwsh.exe`), plus standard Windows runner prerequisites and at least 20 GiB *free* disk space in the runner workspace. Node22/Python3.12/Playwright Chromium are installed by the unchanged workflow steps; permit only necessary trusted downloads. Use one runner process for this repository, and a dedicated empty work directory. Don't configure on the ordinary DJ-PC runner.
3. In GitHub repository Settings > Actions > Runners register the **isolated** machine with labels `self-hosted, Windows, X64, printshop-pos-isolated`. Do not add that custom label to the ordinary runner. Follow GitHub's generated installation commands; don't share registration tokens. Set the following environment just before starting the isolated interactive runner (substitute the actual dedicated work root, not a volume root):

```powershell
cd C:\actions-runner
$env:PRINTSHOP_POS_ISOLATED_TEST_RUNNER = '1'
$env:PRINTSHOP_POS_ISOLATED_TEST_ROOT = 'C:\actions-runner\_work'
.\run.cmd
```

Ensure `_work` exists and is a dedicated local folder (not a reparse point); the runner's actual workspace and temp must be under it. For a service, propagate these variables only to that dedicated service account/environment. Stop/restore the disposable machine after tests; do not run multiple jobs on a contaminated host.

4. Only after isolation is confirmed, in repository Settings > Secrets and variables > Actions > Variables set `PRINTSHOP_POS_WINDOWS_RUNNER` exactly to `isolated`. This is an intentional owner-controlled switch (not a secret). Without it, the Windows matrix uses `windows-2022`; with it, only same-repository pull requests into develop and develop push/manual events route to the labeled Windows runner. Foreign PRs, main, other events and Ubuntu remain hosted. Do not enable until the dedicated runner is online and prepared. If a runner is offline, jobs queue; disable the switch to return new runs to the hosted route. Existing jobs may need to be cancelled by the owner if no longer intended; do not cancel unrelated jobs.

## Dual-use DJ-PC readiness

The connectivity smoke now includes a read-only host readiness diagnostic, printing only tool-presence booleans, coarse free workspace capacity and RAM, virtualization indicators and elevated-token status. It does not check out or execute repository code or install anything. Its marker SELF_HOSTED_WINDOWS_READINESS_PASS is diagnostic completion, not proof of virtualization, isolation or application acceptance. Use actual output to choose a Windows guest; do not infer full hardware readiness from tool presence alone. A Windows guest is to be created/configured by the owner on the same hardware; no live machine settings or business files are changed by the workflow.

## Local configuration verification

Parsed both YAML documents; all 22 original steps and all other configuration remained identical after replacing only routing and adding preflight. 576 local expression-subset cases passed for OS, switch, repository, event, base/head and ref combinations. These are not GitHub or PowerShell execution evidence.

## Acceptance and rollout
- Inspect exact current head/base and job routing, require actual `ISOLATED_WINDOWS_PREFLIGHT_PASS` and every existing Windows step including tests, lint/build, API/browser, pinned v0.9.5 database upgrade, packaged runtime/smoke/upgrade/checksum, evidence upload. Verify synthetic-only evidence and successful end result. A skipped preflight on GitHub-hosted runners is expected.
- Verify complete Ubuntu lane (including HTTPS) and actual installed Android gate separately; never accept connectivity alone, or substitute Windows for Linux/KVM.
- Reconcile stacked PRs #10–#13, ensure exact complete gates before merging sequentially; preserve main, protected baselines, source and handoffs.
- Rollback: remove the variable or set it to a value other than `isolated`, and disconnect the isolated runner when unused. This returns subsequent Windows jobs to existing hosted routing without editing the test commands.
- Billing/runner settings have not been changed by this checkpoint. No software builds have been dispatched to DJ-PC.

## VMware activation — 2026-11-27

Owner confirmed disposable guest isolation and authorized Windows testing. Readiness [job 140252646826](https://github.com/villartadh/Printshop-POS-M2/actions/runs/45820801455/job/140252646826) passed on Printshop-VMware with Git, Bash, PowerShell 7 and Python detected, non-elevated token, 83 GiB free and 6 GiB RAM. Both diagnostic markers appeared in actual logs. Repository settings verified DJ-PC offline, Printshop-VMware idle with `printshop-pos-isolated`, no configured Actions secrets; repository variable `PRINTSHOP_POS_WINDOWS_RUNNER=isolated` created and verified. The previous disabled-state references above are historical. This documentation-only develop checkpoint starts a fresh software acceptance run. Verify the actual runner name, isolated preflight marker, every Windows gate and artifacts; hosted Ubuntu and native Android remain independently required. No feature merge, production promotion or physical acceptance is implied. If the VM is offline, set the variable to `hosted` to return new Windows jobs to hosted routing; keep DJ-PC excluded.
