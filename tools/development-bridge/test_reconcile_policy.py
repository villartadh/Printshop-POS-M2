import unittest
from reconcile_policy import reconcile_ref

A="a"*40; B="b"*40; C="c"*40

class ReconcileTest(unittest.TestCase):
    def test_exact_candidate(self):
        self.assertEqual(reconcile_ref(A,B,B),"exact_candidate_observed")
    def test_parent_is_not_retry_permission(self):
        self.assertEqual(reconcile_ref(A,B,A),"parent_observed_recheck_required")
    def test_different_head(self):
        self.assertEqual(reconcile_ref(A,B,C),"different_head_investigate")
    def test_missing_read(self):
        self.assertEqual(reconcile_ref(A,B,None),"unverified")
    def test_invalid_identity(self):
        for args in [(A,A,A),(None,B,A),(A,"branch",A),(A,B,"unknown"),(A,B,42)]:
            with self.assertRaises(ValueError): reconcile_ref(*args)

if __name__=="__main__": unittest.main()
