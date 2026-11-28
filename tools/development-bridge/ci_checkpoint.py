"""Read-only exact-head GitHub Actions checkpoint. No writes, no AI, no log contents."""
import argparse
import hashlib
import json
import os
import re
import time
import urllib.parse
import urllib.request

REQUIRED = {"Software acceptance": 429885109, "Native Android foundation": 431215336}
REPO = "villartadh/Printshop-POS-M2"

def checkpoint(sha, runs):
    if not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Expected exact commit SHA")
    gates = {}
    for name, workflow_id in REQUIRED.items():
        candidates = [r for r in runs if r.get("head_sha") == sha and
                      r.get("workflow_id") == workflow_id and r.get("event") == "pull_request"]
        # Newest run wins, including a newer pending run. Earlier success cannot mask it.
        if not candidates:
            gates[name] = {"state": "missing"}
            continue
        if any(type(r.get("id")) is not int or r["id"] <= 0 or
               type(r.get("run_attempt")) is not int or r["run_attempt"] <= 0 for r in candidates):
            raise ValueError("Malformed workflow identity")
        latest = max(candidates, key=lambda r: (r["id"], r["run_attempt"]))
        state = "pending"
        if latest.get("status") == "completed":
            conclusion = latest.get("conclusion")
            state = "passed" if conclusion == "success" else "failed" if conclusion == "failure" else "blocked"
        gates[name] = {"state": state, "run_id": latest["id"], "attempt": latest["run_attempt"]}
    states = {g["state"] for g in gates.values()}
    decision = "review" if states == {"passed"} else "diagnose" if "failed" in states else "wait"
    record = {"schema": 1, "repository": REPO, "sha": sha, "gates": gates, "decision": decision}
    record["event_key"] = hashlib.sha256(json.dumps(record, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return record

def fetch_runs(sha, token):
    runs = []
    for page in range(1, 11):
        query = urllib.parse.urlencode({"head_sha": sha, "event": "pull_request", "per_page": 100, "page": page})
        request = urllib.request.Request("https://api.github.com/repos/" + REPO + "/actions/runs?" + query,
            headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "pos-development-bridge"})
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read(8*1024*1024+1)
            if len(raw)>8*1024*1024: raise ValueError("Oversized workflow metadata")
            body = json.loads(raw)
        batch = body["workflow_runs"]
        if not isinstance(batch, list): raise ValueError("Invalid workflow metadata")
        runs.extend(batch)
        if len(batch)<100: return runs
    raise ValueError("Workflow pagination limit exceeded")

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--wait-seconds", type=int, default=0)
    args=parser.parse_args()
    if not re.fullmatch(r"[a-f0-9]{40}", args.sha) or not 0 <= args.wait_seconds <= 1800:
        raise SystemExit("Invalid checkpoint parameters")
    token=os.environ.get("GITHUB_TOKEN")
    if not token: raise SystemExit("GitHub token required")
    deadline=time.monotonic()+args.wait_seconds
    try:
        while True:
            record=checkpoint(args.sha,fetch_runs(args.sha,token))
            if record["decision"] != "wait" or time.monotonic()>=deadline: break
            time.sleep(min(30,max(0,deadline-time.monotonic())))
        print(json.dumps(record,sort_keys=True))
    except (ValueError, KeyError, OSError, TypeError):
        raise SystemExit("Checkpoint unavailable; inspect GitHub independently")

if __name__ == "__main__": main()
