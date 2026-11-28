"""Read-only interrupted-worker reconciliation, not a lease release or takeover."""
from claim_policy import validate, SHA, HEX, WORKER

def assess_recovery(ledger, intent, observed_source, worker_stopped):
    validate(ledger)
    if not isinstance(intent, dict) or set(intent) != {"event","worker","generation","expected","proposed"}:
        raise ValueError("Invalid intent")
    for key, pattern in (("event",HEX),("worker",WORKER),("expected",SHA),("proposed",SHA)):
        if not isinstance(intent[key],str) or not pattern.fullmatch(intent[key]):
            raise ValueError("Invalid intent identity")
    if type(intent["generation"]) is not int or intent["generation"] <= 0 or intent["expected"] == intent["proposed"]:
        raise ValueError("Invalid intent generation or candidate")
    if type(worker_stopped) is not bool:
        raise ValueError("Explicit worker status required")
    if observed_source is not None and (not isinstance(observed_source,str) or not SHA.fullmatch(observed_source)):
        raise ValueError("Invalid source observation")
    active=ledger["active"]
    if active is None or any(active[k] != intent[k] for k in ("event","worker","generation")) or active["sha"] != intent["expected"]:
        return "intent_mismatch_hold"
    if not worker_stopped:
        return "worker_unverified_hold"
    if observed_source is None:
        return "source_unverified_hold"
    if observed_source == intent["proposed"]:
        return "candidate_observed_review"
    if observed_source == intent["expected"]:
        return "parent_observed_review"
    return "source_changed_hold"
