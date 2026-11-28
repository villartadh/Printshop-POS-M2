"""Read-only admission decision from independently refreshed GitHub observations.

No claim, source write or merge is authorized by this policy.
"""
from checkpoint_validation import validate_checkpoint
from ci_checkpoint import checkpoint, REQUIRED

BRIDGE_ID = 433543295

def assess(raw, expected_sha, observed_head, fresh_runs, require_bridge):
    stored = validate_checkpoint(raw, expected_sha)
    if type(require_bridge) is not bool:
        raise ValueError("Explicit bridge applicability required")
    if observed_head != expected_sha:
        return "head_changed"
    fresh = checkpoint(expected_sha, fresh_runs)
    if stored != fresh:
        return "checkpoint_changed"
    if require_bridge:
        candidates = [r for r in fresh_runs if r.get("head_sha") == expected_sha
                      and r.get("workflow_id") == BRIDGE_ID and r.get("event") == "pull_request"]
        if not candidates:
            return "bridge_unverified"
        if any(type(r.get("id")) is not int or r["id"] <= 0 or
               type(r.get("run_attempt")) is not int or r["run_attempt"] <= 0 for r in candidates):
            raise ValueError("Malformed bridge run")
        latest_key = max((r["id"],r["run_attempt"]) for r in candidates)
        latest = [r for r in candidates if (r["id"],r["run_attempt"]) == latest_key]
        if len({(r.get("status"),r.get("conclusion")) for r in latest}) != 1:
            raise ValueError("Conflicting bridge attempt")
        result = latest[0]
        if result.get("status") != "completed" or result.get("conclusion") != "success":
            return "bridge_unverified"
    return {"review":"review_candidate","diagnose":"diagnose_candidate","wait":"wait"}[stored["decision"]]
