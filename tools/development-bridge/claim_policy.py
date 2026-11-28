"""Pure, fail-closed ledger transition contract. Persist using an atomic compare-and-swap.
Never grant a lease from an uncommitted transition or automatically steal a stale claim.
"""
import copy
import re
from ci_checkpoint import REPO
HEX = re.compile(r"[a-f0-9]{64}")
SHA = re.compile(r"[a-f0-9]{40}")
WORKER = re.compile(r"[a-zA-Z0-9_-]{16,80}")
MAX_EVENTS=10000

def new_ledger():
    return {"schema":1,"repository":REPO,"generation":0,"active":None,"completed":{}}

def validate(state):
    if not isinstance(state,dict) or set(state)!={"schema","repository","generation","active","completed"}:
        raise ValueError("Invalid ledger")
    if state["schema"]!=1 or state["repository"]!=REPO or type(state["generation"]) is not int or state["generation"]<0:
        raise ValueError("Invalid ledger identity")
    if not isinstance(state["completed"],dict) or len(state["completed"])>MAX_EVENTS:
        raise ValueError("Invalid completed ledger")
    for key, value in state["completed"].items():
        if not HEX.fullmatch(key) or value not in ("reviewed","diagnosed"):
            raise ValueError("Invalid completion")
    active=state["active"]
    if active is not None:
        if not isinstance(active,dict) or set(active)!={"event","sha","worker","generation","decision"}:
            raise ValueError("Invalid active claim")
        if (not HEX.fullmatch(active["event"]) or not SHA.fullmatch(active["sha"]) or
            not WORKER.fullmatch(active["worker"]) or active["generation"]!=state["generation"] or
            active["event"] in state["completed"] or active["decision"] not in ("review","diagnose")):
            raise ValueError("Invalid active claim")

def claim(state, event, sha, worker, decision):
    validate(state)
    if not HEX.fullmatch(event) or not SHA.fullmatch(sha) or not WORKER.fullmatch(worker) or decision not in ("review","diagnose"):
        raise ValueError("Invalid claim request")
    if state["active"] is not None or event in state["completed"] or len(state["completed"])>=MAX_EVENTS:
        raise ValueError("Claim unavailable")
    next_state=copy.deepcopy(state)
    next_state["generation"]+=1
    next_state["active"]={"event":event,"sha":sha,"worker":worker,"generation":next_state["generation"],"decision":decision}
    return next_state

def complete(state, event, worker, generation, outcome):
    validate(state)
    active=state["active"]
    if (not active or active["event"]!=event or active["worker"]!=worker or
        active["generation"]!=generation or outcome!=("reviewed" if active["decision"]=="review" else "diagnosed")):
        raise ValueError("Claim ownership or outcome mismatch")
    next_state=copy.deepcopy(state)
    next_state["completed"][event]=outcome
    next_state["active"]=None
    next_state["generation"]+=1
    return next_state
