import copy
import unittest
from claim_policy import claim, new_ledger
from recovery_policy import assess_recovery

A="a"*64; S="b"*40; P="c"*40; D="d"*40; W="worker-1234567890"

class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.ledger=claim(new_ledger(),A,S,W,"review")
        self.intent={"event":A,"worker":W,"generation":1,"expected":S,"proposed":P}
    def test_published_candidate(self):
        self.assertEqual(assess_recovery(self.ledger,self.intent,P,True),"candidate_observed_review")
    def test_parent_not_retry_permission(self):
        self.assertEqual(assess_recovery(self.ledger,self.intent,S,True),"parent_observed_review")
    def test_worker_not_stopped(self):
        self.assertEqual(assess_recovery(self.ledger,self.intent,P,False),"worker_unverified_hold")
    def test_source_unavailable(self):
        self.assertEqual(assess_recovery(self.ledger,self.intent,None,True),"source_unverified_hold")
    def test_source_changed(self):
        self.assertEqual(assess_recovery(self.ledger,self.intent,D,True),"source_changed_hold")
    def test_wrong_owner_generation_or_parent(self):
        for k,v in [("worker","worker-0987654321"),("generation",2),("expected",D),("event","e"*64)]:
            self.assertEqual(assess_recovery(self.ledger,dict(self.intent,**{k:v}),P,True),"intent_mismatch_hold")
    def test_no_ledger_mutation(self):
        original=copy.deepcopy(self.ledger)
        for observed in [None,S,P,D]:
            assess_recovery(self.ledger,self.intent,observed,True)
        self.assertEqual(self.ledger,original)
    def test_invalid(self):
        for intent in [dict(self.intent,generation=True),dict(self.intent,proposed=S),dict(self.intent,expected="main")]:
            with self.assertRaises(ValueError): assess_recovery(self.ledger,intent,P,True)
    def test_missing_claim(self):
        self.assertEqual(assess_recovery(new_ledger(),self.intent,P,True),"intent_mismatch_hold")
