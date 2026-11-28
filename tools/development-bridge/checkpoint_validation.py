"""Validate a stored checkpoint before review; integrity is not authenticity."""
import hashlib
import hmac
import json
import re
from ci_checkpoint import REPO, REQUIRED

SHA = re.compile(r"[a-f0-9]{40}")
KEY = re.compile(r"[a-f0-9]{64}")
MAX_BYTES = 16384

def _unique(pairs):
    result = {}
    for name, value in pairs:
        if name in result:
            raise ValueError("Duplicate JSON field")
        result[name] = value
    return result

def validate_checkpoint(raw, expected_sha):
    if not isinstance(expected_sha, str) or not SHA.fullmatch(expected_sha):
        raise ValueError("Invalid expected SHA")
    if not isinstance(raw, bytes) or len(raw) > MAX_BYTES:
        raise ValueError("Invalid checkpoint size")
    record = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique)
    if not isinstance(record, dict) or set(record) != {"schema","repository","sha","gates","decision","event_key"}:
        raise ValueError("Invalid checkpoint fields")
    if type(record["schema"]) is not int or record["schema"] != 1 or record["repository"] != REPO or record["sha"] != expected_sha:
        raise ValueError("Checkpoint identity mismatch")
    gates = record["gates"]
    if not isinstance(gates, dict) or set(gates) != set(REQUIRED):
        raise ValueError("Invalid gate set")
    for gate in gates.values():
        if not isinstance(gate, dict):
            raise ValueError("Invalid gate")
        state = gate.get("state")
        if state == "missing":
            if set(gate) != {"state"}:
                raise ValueError("Invalid missing gate")
        elif state in ("pending","passed","failed","blocked"):
            if set(gate) != {"state","run_id","attempt"} or any(type(gate[k]) is not int or gate[k] <= 0 for k in ("run_id","attempt")):
                raise ValueError("Invalid run identity")
        else:
            raise ValueError("Invalid gate state")
    states = {gate["state"] for gate in gates.values()}
    decision = "review" if states == {"passed"} else "diagnose" if "failed" in states else "wait"
    if record["decision"] != decision:
        raise ValueError("Invalid checkpoint decision")
    key = record["event_key"]
    if not isinstance(key, str) or not KEY.fullmatch(key):
        raise ValueError("Invalid event key")
    unsigned = {k: v for k, v in record.items() if k != "event_key"}
    calculated = hashlib.sha256(json.dumps(unsigned, sort_keys=True, separators=(",",":")).encode()).hexdigest()
    if not hmac.compare_digest(key, calculated):
        raise ValueError("Checkpoint integrity mismatch")
    return record

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--sha", required=True)
    parser.add_argument("--file", required=True)
    args = parser.parse_args()
    try:
        with open(args.file, "rb") as source:
            validate_checkpoint(source.read(MAX_BYTES + 1), args.sha)
    except (ValueError, OSError, RecursionError):
        raise SystemExit("Checkpoint validation failed")
    print("CHECKPOINT_SCHEMA_AND_INTEGRITY_PASS")
