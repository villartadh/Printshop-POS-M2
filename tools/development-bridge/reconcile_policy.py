"""Classify ambiguous ref writes using fresh independent observations.

This does not acquire a lock, authorize a retry or prove source fencing.
"""
import re

SHA = re.compile(r"[a-f0-9]{40}")

def reconcile_ref(expected, proposed, observed):
    """Return a conservative classification; never authorize an automatic retry."""
    if any(not isinstance(s, str) or not SHA.fullmatch(s) for s in (expected, proposed)):
        raise ValueError("Invalid candidate identity")
    if expected == proposed:
        raise ValueError("Candidate must advance the ref")
    if observed is None:
        return "unverified"
    if not isinstance(observed, str) or not SHA.fullmatch(observed):
        raise ValueError("Invalid observed identity")
    if observed == proposed:
        return "exact_candidate_observed"
    if observed == expected:
        return "parent_observed_recheck_required"
    return "different_head_investigate"
