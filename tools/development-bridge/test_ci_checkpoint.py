import unittest
from ci_checkpoint import checkpoint, REQUIRED
SHA="a"*40
def run(name, id, status="completed", conclusion="success", attempt=1, sha=SHA, event="pull_request"):
    return dict(workflow_id=REQUIRED[name],id=id,run_attempt=attempt,head_sha=sha,event=event,status=status,conclusion=conclusion)
A,B=REQUIRED
class CheckpointTest(unittest.TestCase):
    def test_pass(self):
        self.assertEqual(checkpoint(SHA,[run(A,1),run(B,2)])["decision"],"review")
    def test_missing(self):
        self.assertEqual(checkpoint(SHA,[run(A,1)])["decision"],"wait")
    def test_failure(self):
        self.assertEqual(checkpoint(SHA,[run(A,1),run(B,2,conclusion="failure")])["decision"],"diagnose")
    def test_cancelled(self):
        self.assertEqual(checkpoint(SHA,[run(A,1),run(B,2,conclusion="cancelled")])["decision"],"wait")
    def test_new_pending_masks_old(self):
        self.assertEqual(checkpoint(SHA,[run(A,1),run(A,3,status="in_progress"),run(B,2)])["decision"],"wait")
    def test_attempt_masks(self):
        self.assertEqual(checkpoint(SHA,[run(A,1),run(A,1,attempt=2,conclusion="failure"),run(B,2)])["decision"],"diagnose")
    def test_wrong_sha(self):
        self.assertEqual(checkpoint(SHA,[run(A,1),run(B,2,sha="b"*40)])["decision"],"wait")
    def test_wrong_event(self):
        self.assertEqual(checkpoint(SHA,[run(A,1),run(B,2,event="push")])["decision"],"wait")
    def test_stable_key(self):
        a,b=run(A,1),run(B,2)
        self.assertEqual(checkpoint(SHA,[a,b]),checkpoint(SHA,[b,a]))
        self.assertNotEqual(checkpoint(SHA,[a,b])["event_key"],checkpoint(SHA,[a,run(B,2,attempt=2)])["event_key"])
    def test_invalid_sha(self):
        with self.assertRaises(ValueError): checkpoint("main",[])
    def test_invalid_run(self):
        with self.assertRaises(ValueError): checkpoint(SHA,[run(A,0)])
if __name__=="__main__": unittest.main()
