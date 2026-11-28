import json
import unittest
from ci_checkpoint import checkpoint, REQUIRED
from consumption_policy import assess, BRIDGE_ID

A="a"*40; B="b"*40

def run(wid, rid, status="completed", conclusion="success", attempt=1):
    return {"head_sha":A,"workflow_id":wid,"id":rid,"run_attempt":attempt,
            "status":status,"conclusion":conclusion,"event":"pull_request"}

class ConsumptionTest(unittest.TestCase):
    def setUp(self):
        self.runs=[run(wid,wid) for wid in REQUIRED.values()]
        self.raw=json.dumps(checkpoint(A,self.runs)).encode()
    def test_fresh_review(self):
        self.assertEqual(assess(self.raw,A,A,self.runs,False),"review_candidate")
    def test_new_head(self):
        self.assertEqual(assess(self.raw,A,B,self.runs,False),"head_changed")
    def test_rerun_invalidates_artifact(self):
        self.runs.append(run(next(iter(REQUIRED.values())),next(iter(REQUIRED.values())),"in_progress",None,2))
        self.assertEqual(assess(self.raw,A,A,self.runs,False),"checkpoint_changed")
    def test_bridge_missing(self):
        self.assertEqual(assess(self.raw,A,A,self.runs,True),"bridge_unverified")
    def test_bridge_exact_success(self):
        self.runs.append(run(BRIDGE_ID,99))
        self.assertEqual(assess(self.raw,A,A,self.runs,True),"review_candidate")
    def test_bridge_new_attempt_masks_success(self):
        self.runs.extend([run(BRIDGE_ID,99),run(BRIDGE_ID,99,"in_progress",None,2)])
        self.assertEqual(assess(self.raw,A,A,self.runs,True),"bridge_unverified")
    def test_conflicting_duplicate(self):
        self.runs.extend([run(BRIDGE_ID,99),run(BRIDGE_ID,99,"completed","failure")])
        with self.assertRaises(ValueError): assess(self.raw,A,A,self.runs,True)
    def test_requires_explicit_applicability(self):
        with self.assertRaises(ValueError): assess(self.raw,A,A,self.runs,None)
    def test_diagnose_candidate(self):
        self.runs[0]["conclusion"]="failure"
        raw=json.dumps(checkpoint(A,self.runs)).encode()
        self.assertEqual(assess(raw,A,A,self.runs,False),"diagnose_candidate")
    def test_conflicting_primary_attempt(self):
        self.runs.append(dict(self.runs[0], conclusion="failure"))
        with self.assertRaises(ValueError): assess(self.raw,A,A,self.runs,False)
